package com.closetiq.android.ui.buycheck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The "don't buy" check — cut from the 8-day scope, kept as a stub because it is the
 * strongest sustainability feature in the design and the first thing to build afterwards.
 *
 * Not wired into the bottom bar. Reachable only if you navigate to it deliberately, so
 * it cannot accidentally appear in the demo video half-finished.
 *
 * TODO(post-hackathon): photo of a prospective buy → colour extraction → CheckDuplicateUseCase
 * → "you already own three navy tops", plus a palette score and a VTO render.
 */
@Composable
fun BuyCheckScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Should you buy it?", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Not built yet. Point it at something you are about to buy and it answers " +
                "two questions: does this suit you, and do you already own it?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = onBack) { Text("Back") }
    }
}
