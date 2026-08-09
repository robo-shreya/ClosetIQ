package com.closetiq.android.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val service: String? = null,
    /** Which provider the backend is currently running: "gemma" or "youcam". */
    val provider: String? = null
)
