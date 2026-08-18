package com.closetiq.android.domain.usecase

import com.closetiq.android.domain.engine.PaletteEngine
import com.closetiq.android.domain.engine.SkinStateModifier
import com.closetiq.android.domain.model.Category
import com.closetiq.android.domain.model.Garment
import com.closetiq.android.domain.model.OutfitPick
import com.closetiq.android.domain.model.ScoredGarment
import com.closetiq.android.domain.model.SkinReading
import kotlin.random.Random

/**
 * Orchestration only — this one is written for you, because the wiring is not the lesson.
 *
 * It calls the three functions you are writing. Until those return real values this
 * throws, which is exactly what you want: the Mirror screen stays visibly broken until
 * the brain works, rather than showing something plausible and wrong.
 */
class GetTodaysPickUseCase(
    private val scoreGarment: ScoreGarmentUseCase,
    private val rankDormant: RankDormantUseCase
) {

    operator fun invoke(
        garments: List<Garment>,
        reading: SkinReading?,
        now: Long = System.currentTimeMillis()
    ): OutfitPick? {
        val active = garments.filter { it.isActive }
        if (active.isEmpty()) return null

        // With no reading we still need a palette to score against. A neutral reading
        // gives the baseline behaviour: colour still matters, today's skin just doesn't.
        val palette = PaletteEngine.buildPalette(reading ?: SkinReading.NEUTRAL)

        val scored = active
            .map { scoreGarment(it, palette, reading, now) }
            .sortedByDescending { it.total }

        val hero = scored.first()

        // The hero is the one we render. The tiles beside it complete an outfit.
        val supporting = completeOutfit(hero, scored)

        return OutfitPick(
            hero = hero,
            supporting = supporting,
            reason = reasonFor(hero, reading, now)
        )
    }

    /**
     * Another outfit, drawn at random rather than by rank.
     *
     * [invoke] is deterministic on purpose — the same closet and the same day produce the
     * same argument, which is what makes it an argument rather than a slot machine. That
     * also means it never changes until you wear something, so there has to be a second
     * door: this one, for when today's pick is correct and you still do not want to wear it.
     *
     * One garment is drawn per slot, uniformly, so every combination in the closet is
     * reachable. Scores are still computed because the hero and its reason come out of
     * them — the highest-scoring garment of the ones drawn leads, so even a random outfit
     * is explained by the item in it with the best case.
     */
    fun shuffled(
        garments: List<Garment>,
        reading: SkinReading?,
        random: Random = Random.Default,
        now: Long = System.currentTimeMillis()
    ): OutfitPick? {
        val active = garments.filter { it.isActive }
        if (active.isEmpty()) return null

        val palette = PaletteEngine.buildPalette(reading ?: SkinReading.NEUTRAL)
        val scored = active.map { scoreGarment(it, palette, reading, now) }

        fun drawFrom(vararg categories: Category): ScoredGarment? =
            scored.filter { it.garment.category in categories }.randomOrNull(random)

        // A dress is drawn against the top slot and then fills the bottom one too, the same
        // rule [completeOutfit] uses — otherwise a shuffled dress would arrive with trousers.
        val top = drawFrom(Category.TOP, Category.DRESS)
        val bottom = if (top?.garment?.category == Category.DRESS) null else drawFrom(Category.BOTTOM)

        val outfit = listOfNotNull(top, bottom, drawFrom(Category.OUTERWEAR), drawFrom(Category.SHOES))
        if (outfit.isEmpty()) return null

        val hero = outfit.maxBy { it.total }

        return OutfitPick(
            hero = hero,
            supporting = outfit.filterNot { it == hero }.take(MAX_SUPPORTING),
            reason = reasonFor(hero, reading, now)
        )
    }

    /**
     * Both halves, always. The dormancy half is the product's premise and the skin half is
     * what makes today different from yesterday — either one alone reads as a lesser app.
     * Skin is appended only when there is a reading, so the sentence degrades to just the
     * premise rather than to nothing.
     */
    private fun reasonFor(hero: ScoredGarment, reading: SkinReading?, now: Long): String =
        buildString {
            append(dormancyPhrase(hero.garment, now))
            if (reading != null) {
                append(' ')
                append(SkinStateModifier.explain(hero.garment.color, reading))
            }
        }

    /**
     * The four things a suggested outfit is made of.
     *
     * Outerwear is its own slot rather than an alternative to a top, because a blazer and
     * the shirt under it are two decisions. Only one of them can be *rendered* — try-on
     * replaces a body region instead of layering over it — but both are worth suggesting.
     */
    private enum class Slot { OUTER, TOP, BOTTOM, FEET }

    private companion object {
        /** The Mirror shows the hero plus three tiles: outerwear, top, bottom, shoes. */
        const val MAX_SUPPORTING = 3

        /** Which categories already fill a slot. A dress fills both halves at once. */
        val COVERAGE = linkedMapOf(
            Slot.OUTER to setOf(Category.OUTERWEAR),
            Slot.TOP to setOf(Category.TOP, Category.DRESS),
            Slot.BOTTOM to setOf(Category.BOTTOM, Category.DRESS),
            Slot.FEET to setOf(Category.SHOES)
        )

        /** What to reach for when a slot is empty. */
        val PREFERRED = mapOf(
            Slot.OUTER to Category.OUTERWEAR,
            Slot.TOP to Category.TOP,
            Slot.BOTTOM to Category.BOTTOM,
            Slot.FEET to Category.SHOES
        )
    }

    /**
     * Fills out the hero into a whole outfit: outerwear, top, bottom and shoes.
     *
     * The previous rule only excluded the hero's own category, which let both tiles come
     * back as the same kind of thing — two tees beside a blazer. Ranking alone will always
     * do that, because the most dormant garments in a wardrobe cluster: nobody neglects one
     * item per category evenly.
     *
     * So the slots are filled by what the outfit still needs rather than by score order
     * alone. Within a needed slot the highest-scoring garment still wins, so dormancy and
     * today's skin decide *which* top — just not whether a top is what is missing.
     */
    private fun completeOutfit(
        hero: ScoredGarment,
        scored: List<ScoredGarment>
    ): List<ScoredGarment> {
        val needed = COVERAGE.keys.filterNot { part ->
            COVERAGE[part]?.contains(hero.garment.category) == true
        }

        val taken = mutableSetOf(hero)

        return needed.mapNotNull { slot ->
            val wanted = PREFERRED[slot] ?: return@mapNotNull null
            scored.firstOrNull { it !in taken && it.garment.category == wanted }
                ?.also { taken += it }
        }.take(MAX_SUPPORTING)
    }

    private fun dormancyPhrase(garment: Garment, now: Long): String {
        val lastWorn = garment.lastWornAt ?: return "You've never worn this."

        return when (val days = (now - lastWorn) / RankDormantUseCase.MILLIS_PER_DAY) {
            0L -> "You wore this today."
            1L -> "You wore this yesterday."
            else -> "You haven't worn this in $days days."
        }
    }

}
