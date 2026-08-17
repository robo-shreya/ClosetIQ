package com.closetiq.android.domain

import com.closetiq.android.domain.model.PersonPhotos
import com.closetiq.android.domain.model.PhotoSlot
import com.closetiq.android.domain.model.RenderTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The resolution rules matter because getting them wrong costs real money quietly.
 *
 * `cloth` reports a render it could not do as *success* with an empty results object, so
 * a lower-body try-on aimed at a head-and-shoulders selfie spends a credit, returns no
 * image and raises no error. Every "returns null" case below is a credit not spent.
 */
class PersonPhotosTest {

    private val full = PersonPhotos(
        selfie = "selfie.jpg",
        fullBody = "full.jpg",
        upperBody = "upper.jpg",
        lowerBody = "lower.jpg"
    )

    @Test
    fun `an exact slot always wins`() {
        assertEquals("upper.jpg", full.bestFor(RenderTarget.UPPER_BODY))
        assertEquals("lower.jpg", full.bestFor(RenderTarget.LOWER_BODY))
        assertEquals("full.jpg", full.bestFor(RenderTarget.FULL_BODY))
    }

    @Test
    fun `a full body shot stands in for any region`() {
        val onlyFull = PersonPhotos(selfie = "selfie.jpg", fullBody = "full.jpg")

        assertEquals("full.jpg", onlyFull.bestFor(RenderTarget.UPPER_BODY))
        assertEquals("full.jpg", onlyFull.bestFor(RenderTarget.LOWER_BODY))
        assertEquals("full.jpg", onlyFull.bestFor(RenderTarget.SHOES))
        assertEquals("full.jpg", onlyFull.bestFor(RenderTarget.AUTO))
    }

    @Test
    fun `a selfie plus a lower body shot cannot dress the torso`() {
        // The exact profile that produced the live failure: the Mirror's hero was a top,
        // nothing upper-body was on file, and the selfie got sent to the cloth endpoint.
        val asFilmed = PersonPhotos(selfie = "selfie.jpg", lowerBody = "lower.jpg")

        assertNull(asFilmed.bestFor(RenderTarget.UPPER_BODY))
        assertEquals(PhotoSlot.UPPER_BODY, asFilmed.preferredSlotFor(RenderTarget.UPPER_BODY))
        assertEquals("lower.jpg", asFilmed.bestFor(RenderTarget.LOWER_BODY))
    }

    @Test
    fun `a selfie is never a render source, for any target`() {
        val onlySelfie = PersonPhotos(selfie = "selfie.jpg")

        // This regressed once. The selfie was allowed to stand in for UPPER_BODY, on the
        // theory that a head-and-shoulders shot carries enough chest to dress. YouCam
        // rejected exactly that with error_src_face_too_small, from the Mirror, on a real
        // device — and it contradicted what the app promises the selfie is for.
        RenderTarget.entries.forEach { target ->
            assertNull(
                "$target must not resolve to the selfie",
                onlySelfie.bestFor(target)
            )
        }
    }

    @Test
    fun `an upper body shot is refused for lower body and shoes`() {
        val upperOnly = PersonPhotos(selfie = "selfie.jpg", upperBody = "upper.jpg")

        assertNull(upperOnly.bestFor(RenderTarget.LOWER_BODY))
        assertNull(upperOnly.bestFor(RenderTarget.SHOES))
        assertEquals("upper.jpg", upperOnly.bestFor(RenderTarget.UPPER_BODY))
    }

    @Test
    fun `a lower body shot covers shoes but never a top`() {
        val lowerOnly = PersonPhotos(lowerBody = "lower.jpg")

        assertEquals("lower.jpg", lowerOnly.bestFor(RenderTarget.SHOES))
        assertEquals("lower.jpg", lowerOnly.bestFor(RenderTarget.LOWER_BODY))
        assertNull(lowerOnly.bestFor(RenderTarget.UPPER_BODY))
    }

    @Test
    fun `nothing on file resolves to nothing, for every target`() {
        RenderTarget.entries.forEach { target ->
            assertNull("$target should resolve to null", PersonPhotos.EMPTY.bestFor(target))
        }
    }

    @Test
    fun `the preferred slot names the photo a target wants`() {
        // Read one way it is "which photo is missing", read the other it is "where does a
        // photo the user just supplied belong". Both callers need the same answer.
        assertEquals(PhotoSlot.LOWER_BODY, full.preferredSlotFor(RenderTarget.LOWER_BODY))
        assertEquals(PhotoSlot.UPPER_BODY, full.preferredSlotFor(RenderTarget.UPPER_BODY))
        assertEquals(PhotoSlot.FULL_BODY, full.preferredSlotFor(RenderTarget.SHOES))
        assertEquals(PhotoSlot.FULL_BODY, full.preferredSlotFor(RenderTarget.FULL_BODY))
        assertEquals(PhotoSlot.FULL_BODY, full.preferredSlotFor(RenderTarget.AUTO))
    }

    @Test
    fun `saving into the preferred slot makes a blocked target resolvable`() {
        // The exact round trip the add screen performs: nothing on file, the guard names a
        // slot, the user picks a photo, and the render it refused now works.
        val target = RenderTarget.LOWER_BODY
        assertNull(PersonPhotos.EMPTY.bestFor(target))

        val slot = PersonPhotos.EMPTY.preferredSlotFor(target)
        val afterPick = PersonPhotos.EMPTY.with(slot, "legs.jpg")

        assertEquals("legs.jpg", afterPick.bestFor(target))
    }

    @Test
    fun `slots read and write independently`() {
        val one = PersonPhotos.EMPTY.with(PhotoSlot.LOWER_BODY, "lower.jpg")

        assertEquals("lower.jpg", one[PhotoSlot.LOWER_BODY])
        assertNull(one[PhotoSlot.SELFIE])

        val two = one.with(PhotoSlot.SELFIE, "selfie.jpg")
        assertEquals("lower.jpg", two[PhotoSlot.LOWER_BODY])
        assertEquals("selfie.jpg", two[PhotoSlot.SELFIE])
    }

    @Test
    fun `replacing a slot leaves the others alone`() {
        val replaced = full.with(PhotoSlot.SELFIE, "new-selfie.jpg")

        assertEquals("new-selfie.jpg", replaced.selfie)
        assertEquals("full.jpg", replaced.fullBody)
        assertEquals("upper.jpg", replaced.upperBody)
        assertEquals("lower.jpg", replaced.lowerBody)
    }

    @Test
    fun `hasAny distinguishes an empty profile from a partial one`() {
        assertFalse(PersonPhotos.EMPTY.hasAny)
        assertTrue(PersonPhotos(lowerBody = "lower.jpg").hasAny)
    }
}
