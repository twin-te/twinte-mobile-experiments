package net.twinte.mobile_experiments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide

@Composable
internal fun TimetableYearSelector(year: Int, onPreviousYear: () -> Unit, onNextYear: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconButton("Previous year", Lucide.ChevronLeft, onClick = onPreviousYear)
        Text("${year}年度", modifier = Modifier.testTag("selected-year"))
        AppIconButton("Next year", Lucide.ChevronRight, onClick = onNextYear)
    }
}
