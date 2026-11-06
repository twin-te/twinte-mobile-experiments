package net.twinte.mobile_experiments

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun TodayScreen(uiState: DailyTimetableUiState, onRefresh: () -> Unit) {
    ScreenColumn {
        RefreshButton(isLoading = uiState.isLoading, onClick = onRefresh)
        Text(uiState.message, style = MaterialTheme.typography.bodyMedium)
        Text("Date: ${uiState.date}")
        uiState.moduleLabel?.let { Text("Module: $it") }
        uiState.eventLabel?.let { Text("Day: $it") }
        uiState.lectureCount?.let { Text("Today's courses: $it") }
        uiState.firstCourseName?.let { Text("First today: $it") }
        uiState.lastSuccessfulSyncAt?.let {
            Text("Last sync: $it", style = MaterialTheme.typography.bodySmall)
        }
        if (uiState.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}
