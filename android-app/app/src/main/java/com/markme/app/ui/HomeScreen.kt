package com.markme.app.ui

import android.Manifest
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.markme.data.PendingAttendance
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel = viewModel()
) {
    val activity = LocalContext.current as AppCompatActivity
    val uiState by mainViewModel.uiState.collectAsState()

    val qrScannerLauncher = rememberLauncherForActivityResult(
        contract = ScanContract(),
        onResult = { result ->
            mainViewModel.onQrCodeScanned(result.contents)
        }
    )

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                mainViewModel.onMarkAttendanceClicked()
            }
        }
    )

    MarkMeAppScreen(
        uiState = uiState,
        onMarkAttendanceClick = {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        },
        onScanQrClick = {
            mainViewModel.onScanQrClicked()
        },
        onSignClick = {
            mainViewModel.onSignClicked(activity)
        },
        onRetryClick = {
            mainViewModel.retryFetchSession()
        },
        qrScannerLauncher = qrScannerLauncher,
        modifier = modifier
    )
}


@Composable
fun MarkMeAppScreen(
    uiState: HomeUiState,
    onMarkAttendanceClick: () -> Unit,
    onScanQrClick: () -> Unit,
    onSignClick: () -> Unit,
    onRetryClick: () -> Unit,
    qrScannerLauncher: ManagedActivityResultLauncher<ScanOptions, *>,
    modifier: Modifier = Modifier
) {

    if (uiState.userMessage?.contains("Please scan the QR code") == true) {
        val scanOptions = ScanOptions()
            .setPrompt("Scan the class QR code")
            .setBeepEnabled(true)
            .setOrientationLocked(true)
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)

        qrScannerLauncher.launch(scanOptions)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- MODIFICATION: Use padding to account for TopAppBar ---
        // Text(
        //     text = "Project: MarkMe",
        //     style = MaterialTheme.typography.headlineMedium,
        //     modifier = Modifier.padding(top = 32.dp)
        // )
        // --- END MODIFICATION ---

        uiState.currentSession?.let {
            Text(
                text = "Current Class: ${it.className}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        val hasMarkedToday = uiState.attendanceHistory.any {
            it.sess == uiState.currentSession?.sessionId &&
                    (it.status == "PENDING" ||
                            it.status == "SYNCING" || it.status == "VERIFIED" ||
                            it.status == "SYNCED" || it.status == "FAILED") // <-- ADD SYNCED AND FAILED
        }

        // 1. Handle loading and button states
        if (uiState.isLoading) {
            // State 1: App is loading (enrolling, syncing time, etc.)
            CircularProgressIndicator(modifier = Modifier.padding(vertical = 16.dp))
            Text(
                text = uiState.userMessage ?: "Loading...",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        } else if (uiState.currentSession == null) {
            // State 2: No session
            Text(
                text = "Status: ${uiState.userMessage}",
                style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 24.dp)
            )
            Button(onClick = onRetryClick) {
                Text("RETRY")
            }
        } else if (hasMarkedToday) {
            // State 3: Already Marked
            Button(onClick = {}, enabled = false) {
                Text("ATTENDANCE ALREADY MARKED")
            }
        } else if (uiState.isMarking && uiState.userMessage?.contains("Show teacher") == true) {
            // State 4: Got location, waiting for teacher
            Text(
                text = "Status: ${uiState.userMessage}", // "Show teacher: 26.250, 78.169"
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 24.dp)
            )
            Button(onClick = onScanQrClick) {
                Text("TEACHER HAS ENTERED, SCAN QR")
            }
        } else if (uiState.isMarking && uiState.userMessage?.contains("QR Code Scanned") == true) {
            // State 5: Scanned QR, waiting for sign
            Text(
                text = "Status: ${uiState.userMessage}", // "QR Code Scanned. Tap 'Sign'."
                style = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF1B5E20)), // Dark Green
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 24.dp)
            )
            Button(onClick = onSignClick) {
                Text("SIGN ATTENDANCE")
            }
        }
        else {
            // State 6: Enrolled and Idle
            Button(onClick = onMarkAttendanceClick) {
                Text("MARK ATTENDANCE")
            }
        }

        // Show the user message (e.g., "SUCCESS!")
        if(uiState.userMessage?.contains("SUCCESS") == true) {
            Text(
                text = uiState.userMessage,
                style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF1B5E20)),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        // 2. Handle Local Attendance History
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Local Sync Queue",
            style = MaterialTheme.typography.titleMedium,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(uiState.attendanceHistory) { record ->
                AttendanceHistoryItem(record = record)
            }
        }
    }
}

@Composable
fun AttendanceHistoryItem(record: PendingAttendance) {
    val (statusColor, statusText) = when (record.status) {
        "VERIFIED" -> Color(0xFF1B5E20) to "Verified" // Dark Green
        "SYNCED" -> Color(0xFF1B5E20) to "Verified" // Dark Green (Treat Synced as Verified)
        "PENDING" -> Color(0xFFE65100) to "Pending Sync" // Dark Orange
        "SYNCING" -> Color(0xFF0D47A1) to "Syncing..." // Dark Blue
        else -> Color(0xFFB71C1C) to "Failed" // Dark Red
    }

    val instant = Instant.parse(record.created_at)
    val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm a")
        .withZone(ZoneId.systemDefault())
    val formattedDate = formatter.format(instant)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // --- START FIX: Use className ---
                Text(
                    text = record.className,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                // --- END FIX ---
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}