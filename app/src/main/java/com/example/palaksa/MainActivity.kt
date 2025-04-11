package com.example.palaksa

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// Constantes para SharedPreferences
private const val PREFS_NAME = "PalaksaPrefs"
private const val KEY_POINT_A = "point_a"
private const val KEY_POINT_B = "point_b"
private const val KEY_ROUTE_POINTS = "route_points"
private const val OPENROUTE_API_KEY = "5b3ce3597851110001cf6248224708960f674c2d8df73a9101cbb69b"

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                recreate() // Recargar la actividad cuando se conceden permisos
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configuración de OSMDroid
        Configuration.getInstance().apply {
            userAgentValue = packageName
            load(applicationContext, getSharedPreferences("osm", Context.MODE_PRIVATE))
        }

        setContent {
            PalaksaApp(requestPermissionLauncher, this)
        }
    }

    // Guardar puntos en SharedPreferences
    fun savePoints(pointA: GeoPoint?, pointB: GeoPoint?, routePoints: List<GeoPoint>?) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(prefs.edit()) {
            pointA?.let {
                putString(KEY_POINT_A, "${it.latitude},${it.longitude}")
            } ?: run {
                remove(KEY_POINT_A)
            }

            pointB?.let {
                putString(KEY_POINT_B, "${it.latitude},${it.longitude}")
            } ?: run {
                remove(KEY_POINT_B)
            }

            routePoints?.let { points ->
                val pointsString = points.joinToString("|") { "${it.latitude},${it.longitude}" }
                putString(KEY_ROUTE_POINTS, pointsString)
            } ?: run {
                remove(KEY_ROUTE_POINTS)
            }
            apply()
        }
    }

    // Cargar puntos desde SharedPreferences
    fun loadPoints(): Triple<GeoPoint?, GeoPoint?, List<GeoPoint>?> {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return Triple(
            prefs.getString(KEY_POINT_A, null)?.toGeoPoint(),
            prefs.getString(KEY_POINT_B, null)?.toGeoPoint(),
            prefs.getString(KEY_ROUTE_POINTS, null)?.toGeoPointList()
        )
    }

    // Limpiar datos guardados
    fun clearSavedPoints() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    // Extensión para convertir String a GeoPoint
    private fun String.toGeoPoint(): GeoPoint? {
        return try {
            val parts = split(",")
            if (parts.size == 2) GeoPoint(parts[0].toDouble(), parts[1].toDouble()) else null
        } catch (e: Exception) {
            null
        }
    }

    // Extensión para convertir String a List<GeoPoint>
    private fun String.toGeoPointList(): List<GeoPoint>? {
        return try {
            split("|").mapNotNull { it.toGeoPoint() }
        } catch (e: Exception) {
            null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PalaksaApp(
    requestPermissionLauncher: ActivityResultLauncher<String>,
    activity: MainActivity
) {
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var currentLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var pointA by remember { mutableStateOf<GeoPoint?>(null) }
    var pointB by remember { mutableStateOf<GeoPoint?>(null) }
    var routePoints by remember { mutableStateOf<List<GeoPoint>?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Cargar puntos guardados al iniciar
    LaunchedEffect(Unit) {
        val (savedPointA, savedPointB, savedRoutePoints) = activity.loadPoints()
        savedPointA?.let { pointA = it }
        savedPointB?.let { pointB = it }
        savedRoutePoints?.let { routePoints = it }
    }

    // Guardar puntos cuando cambian
    LaunchedEffect(pointA, pointB, routePoints) {
        activity.savePoints(pointA, pointB, routePoints)
    }

    // Debug logs
    DisposableEffect(currentLocation) {
        currentLocation?.let {
            println("Ubicación actual actualizada: Lat=${it.latitude}, Lon=${it.longitude}")
        }
        onDispose { }
    }

    DisposableEffect(pointB) {
        pointB?.let {
            println("Punto B seleccionado: Lat=${it.latitude}, Lon=${it.longitude}")
        }
        onDispose { }
    }

    // Obtener ubicación actual
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            val locationClient = LocationServices.getFusedLocationProviderClient(context)
            try {
                val locationResult = locationClient.lastLocation
                locationResult.addOnSuccessListener { location ->
                    location?.let {
                        val geoPoint = GeoPoint(it.latitude, it.longitude)
                        currentLocation = geoPoint
                        pointA = geoPoint
                        println("Ubicación obtenida correctamente")
                    }
                }.addOnFailureListener { e ->
                    println("Error obteniendo ubicación: ${e.message}")
                }
            } catch (e: SecurityException) {
                println("Error de permisos: ${e.message}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Navegación Palaksa") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                actions = {
                    IconButton(
                        onClick = {
                            // Botón para limpiar ruta guardada
                            pointB = null
                            routePoints = null
                            activity.clearSavedPoints()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Limpiar ruta"
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (pointB != null) {
                Button(
                    onClick = {
                        if (pointA != null && pointB != null) {
                            coroutineScope.launch {
                                calculateRoute(
                                    start = pointA!!,
                                    end = pointB!!,
                                    onResult = { points ->
                                        routePoints = if (points.isNotEmpty()) points else null
                                    },
                                    activity = activity
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Llévame a casa")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (!hasLocationPermission) {
                Button(
                    onClick = {
                        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("Conceder permiso de ubicación")
                }
            } else {
                OSMMapView(
                    modifier = Modifier.weight(1f),
                    currentLocation = currentLocation,
                    pointA = pointA,
                    pointB = pointB,
                    routePoints = routePoints,
                    onMapClick = { geoPoint ->
                        pointB = geoPoint
                        routePoints = null
                    },
                    activity = activity
                )
            }
        }
    }
}

@Composable
fun OSMMapView(
    modifier: Modifier = Modifier,
    currentLocation: GeoPoint?,
    pointA: GeoPoint?,
    pointB: GeoPoint?,
    routePoints: List<GeoPoint>?,
    onMapClick: (GeoPoint) -> Unit,
    activity: MainActivity
) {
    var firstLoad by remember { mutableStateOf(true) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                minZoomLevel = 3.0
                maxZoomLevel = 19.0
                setMultiTouchControls(true)

                overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        p?.let { onMapClick(it) }
                        return true
                    }
                    override fun longPressHelper(p: GeoPoint?): Boolean = false
                }))
            }
        },
        update = { mapView ->
            // Limpiar marcadores y rutas anteriores
            mapView.overlays.removeIf { it is Marker || it is Polyline }

            // Centrar mapa en la ubicación actual (solo en primera carga)
            if (firstLoad && currentLocation != null) {
                mapView.controller.animateTo(currentLocation)
                mapView.controller.setZoom(16.0)
                firstLoad = false
            }

            // Agregar marcador de ubicación actual (Punto A)
            pointA?.let {
                Marker(mapView).apply {
                    position = it
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Tu ubicación (Punto A)"
                    mapView.overlays.add(this)
                }
            }

            // Agregar marcador de Punto B
            pointB?.let {
                Marker(mapView).apply {
                    position = it
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Destino (Punto B)"
                    mapView.overlays.add(this)
                }
            }

            // Dibujar ruta
            routePoints?.let { points ->
                Polyline(mapView).apply {
                    setPoints(points)
                    color = Color.parseColor("#3F51B5") // Azul Material
                    width = 12f
                    mapView.overlays.add(this)
                }
            }

            mapView.invalidate()
        }
    )

    // Guardar estado al salir
    DisposableEffect(Unit) {
        onDispose {
            activity.savePoints(pointA, pointB, routePoints)
        }
    }
}

// Configuración de OpenRouteService
interface OpenRouteServiceApi {
    @GET("v2/directions/foot-walking")
    suspend fun getWalkingDirections(
        @Query("api_key") apiKey: String,
        @Query("start") start: String,
        @Query("end") end: String
    ): RouteResponse
}

data class RouteResponse(
    val features: List<Feature>
)

data class Feature(
    val geometry: Geometry
)

data class Geometry(
    val coordinates: List<List<Double>>
)

fun createOpenRouteServiceApi(): OpenRouteServiceApi {
    val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    val retrofit = Retrofit.Builder()
        .baseUrl("https://api.openrouteservice.org/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    return retrofit.create(OpenRouteServiceApi::class.java)
}

suspend fun calculateRoute(
    start: GeoPoint,
    end: GeoPoint,
    onResult: (List<GeoPoint>) -> Unit,
    activity: MainActivity
) {
    val api = createOpenRouteServiceApi()

    try {
        println("Calculando ruta desde (${start.latitude}, ${start.longitude}) hasta (${end.latitude}, ${end.longitude})")

        val response = api.getWalkingDirections(
            apiKey = OPENROUTE_API_KEY,
            start = "${start.longitude},${start.latitude}",
            end = "${end.longitude},${end.latitude}"
        )

        val points = response.features.firstOrNull()?.geometry?.coordinates?.map {
            GeoPoint(it[1], it[0]) // Convertir [lon, lat] a GeoPoint
        } ?: emptyList()

        if (points.isEmpty()) {
            println("La API respondió pero no devolvió puntos de ruta")
        } else {
            println("Ruta calculada exitosamente con ${points.size} puntos")
            // Guardar automáticamente la ruta calculada
            activity.savePoints(start, end, points)
        }

        onResult(points)
    } catch (e: Exception) {
        println("Error al calcular ruta: ${e.javaClass.simpleName} - ${e.message}")
        e.printStackTrace()
        onResult(emptyList())
    }
}