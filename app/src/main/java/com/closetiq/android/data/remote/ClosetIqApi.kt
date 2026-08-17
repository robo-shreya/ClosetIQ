package com.closetiq.android.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * The app only ever talks to your own backend. It never sees a YouCam URL or credential.
 *
 * Which provider actually serves these calls is decided by the PROVIDER value in
 * backend/.env, not by anything in this app. It was two providers during development — a
 * local Gemma mock alongside real YouCam, same interface, so the whole async pipeline
 * could be proven before a credit was spent — and is one now that the mock has served
 * its purpose and been removed.
 */
interface ClosetIqApi {

    @POST("api/tasks")
    suspend fun createTask(@Body request: CreateTaskRequest): CreateTaskResponse

    @GET("api/tasks/{taskId}")
    suspend fun getTask(@Path("taskId") taskId: String): TaskResponse

    @GET("health")
    suspend fun health(): HealthResponse
}
