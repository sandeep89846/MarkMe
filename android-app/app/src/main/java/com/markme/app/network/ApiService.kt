package com.markme.app.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

// --- Data Classes for API Communication ---

data class TimeResponse(val utc: String)

// --- START NEW AUTH CLASSES ---
// For POST /api/auth/google-signin
data class GoogleSignInRequest(
    val idToken: String,
    val deviceId: String,
    val pubkeyPem: String
)
data class AuthResponse(val token: String, val status: String)
// --- END NEW AUTH CLASSES ---

// For GET /api/session/current
data class LocationData(val latitude: Double, val longitude: Double)
data class SessionInfoResponse(
    val sessionId: String,
    val className: String,
    val location: LocationData,
    val qrRotationIntervalMs: Long
)

// For data parsed from the QR code
data class QrNonceResponse(
    val qrNonce: String,
    val sessionId: String,
    val ts: String
)

// For POST /api/attendance/batch
data class AttendanceEvent(
    val attendance: Map<String, Any>,
    val student_sig: String
)
data class AttendanceBatch(val events: List<AttendanceEvent>)
data class BatchResultItem(val id: String, val status: String, val metadata: String?)
data class AttendanceBatchResponse(
    val results: List<BatchResultItem>,
    val server_time: String
)

// --- START NEW HISTORY CLASSES ---
data class Subject(
    val id: String,
    val code: String,
    val name: String
)
data class SubjectsResponse(val subjects: List<Subject>)

data class AttendanceHistoryItem(
    val id: String,
    val className: String,
    val status: String,
    val timestamp: String // ISO 8601 string
)
data class HistoryResponse(val history: List<AttendanceHistoryItem>)
// --- END NEW HISTORY CLASSES ---


// --- The Retrofit API Interface ---
interface ApiService {

    @GET("api/time")
    suspend fun getServerTime(): TimeResponse

    // --- START UPDATED/NEW ENDPOINTS ---
    @POST("api/auth/google-signin")
    suspend fun googleSignIn(@Body req: GoogleSignInRequest): AuthResponse

    @GET("api/session/current")
    suspend fun getCurrentSession(
        @Header("Authorization") token: String
    ): SessionInfoResponse

    @GET("api/student/my-subjects")
    suspend fun getMySubjects(
        @Header("Authorization") token: String
    ): SubjectsResponse

    @GET("api/student/my-history")
    suspend fun getMyHistory(
        @Header("Authorization") token: String,
        @Query("subjectId") subjectId: String
    ): HistoryResponse
    // --- END UPDATED/NEW ENDPOINTS ---

    @POST("api/attendance/batch")
    suspend fun postAttendanceBatch(
        @Header("Authorization") token: String,
        @Body batch: AttendanceBatch
    ): AttendanceBatchResponse
}