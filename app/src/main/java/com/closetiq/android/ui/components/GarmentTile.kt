package com.closetiq.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.closetiq.android.domain.color.ColorMath
import com.closetiq.android.domain.model.Garment
import com.closetiq.android.domain.model.GarmentStatus
import com.closetiq.android.domain.model.LabColor
import com.closetiq.android.ui.theme.Nocturne
import java.io.File

/** Lab to a Compose colour, for the garment swatches. */
fun LabColor.toComposeColor(): Color = Color(ColorMath.labToRgb(this) or 0xFF000000.toInt())

/**
 * A garment swatch reads as a soft gradient rather than a flat fill, so it sits in the
 * dark ground the way a photograph would instead of stamping a rectangle onto it.
 */
private fun LabColor.toTileBrush(): Brush {
    val base = toComposeColor()
    return Brush.linearGradient(listOf(base, lerp(base, Nocturne.Bg, DEPTH_FRACTION)))
}

/** How far the swatch falls toward the ground at its far corner. */
private const val DEPTH_FRACTION = 0.28f

/** Fixed so every row of the three-column grid lines up. */
private const val TILE_HEIGHT_DP = 92

/**
 * One item in a closet grid.
 *
 * Seeded garments have no photo and render as a colour swatch; photographed ones render
 * the photo and carry a small "photo" tag, so the two are always distinguishable at a
 * glance. Colour is what the scoring engine consumes either way.
 */
@Composable
fun GarmentTile(
    garment: Garment,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color = Nocturne.Neutral600
) {
    val processing = garment.status == GarmentStatus.PROCESSING

    Column(
        modifier = modifier
            .clip(RadiusMd)
            .background(Nocturne.Surface)
            .border(1.dp, Nocturne.Neutral800, RadiusMd)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TILE_HEIGHT_DP.dp)
                .background(garment.color.toTileBrush())
                .alpha(if (processing) 0.5f else 1f),
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

            when {
                processing -> NocturneSpinner(size = 18)

                garment.imagePath != null -> Kicker(
                    text = "photo",
                    color = Nocturne.Neutral300,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 7.dp, bottom = 6.dp)
                )
            }
        }

        Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 7.dp, bottom = 8.dp)) {
            Text(
                text = garment.label,
                style = MaterialTheme.typography.bodySmall,
                color = Nocturne.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle ?: garment.category.name.lowercase(),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
