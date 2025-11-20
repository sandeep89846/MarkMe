package com.markme.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markme.app.data.DatabaseManager
import com.markme.app.network.ApiService
import com.markme.app.network.AttendanceHistoryItem
import com.markme.app.network.RetrofitClient
import com.markme.app.network.Subject
import com.markme.data.PendingAttendanceQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val isLoading: Boolean = false,
    val userMessage: String? = null,
    val subjects: List<Subject> = emptyList(),
    val selectedSubject: Subject? = null,
    val attendanceHistory: List<AttendanceHistoryItem> = emptyList()
)

class HistoryViewModel : ViewModel() {

    companion object {
        const val TAG = "HistoryViewModel"
        const val ACTION_ATTENDANCE_SYNCED = "com.markme.app.ACTION_ATTENDANCE_SYNCED"
    }

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState = _uiState.asStateFlow()

    private lateinit var api: ApiService
    private lateinit var dbQueries: PendingAttendanceQueries
    private var sessionToken: String? = null
    private var appContext: Context? = null
    private var syncBroadcastReceiver: BroadcastReceiver? = null

    fun initialize(context: Context) {
        api = RetrofitClient.instance
        dbQueries = DatabaseManager.getInstance(context).pendingAttendanceQueries

        val prefs = context.getSharedPreferences("MarkMePrefs", Context.MODE_PRIVATE)
        this.sessionToken = prefs.getString("SESSION_TOKEN", null)
        this.appContext = context.applicationContext

        loadCachedSubjects()
        fetchSubjects()

        try {
            val filter = IntentFilter(ACTION_ATTENDANCE_SYNCED)
            syncBroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val selected = _uiState.value.selectedSubject
                    selected?.let {
                        fetchHistoryForSubject(it, forceReload = true)
                    }
                }
            }
            appContext?.registerReceiver(syncBroadcastReceiver, filter)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register receiver", e)
        }
    }

    private fun loadCachedSubjects() {
        viewModelScope.launch(Dispatchers.IO) {
            val cached = dbQueries.getAllSubjects().executeAsList()
            if (cached.isNotEmpty()) {
                val subjects = cached.map { Subject(it.id, it.code, it.name) }
                _uiState.update { it.copy(subjects = subjects) }
            }
        }
    }

    fun fetchSubjects() {
        if (sessionToken == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = api.getMySubjects("Bearer $sessionToken")

                dbQueries.transaction {
                    dbQueries.deleteAllSubjects()
                    response.subjects.forEach {
                        dbQueries.insertSubject(it.id, it.code, it.name)
                    }
                }

                _uiState.update {
                    it.copy(subjects = response.subjects, userMessage = null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch subjects from network", e)
                if (_uiState.value.subjects.isEmpty()) {
                    _uiState.update { it.copy(userMessage = "Offline mode: No subjects found.") }
                }
            }
        }
    }

    // --- THIS IS THE METHOD YOU WERE MISSING ---
    fun toggleSubjectSelection(subject: Subject) {
        if (_uiState.value.selectedSubject?.id == subject.id) {
            // If clicking the already open subject, close it
            _uiState.update { it.copy(selectedSubject = null, attendanceHistory = emptyList()) }
        } else {
            // Otherwise, fetch and open the new one
            fetchHistoryForSubject(subject)
        }
    }
    // -------------------------------------------

    fun fetchHistoryForSubject(subject: Subject, forceReload: Boolean = false) {
        if (!forceReload && subject.id == _uiState.value.selectedSubject?.id) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(isLoading = true, selectedSubject = subject, userMessage = null)
            }

            val cachedRecords = dbQueries.getVerifiedBySubject(subject.id).executeAsList()
            val historyItems = cachedRecords.map {
                AttendanceHistoryItem(it.id, it.className, it.status, it.timestamp)
            }
            _uiState.update { it.copy(attendanceHistory = historyItems) }

            if (sessionToken == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            try {
                val response = api.getMyHistory("Bearer $sessionToken", subject.id)

                dbQueries.transaction {
                    response.history.forEach { record ->
                        dbQueries.insertVerified(
                            id = record.id,
                            sessionId = "unknown",
                            className = record.className,
                            status = record.status,
                            timestamp = record.timestamp,
                            subjectId = subject.id
                        )
                    }
                }

                _uiState.update {
                    it.copy(isLoading = false, attendanceHistory = response.history)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch history from network", e)
                _uiState.update {
                    it.copy(isLoading = false, userMessage = "Showing offline history.")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            syncBroadcastReceiver?.let { appContext?.unregisterReceiver(it) }
        } catch (e: Exception) {}
    }
}