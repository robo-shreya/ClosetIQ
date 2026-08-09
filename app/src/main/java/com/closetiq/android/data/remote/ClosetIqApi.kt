package com.closetiq.android.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * The app only ever talks to your own backend. It never sees a YouCam URL or credential.
 *
 * Which provider actually serves these calls — the Gemma mock or real YouCam — is decided
 * by the PROVIDER value in backend/.env. Nothing in the Android app changes when you swap.
 */
interface ClosetIqApi {

    @POST("api/tasks")
    suspend fun createTask(@Body request: CreateTaskRequest): CreateTaskResponse

    @GET("api/tasks/{taskId}")
    suspend fun getTask(@Path("taskId") taskId: String): TaskResponse

    @GET("health")
    suspend fun health(): HealthResponse
}
