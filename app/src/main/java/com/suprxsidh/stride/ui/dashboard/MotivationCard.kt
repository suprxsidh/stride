package com.suprxsidh.stride.ui.dashboard

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.suprxsidh.stride.ui.theme.Spacing
import com.suprxsidh.stride.ui.theme.component.PunchCardRow

@Composable
fun MotivationCard(line: String) {
    PunchCardRow(modifier = Modifier.padding(top = Spacing.xs)) {
        Text(line, style = MaterialTheme.typography.bodyLarge)
    }
}
