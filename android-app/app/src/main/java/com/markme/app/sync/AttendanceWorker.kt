package com.markme.app.sync

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.markme.app.data.DatabaseManager
import com.markme.app.network.ApiService
import com.markme.app.network.AttendanceBatch
import com.markme.app.network.AttendanceEvent
import com.markme.app.network.RetrofitClient
import com.markme.data.PendingAttendanceQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Worker that uploads pending attendance records.
 * On success ("ok"), it moves the record to the local VerifiedAttendance table for offline history.
 */
class AttendanceWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "AttendanceWorker"
        const val INPUT_AUTH_TOKEN = "AUTH_TOKEN"
        const val BATCH_SIZE = 20
        const val ACTION_ATTENDANCE_SYNCED = "com.markme.app.ACTION_ATTENDANCE_SYNCED"
    }

    private val dbQueries: PendingAttendanceQueries
    private val api: ApiService

    init {
        dbQueries = DatabaseManager.getInstance(context).pendingAttendanceQueries
        api = RetrofitClient.instance
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val token = inputData.getString(INPUT_AUTH_TOKEN)
        if (token.isNullOrBlank()) {
            Log.e(TAG, "Auth token is missing. Stopping worker.")
            return@withContext Result.failure()
        }

        val authHeader = "Bearer $token"

        // Fetch pending records from SQLDelight
        val pendingRecords = try {
            dbQueries.getPending(limit = BATCH_SIZE.toLong()).executeAsList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read pending records from DB", e)
            return@withContext Result.retry()
        }

        if (pendingRecords.isEmpty()) {
            return@withContext Result.success()
        }

        val recordIds = pendingRecords.map { it.id }

        try {
            dbQueries.markAsSyncing(recordIds)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to mark records as SYNCING; continuing", e)
        }

        val events = pendingRecords.map { record ->
            AttendanceEvent(
                attendance = jsonToMap(record.attendance_blob),
                student_sig = record.student_sig
            )
        }

        val batchRequest = AttendanceBatch(events = events)

        try {
            val response = api.postAttendanceBatch(authHeader, batchRequest)

            var anyDeleted = false
            var hasTransientError = false
            var hasFatalError = false

            response.results.forEach { result ->
                // We need the original record to get metadata (className, timestamp) to save to history
                val originalRecord = pendingRecords.find { it.id == result.id }

                when (result.status) {
                    "ok" -> {
                        try {
                            // 1. Delete from Pending Queue
                            dbQueries.deleteById(result.id)

                            // 2. Insert into Verified History (NEW FEATURE)
                            if (originalRecord != null) {
                                dbQueries.insertVerified(
                                    id = result.id,
                                    sessionId = originalRecord.sess,
                                    className = originalRecord.className,
                                    status = "VERIFIED",
                                    timestamp = originalRecord.created_at,
                                    subjectId = null // We don't strictly know subjectId here, but that's okay
                                )
                            }

                            Log.i(TAG, "Successfully synced and moved to history: ${result.id}")
                            anyDeleted = true
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to update DB for: ${result.id}", e)
                            hasTransientError = true
                        }
                    }

                    // Fatal errors - stop retrying
                    "bad_signature", "unauthorized_device", "invalid_payload",
                    "unknown_session", "device_mismatch", "location_mismatch",
                    "nonce_missing", "nonce_time_mismatch" -> {
                        try {
                            dbQueries.markAsFailed(listOf(result.id))
                        } catch (e: Exception) {}
                        hasFatalError = true
                        Log.e(TAG, "Fatal sync error for ${result.id}: ${result.status}")
                    }

                    else -> {
                        try {
                            dbQueries.markAsFailed(listOf(result.id))
                        } catch (e: Exception) {}
                        hasTransientError = true
                    }
                }
            }

            // Broadcast so HistoryScreen refreshes
            if (anyDeleted) {
                try {
                    val intent = Intent(ACTION_ATTENDANCE_SYNCED)
                    context.sendBroadcast(intent)
                } catch (e: Exception) {}
            }

            return@withContext when {
                hasFatalError -> Result.failure()
                hasTransientError -> Result.retry()
                else -> Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error during sync", e)
            try {
                dbQueries.markAsFailed(recordIds)
            } catch (ex: Exception) {}
            return@withContext Result.retry()
        }
    }

    private fun jsonToMap(jsonString: String): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        try {
            val obj = JSONObject(jsonString)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.get(k)
                map[k] = when (v) {
                    is Number -> v
                    is Boolean -> v
                    else -> v.toString()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse JSON", e)
        }
        return map
    }
}