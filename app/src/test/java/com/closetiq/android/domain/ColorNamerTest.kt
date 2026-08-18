package com.closetiq.android.domain

import com.closetiq.android.domain.color.ColorNamer
import com.closetiq.android.domain.model.Category
import com.closetiq.android.domain.model.LabColor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every fixture here is a real Lab value measured from a garment photograph, paired with
 * the name a person actually gave that garment.
 *
 * That choice is deliberate. Twice on this project a green test sat next to a wrong
 * screen because the fixtures were invented — flat, extreme colours that no photo
 * produces. A colour namer tested on pure red and pure blue would pass while calling
 * every real garment in the closet "navy".
 */
class ColorNamerTest {

    @Test
    fun `neutrals are named by lightness alone`() {
        // Black cotton tee, Charcoal chinos, Grey hoodie, White sneakers.
        assertEquals("Black", ColorNamer.name(LabColor(22f, 0f, 0f)))
        assertEquals("Charcoal", ColorNamer.name(LabColor(34f, 0f, -1f)))
        assertEquals("Grey", ColorNamer.name(LabColor(55f, -1f, -2f)))
        assertEquals("White", ColorNamer.name(LabColor(88f, 0f, 2f)))
    }

    @Test
    fun `a near-grey is not given a hue name`() {
        // Charcoal chinos sit at chroma 1. The hue angle there is noise: b = -1f alone
        // would otherwise place it in the blue band and call it navy.
        assertEquals("Charcoal", ColorNamer.name(LabColor(34f, 0f, -1f)))
    }

    @Test
    fun `muted warm tones get wardrobe words, not pale versions of vivid ones`() {
        // Beige blazer, Khaki chinos, Brown leather boots — chroma 7, 21 and 21.
        assertEquals("Beige", ColorNamer.name(LabColor(75f, 6f, 3f)))
        assertEquals("Khaki", ColorNamer.name(LabColor(52f, 2f, 21f)))
        assertEquals("Brown", ColorNamer.name(LabColor(30f, 12f, 18f)))
    }

    @Test
    fun `muted greens and blues`() {
        // Sage linen shirt, Olive field jacket, Denim jacket, Navy crewneck.
        assertEquals("Sage", ColorNamer.name(LabColor(68f, -14f, 12f)))
        assertEquals("Olive", ColorNamer.name(LabColor(42f, -8f, 22f)))
        assertEquals("Slate blue", ColorNamer.name(LabColor(48f, -2f, -18f)))
        assertEquals("Navy", ColorNamer.name(LabColor(28f, 3f, -24f)))
    }

    @Test
    fun `a pale lilac is purple, not pink`() {
        // Measured from the lilac blouse in the demo wardrobe. It came back "Blush" before
        // purple had its own muted band — the wrong colour, not merely an imprecise one.
        assertEquals("Lilac", ColorNamer.name(LabColor(78f, 12f, -14f)))

        // The pink band still has to work either side of it.
        assertEquals("Blush", ColorNamer.name(LabColor(80f, 16f, 2f)))
        assertEquals("Aubergine", ColorNamer.name(LabColor(30f, 14f, -12f)))
    }

    @Test
    fun `vivid tones keep their own names`() {
        // Mustard cardigan, Rust corduroy shirt, Tan suede loafers, Green tee.
        assertEquals("Mustard", ColorNamer.name(LabColor(70f, 8f, 52f)))
        assertEquals("Rust", ColorNamer.name(LabColor(45f, 24f, 30f)))
        assertEquals("Tan", ColorNamer.name(LabColor(58f, 9f, 24f)))
        assertEquals("Green", ColorNamer.name(LabColor(37f, -37f, 20f)))
    }

    @Test
    fun `the same hue splits by lightness`() {
        // One band, three garments. Without the lightness tiers a dark olive and a
        // bright mustard would come back as the same word.
        val darkYellow = LabColor(30f, 6f, 40f)
        val midYellow = LabColor(50f, 8f, 46f)
        val lightYellow = LabColor(70f, 8f, 52f)

        assertEquals("Olive", ColorNamer.name(darkYellow))
        assertEquals("Ochre", ColorNamer.name(midYellow))
        assertEquals("Mustard", ColorNamer.name(lightYellow))
    }

    @Test
    fun `the hue band wraps around red without a gap`() {
        // atan2 puts a slightly blue-leaning red just under 360 and a slightly orange one
        // just over 0. Both are the same colour to a person.
        assertEquals("Brick", ColorNamer.name(LabColor(45f, 50f, -6f)))
        assertEquals("Brick", ColorNamer.name(LabColor(45f, 50f, 6f)))
    }

    @Test
    fun `an empty closet colour still names rather than crashing`() {
        // The placeholder a garment carries while its colour is still being extracted.
        assertEquals("Grey", ColorNamer.name(LabColor(50f, 0f, 0f)))
    }

    @Test
    fun `the suggested label pairs the colour with the user's own word for the garment`() {
        val mustard = LabColor(70f, 8f, 52f)

        assertEquals("Mustard jacket", ColorNamer.suggestLabel(mustard, Category.OUTERWEAR))
        assertEquals("Mustard top", ColorNamer.suggestLabel(mustard, Category.TOP))
        assertEquals("Mustard bottoms", ColorNamer.suggestLabel(mustard, Category.BOTTOM))
        assertEquals("Mustard shoes", ColorNamer.suggestLabel(mustard, Category.SHOES))
        assertEquals("Mustard dress", ColorNamer.suggestLabel(mustard, Category.DRESS))
    }
}
