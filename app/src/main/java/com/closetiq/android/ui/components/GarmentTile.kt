package com.closetiq.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.closetiq.android.domain.color.ColorMath
import com.closetiq.android.domain.model.Garment
import com.closetiq.android.domain.model.GarmentStatus
import com.closetiq.android.domain.model.LabColor
import java.io.File

/** Lab to a Compose colour, for swatches. */
fun LabColor.toComposeColor(): Color = Color(ColorMath.labToRgb(this) or 0xFF000000.toInt())

/**
 * One item in the closet grid.
 *
 * Seeded garments have no photo and render as a flat colour swatch. That is deliberate:
 * colour is what the scoring engine actually consumes, so nothing that matters is faked,
 * and there are no image assets to ship.
 */
@Composable
fun GarmentTile(
    garment: Garment,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val processing = garment.status == GarmentStatus.PROCESSING

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.85f)
                .clip(RoundedCornerShape(10.dp))
                .background(garment.color.toComposeColor())
                .alpha(if (processing) 0.4f else 1f),
            contentAlignment = Alignment.Center
        ) {
            garment.imagePath?.let { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = garment.label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            if (processing) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            }
        }

        Text(
            text = garment.label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )

        Text(
            text = subtitle ?: garment.category.name.lowercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
