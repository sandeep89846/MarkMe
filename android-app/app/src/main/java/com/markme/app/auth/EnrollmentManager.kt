package com.markme.app.auth

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.InvalidAlgorithmParameterException
import java.util.concurrent.Executor

// A custom exception to make our error handling cleaner in the ViewModel
class NoBiometricException(message: String) : Exception(message)

object EnrollmentManager {

    private const val KEY_ALIAS = "com.markme.app.SIGNING_KEY"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TAG = "EnrollmentManager"

    fun isKeyEnrolled(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            keyStore.containsAlias(KEY_ALIAS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check key existence", e)
            false
        }
    }

    @Throws(NoBiometricException::class)
    fun createKeyAndGetPublicPem(): String {
        try {
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                ANDROID_KEYSTORE
            )

            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN
            )
                .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(true)
                .build()

            kpg.initialize(spec)
            val kp = kpg.generateKeyPair()
            val publicKey = kp.public

            val pubKeyBytes = publicKey.encoded
            val pubKeyBase64 = Base64.encodeToString(pubKeyBytes, Base64.NO_WRAP)
            return "-----BEGIN PUBLIC KEY-----\n$pubKeyBase64\n-----END PUBLIC KEY-----\n"

        } catch (e: InvalidAlgorithmParameterException) {
            Log.e(TAG, "Failed to create keypair, biometrics not enrolled?", e)
            throw NoBiometricException("Keyguard not set up. Please enroll a PIN, Pattern, or Fingerprint in your device's Security settings.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create keypair", e)
            throw RuntimeException("Failed to create keypair", e)
        }
    }


    fun promptForBiometricSignature(
        activity: AppCompatActivity,
        onSuccess: (Signature) -> Unit,
        onFailure: (String) -> Unit
    ) {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            val privateKey = keyStore.getKey(KEY_ALIAS, null) as? PrivateKey
                ?: throw RuntimeException("Private key not found. Is device enrolled?")

            val signature = Signature.getInstance("SHA256withECDSA").apply {
                initSign(privateKey)
            }

            val executor = ContextCompat.getMainExecutor(activity)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Confirm Attendance")
                .setSubtitle("Authenticate to sign your attendance record")
                // --- START FIX ---
                // .setNegativeButtonText("Cancel") // <-- REMOVE THIS LINE
                // --- END FIX ---
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()

            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val cryptoObject = result.cryptoObject
                    if (cryptoObject?.signature != null) {
                        onSuccess(cryptoObject.signature!!)
                    } else {
                        onFailure("Authentication succeeded but signature object was null.")
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.e(TAG, "Auth error: $errorCode - $errString")
                    onFailure("Authentication error: $errString")
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.w(TAG, "Authentication failed (e.g., wrong finger).")
                }
            }

            BiometricPrompt(activity as androidx.fragment.app.FragmentActivity, executor, callback)
                .authenticate(promptInfo, BiometricPrompt.CryptoObject(signature))

        } catch (e: Exception) {
            Log.e(TAG, "Biometric prompt failed to start", e)
            onFailure("Biometric error: ${e.message}")
        }
    }
}