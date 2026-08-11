package com.closetiq.android.ui.buycheck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.closetiq.android.ui.components.DashedPanel
import com.closetiq.android.ui.theme.Nocturne

/**
 * The "don't buy" check — cut from the 8-day scope, kept as a stub because it is the
 * strongest sustainability feature in the design and the first thing to build afterwards.
 *
 * Registered as a route but nothing navigates to it, so it cannot accidentally appear
 * half-finished in the demo video.
 */
@Composable
fun BuyCheckScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DashedPanel(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Should you buy it?",
                style = MaterialTheme.typography.titleMedium,
                color = Nocturne.Neutral300
            )
            Text(
                text = "Point it at something you are about to buy and it answers two " +
                    "questions: does this suit you, and do you already own it?",
                style = MaterialTheme.typography.bodyMedium,
                color = Nocturne.Neutral600
            )
        }

        OutlinedButton(onClick = onBack) {
            Text("Back", color = Nocturne.Text)
        }
    }
}
