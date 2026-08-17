package com.closetiq.android.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format for the local backend.
 *
 * One task shape covers both YouCam capabilities, because both are async in exactly the
 * same way: create a task, poll it. Keeping them identical means one poller in the app
 * instead of two, and it is what let the backend swap between providers — a local Gemma
 * mock during early development, real YouCam now — without touching this file.
 */

@Serializable
enum class TaskKind {
    @SerialName("TRY_ON")
    TRY_ON,

    @SerialName("SKIN_ANALYSIS")
    SKIN_ANALYSIS
}

@Serializable
data class ImagePayload(
    /** Base64, no data: prefix. */
    val base64: String,
    val mimeType: String = "image/jpeg"
)

@Serializable
data class CreateTaskRequest(
    val kind: TaskKind,
    /** TRY_ON: the photo of the person. SKIN_ANALYSIS: the selfie. */
    val personImage: ImagePayload? = null,
    /** TRY_ON only. */
    val garmentImage: ImagePayload? = null,
    /** TRY_ON only — AUTO, FULL_BODY, UPPER_BODY, LOWER_BODY, SHOES. */
    val renderTarget: String? = null
)

@Serializable
data class CreateTaskResponse(
    val taskId: String,
    val status: String
)

@Serializable
data class TaskResponse(
    val taskId: String,
    /** "processing" | "success" | "failed" */
    val status: String,
    val result: TaskResult? = null,
    val error: String? = null
)

@Serializable
data class TaskResult(
    /**
     * TRY_ON. Null when nothing was produced — `cloth` can report task_status "success"
     * with an empty results object when it can't use the photos it was given, rather than
     * an error. [note] is what tells the user why.
     */
    val imageUrl: String? = null,
    /** Provider commentary — explains an empty [imageUrl], or carries the plain error. */
    val note: String? = null,
    /** SKIN_ANALYSIS. */
    val skin: SkinResultDto? = null
)

@Serializable
data class SkinResultDto(
    /** "WARM" | "COOL" | "NEUTRAL" */
    val undertone: String,
    val fitzpatrick: Int,
    val redness: Float,
    val dullness: Float,
    val darkCircles: Float
)
