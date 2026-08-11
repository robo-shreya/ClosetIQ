package com.closetiq.android.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.closetiq.android.ui.components.Kicker
import com.closetiq.android.ui.theme.Nocturne

/**
 * The screen header: a kicker over a title, with an optional meta value opposite.
 *
 * Flush-left and asymmetric — content hugs the left edge and the whitespace collects on
 * the right, which is the system's stated direction.
 */
@Composable
fun ScreenHeader(
    kicker: String,
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    onBack: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            onBack?.let { back ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .border(1.dp, Nocturne.Neutral800, CircleShape)
                        .clickable(onClick = back),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "←",
                        style = MaterialTheme.typography.labelLarge,
                        color = Nocturne.Neutral300
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Kicker(kicker, color = Nocturne.Neutral600)
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Nocturne.Text
                )
            }
        }

        meta?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = Nocturne.Neutral600,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

/**
 * The bottom bar.
 *
 * Nocturne uses a short accent mark rather than an icon to show the active tab — the
 * accent as a line, not a flood. Marks stay solid; only freestanding rules fade.
 */
@Composable
fun BottomTabs(
    tabs: List<Screen>,
    currentRoute: String?,
    onSelect: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Nocturne.TabBar)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Nocturne.Neutral900)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            tabs.forEach { screen ->
                val selected = currentRoute == screen.route

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clickable { onSelect(screen) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
                ) {
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (selected) Nocturne.Accent else Nocturne.Neutral700
                            )
                    )
                    Text(
                        text = screen.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) Nocturne.Accent200 else Nocturne.Neutral600
                    )
                }
            }
        }
    }
}
