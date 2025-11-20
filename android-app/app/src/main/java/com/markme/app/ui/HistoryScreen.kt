package com.markme.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize // <--- ADDED THIS IMPORT
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.markme.app.network.AttendanceHistoryItem
import com.markme.app.network.Subject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    historyViewModel: HistoryViewModel = viewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        historyViewModel.initialize(context)
    }

    val uiState by historyViewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text(
            text = "Attendance History",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (uiState.subjects.isEmpty() && uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.subjects.isEmpty() && uiState.userMessage != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = uiState.userMessage ?: "No subjects found.",
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.subjects) { subject ->
                    ExpandableSubjectCard(
                        subject = subject,
                        isExpanded = uiState.selectedSubject?.id == subject.id,
                        onCardClick = { historyViewModel.toggleSubjectSelection(subject) },
                        isLoading = uiState.isLoading,
                        historyItems = if (uiState.selectedSubject?.id == subject.id) uiState.attendanceHistory else emptyList()
                    )
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun ExpandableSubjectCard(
    subject: Subject,
    isExpanded: Boolean,
    onCardClick: () -> Unit,
    isLoading: Boolean,
    historyItems: List<AttendanceHistoryItem>
) {
    val containerColor = if (isExpanded)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceContainerHigh

    val contentColor = if (isExpanded)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurface

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded) 4.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCardClick() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subject.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Text(
                        text = subject.code,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = contentColor
                )
            }

            if (isExpanded) {
                HorizontalDivider(
                    color = contentColor.copy(alpha = 0.2f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (isLoading && historyItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = contentColor
                            )
                        }
                    } else if (historyItems.isEmpty()) {
                        Text(
                            text = "No attendance history available.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor.copy(alpha = 0.7f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        historyItems.forEach { record ->
                            HistoryRowItem(record, contentColor)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryRowItem(record: AttendanceHistoryItem, parentContentColor: Color) {
    val instant = try {
        Instant.parse(record.timestamp)
    } catch (e: Exception) { Instant.now() }

    val formatter = DateTimeFormatter.ofPattern("MMM dd, hh:mm a")
        .withZone(ZoneId.systemDefault())
    val formattedDate = formatter.format(instant)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Status: ${record.status}",
                style = MaterialTheme.typography.bodySmall,
                color = if (record.status == "VERIFIED") Color(0xFF1B5E20) else MaterialTheme.colorScheme.error
            )
        }
    }
}