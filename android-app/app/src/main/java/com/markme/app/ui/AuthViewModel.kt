package com.markme.app.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.markme.app.auth.EnrollmentManager
import com.markme.app.auth.NoBiometricException
import com.markme.app.network.ApiService
import com.markme.app.network.GoogleSignInRequest
import com.markme.app.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class AuthUiState(
    val isLoading: Boolean = false,
    val userMessage: String? = null,
    val isAuthenticated: Boolean = false
)

class AuthViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    private lateinit var api: ApiService
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var appContext: Context

    // This is the variable declaration
    private var deviceId: String? = null

    // TODO: Get this from your Google Cloud Console / strings.xml
    private val WEB_CLIENT_ID = "785046225223-9pvc27uun2ol3rrpa7sclj8jenbrg2l6.apps.googleusercontent.com"

    fun initialize(context: Context) {
        this.appContext = context.applicationContext
        api = RetrofitClient.instance

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(appContext, gso)

        checkIfAlreadyAuthenticated()
    }

    private fun checkIfAlreadyAuthenticated() {
        val prefs = appContext.getSharedPreferences("MarkMePrefs", Context.MODE_PRIVATE)
        val token = prefs.getString("SESSION_TOKEN", null)
        val storedDeviceId = prefs.getString("DEVICE_ID", null)

        if (token != null && storedDeviceId != null && EnrollmentManager.isKeyEnrolled()) {
            this.deviceId = storedDeviceId
            _uiState.update { it.copy(isAuthenticated = true) }
        } else {
            // Not fully enrolled, ensure we're signed out
            googleSignInClient.signOut()
            _uiState.update { it.copy(isLoading = false, userMessage = "Please sign in.") }
        }
    }

    fun startSignIn(launcher: ManagedActivityResultLauncher<Intent, ActivityResult>) {
        _uiState.update { it.copy(isLoading = true) }
        val signInIntent = googleSignInClient.signInIntent
        launcher.launch(signInIntent)
    }

    fun onSignInResult(result: ActivityResult) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            if (account?.idToken != null) {
                Log.i("AuthViewModel", "Google Sign-In Success. Got idToken.")
                enrollDevice(account.idToken!!)
            } else {
                _uiState.update { it.copy(isLoading = false, userMessage = "Sign-in failed: No ID Token.") }
            }
        } catch (e: ApiException) {
            Log.e("AuthViewModel", "Google Sign-In failed", e)
            _uiState.update { it.copy(isLoading = false, userMessage = "Sign-in failed: ${e.statusCode}") }
        }
    }

    private fun enrollDevice(idToken: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, userMessage = "Enrolling secure key...") }
            try {
                // 1. Generate secure key
                val pubKeyPem = EnrollmentManager.createKeyAndGetPublicPem()

                // 2. Get or create device ID
                val prefs = appContext.getSharedPreferences("MarkMePrefs", Context.MODE_PRIVATE)
                var storedDeviceId = prefs.getString("DEVICE_ID", null)
                if (storedDeviceId == null) {
                    storedDeviceId = "dev-${UUID.randomUUID()}"
                    prefs.edit().putString("DEVICE_ID", storedDeviceId).apply()
                }
                // This line (115) is now valid because the variable is declared above
                deviceId = storedDeviceId

                // 3. Call new server endpoint
                _uiState.update { it.copy(userMessage = "Registering device with server...") }
                val request = GoogleSignInRequest(
                    idToken = idToken,
                    deviceId = storedDeviceId,
                    pubkeyPem = pubKeyPem
                )
                val response = api.googleSignIn(request)

                // 4. Save session token
                prefs.edit().putString("SESSION_TOKEN", response.token).apply()
                Log.i("AuthViewModel", "Enrollment complete. Stored session token.")

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        userMessage = null,
                        isAuthenticated = true
                    )
                }

            } catch (e: NoBiometricException) {
                Log.e("AuthViewModel", "Enrollment failed: No biometrics.", e)
                _uiState.update { it.copy(isLoading = false, userMessage = "Enrollment Failed: Please enroll a Fingerprint or PIN and restart the app.") }
                googleSignInClient.signOut() // Sign out so they can retry
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Enrollment failed", e)
                _uiState.update { it.copy(isLoading = false, userMessage = "Enrollment failed: ${e.message}") }
                googleSignInClient.signOut() // Sign out so they can retry
            }
        }
    }

    fun signOut() {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Sign out from Google
            googleSignInClient.signOut()

            // 2. Clear local preferences
            val prefs = appContext.getSharedPreferences("MarkMePrefs", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()

            // 3. Update UI state to show AuthScreen
            _uiState.update {
                it.copy(
                    isAuthenticated = false,
                    userMessage = "You have been signed out."
                )
            }
        }
    }
}