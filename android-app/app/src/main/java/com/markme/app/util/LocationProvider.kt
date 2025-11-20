package com.markme.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine // <-- This provides the function
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationProvider(private val context: Context) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val TAG = "LocationProvider"

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Gets a single, high-accuracy location update.
     * This is a suspend function that wraps the callback-based API.
     */
    suspend fun getCurrentLocation(): Location = suspendCancellableCoroutine { continuation ->
        if (!hasLocationPermission()) {
            continuation.resumeWithException(SecurityException("Missing location permission"))
            return@suspendCancellableCoroutine
        }

        val cancellationToken = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationToken.token
        ).addOnSuccessListener { location: Location? ->
            if (location != null) {
                // Check if the coroutine is still active before resuming
                if (continuation.isActive) {
                    Log.i(TAG, "Got location: ${location.latitude}, ${location.longitude}")
                    continuation.resume(location)
                }
            } else {
                if (continuation.isActive) {
                    Log.w(TAG, "Failed to get location, result was null.")
                    continuation.resumeWithException(Exception("Failed to get location (null)"))
                }
            }
        }.addOnFailureListener { e ->
            if (continuation.isActive) {
                Log.e(TAG, "Failed to get location", e)
                continuation.resumeWithException(e)
            }
        }

        // This is the function that was unresolved.
        // It's a method on the 'continuation' object.
        continuation.invokeOnCancellation {
            cancellationToken.cancel()
        }
    }
}