package com.example.location

import android.Manifest
import android.location.Geocoder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.location.ui.theme.LocationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// Data class to hold custom marker information.
data class CustomMarker(val position: LatLng, val address: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MapScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen() {
    val context = LocalContext.current

    // Request location permission
    val permissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)
    val coroutineScope = rememberCoroutineScope()

    // State to hold the user location, address, and any custom markers
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var userAddress by remember { mutableStateOf("") }
    var customMarkers by remember { mutableStateOf(listOf<CustomMarker>()) }

    // If the permission is not granted, ask for it
    if (!permissionState.status.isGranted) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Location permission is required to show your position on the map.")
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { permissionState.launchPermissionRequest() }) {
                Text("Request Permission")
            }
        }
    } else {
        // Request the last known location
        LaunchedEffect(Unit) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                userLocation = if (location != null) {
                    LatLng(location.latitude, location.longitude)
                } else {
                    // If no current location is available
                    LatLng(37.4221, -122.0841)
                }
            }
        }

        // Reverse geocode the user location to get an address
        LaunchedEffect(userLocation) {
            userLocation?.let {
                userAddress = getAddressFromLatLng(context, it) ?: "No address found"
            }
        }

        // Set up the camera position state for the Google Map
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(userLocation ?: LatLng(37.4221, -122.0841), 15f)
        }

        LaunchedEffect(userLocation) {
            userLocation?.let { newLocation ->
                cameraPositionState.animate(
                    update = CameraUpdateFactory.newLatLngZoom(newLocation, 15f),
                    durationMs = 1000
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = true),
                uiSettings = MapUiSettings(myLocationButtonEnabled = true, compassEnabled = true),
                onMapClick = { latLng ->
                    // Add a custom marker on tap with its reverse-geocoded address.
                    coroutineScope.launch {
                        val address = getAddressFromLatLng(context, latLng) ?: "No address found"
                        customMarkers = customMarkers + CustomMarker(latLng, address)
                    }
                }
            ) {
                // Marker for the user's location
                userLocation?.let {
                    Marker(
                        state = MarkerState(position = it),
                        title = "You are here",
                        snippet = userAddress
                    )
                }
                // Markers for custom locations
                customMarkers.forEach { marker ->
                    Marker(
                        state = MarkerState(position = marker.position),
                        title = "Custom Marker",
                        snippet = marker.address
                    )
                }
            }
            // Display the address
            if (customMarkers.isNotEmpty()) {
                val lastMarker = customMarkers.last()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                        .padding(8.dp)
                ) {
                    Text(text = "Last marker address: ${lastMarker.address}")
                }
            }
        }
    }
}

// Suspend function to get address information using reverse geocoding
suspend fun getAddressFromLatLng(context: android.content.Context, latLng: LatLng): String? =
    withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            addresses?.firstOrNull()?.getAddressLine(0)
        } catch (e: Exception) {
            null
        }
    }
