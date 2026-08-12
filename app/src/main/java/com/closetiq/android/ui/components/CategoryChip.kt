package com.closetiq.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.closetiq.android.domain.model.Category
import com.closetiq.android.ui.theme.Nocturne

private val ChipShape = RoundedCornerShape(99.dp)

/** A pill that takes the accent when chosen and a hairline outline when not. */
@Composable
fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(ChipShape)
            .background(if (selected) Nocturne.Accent900 else Nocturne.Bg)
            .border(
                width = 1.dp,
                color = if (selected) Nocturne.Accent else Nocturne.Neutral800,
                shape = ChipShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Nocturne.Accent200 else Nocturne.Neutral400
        )
    }
}

/**
 * The full category row. Both the add-item and buy-check flows ask exactly this
 * question, so they ask it with exactly the same control.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(
    selected: Category,
    onSelect: (Category) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Category.entries.forEach { category ->
            CategoryChip(
                label = category.name.lowercase(),
                selected = selected == category,
                onClick = { onSelect(category) }
            )
        }
    }
}
