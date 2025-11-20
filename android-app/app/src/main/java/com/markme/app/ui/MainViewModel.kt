package com.markme.app.ui

import android.content.Context
import android.location.Location
import android.util.Base64
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.markme.app.auth.EnrollmentManager
import com.markme.app.data.DatabaseManager
import com.markme.app.network.*
import com.markme.app.sync.AttendanceWorker
import com.markme.app.util.Canonicalizer
import com.markme.app.util.LocationProvider
import com.markme.app.util.TimeSyncManager
import com.markme.data.PendingAttendance
import com.markme.data.PendingAttendanceQueries
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException // <-- IMPORT THIS
import java.io.IOException // <-- IMPORT THIS
import java.security.Signature
import java.util.UUID
import java.util.concurrent.TimeUnit
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList

data class HomeUiState(
    val userMessage: String? = "Initializing...",
    val isLoading: Boolean = true,
    val isMarking: Boolean = false,
    val currentSession: SessionInfoResponse? = null,
    val attendanceHistory: List<PendingAttendance> = emptyList()
)

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    private lateinit var api: ApiService
    private lateinit var dbQueries: PendingAttendanceQueries
    private lateinit var workManager: WorkManager
    private lateinit var locationProvider: LocationProvider
    private lateinit var moshi: Moshi
    private lateinit var appContext: Context

    private var sessionToken: String? = null
    private var deviceId: String? = null
    private var tempLocation: Location? = null
    private var tempQrResponse: QrNonceResponse? = null

    fun initialize(context: Context) {
        this.appContext = context.applicationContext
        api = RetrofitClient.instance
        dbQueries = DatabaseManager.getInstance(appContext).pendingAttendanceQueries
        workManager = WorkManager.getInstance(appContext)
        locationProvider = LocationProvider(appContext)
        moshi = RetrofitClient.moshi

        val prefs = appContext.getSharedPreferences("MarkMePrefs", Context.MODE_PRIVATE)
        this.sessionToken = prefs.getString("SESSION_TOKEN", null)
        this.deviceId = prefs.getString("DEVICE_ID", null)

        if (sessionToken == null || deviceId == null) {
            _uiState.update { it.copy(isLoading = false, userMessage = "FATAL: Not authenticated.") }
            return
        }

        // Load Pending History from DB
        viewModelScope.launch {
            dbQueries.getAllPending().asFlow().mapToList(Dispatchers.IO).collectLatest { history ->
                _uiState.update { it.copy(attendanceHistory = history) }
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(userMessage = "Syncing time...") }
            // Try to sync time, but don't block if offline
            try {
                TimeSyncManager.syncWithServer(api)
            } catch (e: Exception) {
                Log.w("MainViewModel", "Time sync failed (likely offline)")
            }
            fetchCurrentSession(showLoading = true)
        }
    }

    fun retryFetchSession() {
        fetchCurrentSession(showLoading = true)
    }

    private fun fetchCurrentSession(showLoading: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        if (sessionToken == null) return@launch

        if (showLoading) {
            _uiState.update { it.copy(isLoading = true, userMessage = "Fetching class info...") }
        }

        try {
            val sessionInfo = api.getCurrentSession("Bearer $sessionToken")

            _uiState.update {
                it.copy(
                    userMessage = "Ready to mark for ${sessionInfo.className}",
                    isLoading = false,
                    currentSession = sessionInfo
                )
            }
            startPeriodicSync()

        } catch (e: HttpException) {
            // Server responded, but with an error (e.g., 404 Not Found)
            if (e.code() == 404) {
                _uiState.update {
                    it.copy(
                        userMessage = "No class scheduled right now.",
                        isLoading = false,
                        currentSession = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        userMessage = "Server Error: ${e.message()}",
                        isLoading = false,
                        currentSession = null
                    )
                }
            }
        } catch (e: IOException) {
            // Network error (No internet, timeout) -> ENABLE OFFLINE MODE
            Log.w("MainViewModel", "Network error fetching session", e)
            _uiState.update {
                it.copy(
                    userMessage = "Offline Mode: Tap 'Mark Attendance' to scan QR.",
                    isLoading = false,
                    currentSession = null
                )
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Unexpected error", e)
            _uiState.update {
                it.copy(
                    userMessage = "Error: ${e.message}",
                    isLoading = false,
                    currentSession = null
                )
            }
        }
    }

    fun onMarkAttendanceClicked() {
        if (_uiState.value.isMarking) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(userMessage = "Requesting location...", isMarking = true) }
            try {
                val location = locationProvider.getCurrentLocation()
                tempLocation = location
                _uiState.update {
                    it.copy(
                        userMessage = "Show teacher: ${location.latitude.format(6)}, ${location.longitude.format(6)}"
                    )
                }

            } catch (e: SecurityException) {
                _uiState.update { it.copy(userMessage = "Error: Location permission is required.", isMarking = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(userMessage = "Error: Could not get location. ${e.message}", isMarking = false) }
            }
        }
    }

    fun onScanQrClicked() {
        _uiState.update { it.copy(userMessage = "Please scan the QR code") }
    }

    fun onQrCodeScanned(qrData: String?) {
        if (qrData.isNullOrBlank()) {
            _uiState.update { it.copy(userMessage = "QR scan cancelled.", isMarking = false) }
            return
        }

        try {
            val adapter = moshi.adapter(QrNonceResponse::class.java)
            val qrNonceResponse = adapter.fromJson(qrData)

            if (qrNonceResponse == null) {
                _uiState.update { it.copy(userMessage = "Error: Invalid QR code.", isMarking = false) }
                return
            }

            // ONLINE CHECK: If we have a session, ensure QR matches it
            val currentSess = _uiState.value.currentSession
            if (currentSess != null && qrNonceResponse.sessionId != currentSess.sessionId) {
                _uiState.update { it.copy(userMessage = "Error: This QR code is for a different class.", isMarking = false) }
                return
            }

            // OFFLINE MODE: We don't have currentSess, so we trust the QR code for now.

            tempQrResponse = qrNonceResponse
            _uiState.update {
                it.copy(
                    userMessage = "QR Code Scanned. Tap 'Sign' to confirm.",
                    isMarking = true
                )
            }

        } catch (e: Exception) {
            Log.e("MainViewModel", "QR parse error", e)
            _uiState.update { it.copy(userMessage = "Error: Failed to read QR code. ${e.message}", isMarking = false) }
        }
    }

    fun onSignClicked(activity: AppCompatActivity) {
        viewModelScope.launch(Dispatchers.IO) {
            val location = tempLocation
            val qrResponse = tempQrResponse

            if (location == null || qrResponse == null) {
                _uiState.update { it.copy(userMessage = "Error: Missing data. Please start over.", isMarking = false) }
                return@launch
            }

            val hasAlreadyMarked = _uiState.value.attendanceHistory
                .any { it.sess == qrResponse.sessionId && it.status != "FAILED" }

            if (hasAlreadyMarked) {
                _uiState.update { it.copy(userMessage = "You have already marked attendance for this class.", isMarking = false) }
                return@launch
            }

            // If offline, we might not know the class name, so use a placeholder or date
            val className = _uiState.value.currentSession?.className ?: "Offline Class (${qrResponse.ts.take(10)})"

            _uiState.update { it.copy(userMessage = "Waiting for biometrics...") }

            withContext(Dispatchers.Main) {
                val onSignSuccess: (Signature) -> Unit = { unlockedSignature ->
                    createAndQueueRecord(unlockedSignature, qrResponse, location, className)
                }
                val onSignFailure: (String) -> Unit = { errorMsg ->
                    _uiState.update { it.copy(userMessage = errorMsg, isMarking = false) }
                }
                EnrollmentManager.promptForBiometricSignature(activity, onSignSuccess, onSignFailure)
            }
        }
    }

    private fun createAndQueueRecord(
        unlockedSignature: Signature,
        qr: QrNonceResponse,
        location: Location,
        className: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val blob = createAttendanceBlob(qr, location)
                val canonicalJson = Canonicalizer.canonicalize(blob)
                val dataToSign = canonicalJson.toByteArray(Charsets.UTF_8)

                unlockedSignature.update(dataToSign)
                val sigBytes = unlockedSignature.sign()
                val sigBase64 = Base64.encodeToString(sigBytes, Base64.NO_WRAP)

                dbQueries.insert(
                    id = blob["idempotency_key"] as String,
                    student_id = "deprecated",
                    device_id = blob["device_id"] as String,
                    sess = blob["sess"] as String,
                    qrNonce = blob["qrNonce"] as String,
                    lat = blob["lat"] as Double,
                    lon = blob["lon"] as Double,
                    className = className,
                    ts_client = blob["ts_client"] as String,
                    attendance_blob = canonicalJson,
                    student_sig = sigBase64,
                    created_at = TimeSyncManager.nowIsoUtcString()
                )

                _uiState.update {
                    it.copy(
                        userMessage = "SUCCESS! Attendance queued locally.",
                        isMarking = false
                    )
                }
                triggerOneTimeSync()

            } catch (e: Exception) {
                Log.e("MainViewModel", "Signing or DB error", e)
                _uiState.update { it.copy(userMessage = "Error during signing: ${e.message}", isMarking = false) }
            }
        }
    }

    private fun createAttendanceBlob(qr: QrNonceResponse, location: Location): Map<String, Any> {
        return mapOf(
            "ts_client" to TimeSyncManager.nowIsoUtcString(),
            "device_id" to deviceId!!,
            "sess" to qr.sessionId,
            "qrNonce" to qr.qrNonce,
            "idempotency_key" to UUID.randomUUID().toString(),
            "lat" to location.latitude.format(6).toDouble(),
            "lon" to location.longitude.format(6).toDouble()
        )
    }

    private fun Double.format(digits: Int): String = "%.${digits}f".format(this)

    private fun startPeriodicSync() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val workRequest = PeriodicWorkRequestBuilder<AttendanceWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInputData(Data.Builder().putString(AttendanceWorker.INPUT_AUTH_TOKEN, sessionToken).build())
            .build()
        workManager.enqueueUniquePeriodicWork("periodic-attendance-sync", ExistingPeriodicWorkPolicy.KEEP, workRequest)
    }

    private fun triggerOneTimeSync() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val workRequest = OneTimeWorkRequestBuilder<AttendanceWorker>()
            .setConstraints(constraints)
            .setInputData(Data.Builder().putString(AttendanceWorker.INPUT_AUTH_TOKEN, sessionToken).build())
            .build()
        workManager.enqueue(workRequest)
    }
}