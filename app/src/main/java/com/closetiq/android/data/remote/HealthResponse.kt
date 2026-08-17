package com.closetiq.android.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val service: String? = null,
    /** Which backend provider is live. Currently always "youcam". */
    val provider: String? = null
)
