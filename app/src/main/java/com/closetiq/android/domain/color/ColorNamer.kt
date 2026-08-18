package com.closetiq.android.domain.color

import com.closetiq.android.domain.model.Category
import com.closetiq.android.domain.model.LabColor

/**
 * Turns a measured garment colour into a name a person would actually use, so the add
 * screen can propose "Mustard jacket" instead of an empty field.
 *
 * Pure Kotlin, in `domain`, so the whole table is unit-testable without an emulator.
 *
 * The bands were calibrated against 21 hand-named garment photographs —
 * those labels are a person's own vocabulary for colours the app already stores as Lab,
 * which makes them the only honest fixture available. Fourteen of the twenty-one come
 * back with the same word a human chose; the rest land on a near synonym.
 *
 * This is a suggestion, never a decision. Every name here is one the user overwrites.
 */
object ColorNamer {

    /**
     * A colour name on its own, capitalised for the start of a label.
     *
     * Chroma is read before hue on purpose. A hue angle is meaningless once a colour is
     * nearly grey — a charcoal garment has an arbitrary hue that would otherwise come
     * back "navy" or "olive" depending on sensor noise.
     */
    fun name(color: LabColor): String {
        val chroma = ColorMath.chroma(color)

        return when {
            chroma < NEUTRAL_CHROMA -> neutralName(color.l)
            chroma < MUTED_CHROMA -> mutedName(ColorMath.hueAngle(color), color.l)
            else -> vividName(ColorMath.hueAngle(color), color.l)
        }
    }

    /** "Mustard jacket" — the colour plus what the user just told us the thing is. */
    fun suggestLabel(color: LabColor, category: Category): String =
        "${name(color)} ${noun(category)}"

    private fun neutralName(l: Float): String = when {
        l >= 85f -> "White"
        l >= 70f -> "Off-white"
        l >= 45f -> "Grey"
        l >= 25f -> "Charcoal"
        else -> "Black"
    }

    /**
     * Colours with some hue but little saturation. These are most of a real wardrobe, and
     * they need their own vocabulary: a desaturated yellow is beige or khaki, never a pale
     * mustard.
     */
    private fun mutedName(hue: Float, l: Float): String = when (family(hue)) {
        Family.WARM -> when {
            l >= 70f -> "Beige"
            l >= 52f -> "Khaki"
            l >= 38f -> "Olive"
            else -> "Brown"
        }
        // Sage starts lower than the other pale tiers. A sage garment measures around
        // L 68, and at 70 the real one in the seed closet came back "olive".
        Family.GREEN -> when {
            l >= 60f -> "Sage"
            l >= 40f -> "Olive"
            else -> "Forest"
        }
        Family.BLUE -> when {
            l >= 70f -> "Powder blue"
            l >= 45f -> "Slate blue"
            else -> "Navy"
        }
        Family.PURPLE -> when {
            l >= 70f -> "Lilac"
            l >= 45f -> "Mauve"
            else -> "Aubergine"
        }
        Family.PINK -> when {
            l >= 70f -> "Blush"
            l >= 45f -> "Rose"
            else -> "Plum"
        }
    }

    private fun vividName(hue: Float, l: Float): String = when {
        hue >= 345f || hue < 20f -> when {
            l < 30f -> "Burgundy"
            l < 55f -> "Brick"
            l < 75f -> "Red"
            else -> "Rose"
        }
        hue < 45f -> when {
            l < 30f -> "Chocolate"
            l < 55f -> "Terracotta"
            l < 75f -> "Coral"
            else -> "Peach"
        }
        hue < 70f -> when {
            l < 35f -> "Chocolate"
            l < 55f -> "Rust"
            l < 75f -> "Tan"
            else -> "Cream"
        }
        hue < 105f -> when {
            l < 40f -> "Olive"
            l < 60f -> "Ochre"
            l < 85f -> "Mustard"
            else -> "Butter"
        }
        hue < 160f -> when {
            l < 35f -> "Forest"
            l < 60f -> "Green"
            l < 80f -> "Sage"
            else -> "Mint"
        }
        hue < 200f -> when {
            l < 45f -> "Teal"
            l < 75f -> "Aqua"
            else -> "Pale aqua"
        }
        hue < 260f -> when {
            l < 35f -> "Petrol"
            l < 65f -> "Steel blue"
            else -> "Powder blue"
        }
        hue < 300f -> when {
            l < 35f -> "Navy"
            l < 65f -> "Blue"
            else -> "Sky blue"
        }
        hue < 330f -> when {
            l < 35f -> "Aubergine"
            l < 65f -> "Purple"
            else -> "Lilac"
        }
        else -> when {
            l < 35f -> "Plum"
            l < 70f -> "Raspberry"
            else -> "Blush"
        }
    }

    private enum class Family { WARM, GREEN, BLUE, PURPLE, PINK }

    /**
     * Coarse bands for the muted table, which needs five names rather than eleven.
     *
     * Purple is its own band because without it a pale lilac lands in the pink family and
     * comes back "Blush" — which is what a real lilac blouse got, and it reads as the wrong
     * colour rather than merely an imprecise one.
     */
    private fun family(hue: Float): Family = when {
        hue < 20f -> Family.PINK
        hue < 105f -> Family.WARM
        hue < 200f -> Family.GREEN
        hue < 300f -> Family.BLUE
        hue < 345f -> Family.PURPLE
        else -> Family.PINK
    }

    /** What the user calls the garment, rather than the enum's name for it. */
    private fun noun(category: Category): String = when (category) {
        Category.TOP -> "top"
        Category.BOTTOM -> "bottoms"
        Category.OUTERWEAR -> "jacket"
        Category.DRESS -> "dress"
        Category.SHOES -> "shoes"
    }

    /** Below this a hue angle is noise, not a colour. */
    private const val NEUTRAL_CHROMA = 5f

    /** Between the two thresholds a colour reads as a muted version of its hue. */
    private const val MUTED_CHROMA = 24f
}
