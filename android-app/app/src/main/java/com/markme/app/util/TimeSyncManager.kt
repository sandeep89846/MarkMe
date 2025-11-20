package com.markme.app.util

import android.util.Log
import com.markme.app.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

object TimeSyncManager {

    private const val TAG = "TimeSyncManager"
    private var offsetMs = AtomicLong(0L)

    // We will call this from our main activity or viewmodel
    suspend fun syncWithServer(api: ApiService) = withContext(Dispatchers.IO) {
        try {
            val localBefore = System.currentTimeMillis()
            val serverResponse = api.getServerTime()
            val localAfter = System.currentTimeMillis()

            // Estimate network latency
            val latency = (localAfter - localBefore) / 2

            val serverTime = Instant.parse(serverResponse.utc).toEpochMilli()
            val adjustedServerTime = serverTime + latency

            val localNow = System.currentTimeMillis()

            val newOffset = adjustedServerTime - localNow
            offsetMs.set(newOffset)

            Log.i(TAG, "Time sync successful. Offset: ${newOffset}ms")

        } catch (e: Exception) {
            Log.e(TAG, "Time sync failed: ${e.message}")
            // We'll keep using the old offset (or 0)
        }
    }

    /**
     * Returns the current, trusted UTC time in milliseconds.
     */
    fun nowUtcMillis(): Long {
        return System.currentTimeMillis() + offsetMs.get()
    }

    /**
     * Returns the current, trusted UTC time as an ISO 8601 string.
     * This is what we will put in our JSON blobs.
     */
    fun nowIsoUtcString(): String {
        return Instant.ofEpochMilli(nowUtcMillis()).toString()
    }
}