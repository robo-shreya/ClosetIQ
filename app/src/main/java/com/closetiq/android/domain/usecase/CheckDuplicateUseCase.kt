package com.closetiq.android.domain.usecase

import com.closetiq.android.domain.model.Category
import com.closetiq.android.domain.model.Garment
import com.closetiq.android.domain.model.LabColor

/**
 * The "don't buy" check — cut from the 8-day scope, kept as a skeleton because it is
 * the strongest sustainability feature in the design and the one to build first after
 * the hackathon.
 *
 * Point it at a prospective purchase and it answers: do you already own this?
 */
class CheckDuplicateUseCase {

    data class Verdict(
        val alreadyOwn: List<Garment>,
        val message: String
    )

    /**
     * TODO(post-hackathon): find items in [closet] that are close in both colour and category.
     *
     * ColorMath.deltaE76 under about 15 reads as "the same colour" to a person.
     * Same category plus that threshold is the whole rule.
     *
     * The output line is the point of the feature: "You already own three navy tops."
     */
    operator fun invoke(
        closet: List<Garment>,
        candidateColor: LabColor,
        candidateCategory: Category
    ): Verdict {
        TODO("Find near-duplicate garments already in the closet")
    }
}
