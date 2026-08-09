package com.closetiq.android.data.remote

import android.util.Log
import kotlinx.coroutines.delay

/**
 * Create a task, then poll it until it finishes.
 *
 * This is the cut-down version of the state machine in the architecture doc: it lives in
 * a coroutine rather than in WorkManager, so if the app dies mid-render the work is lost.
 * That is an accepted trade for the hackathon — a lost render during a *recording* costs
 * you a retake, nothing more.
 *
 * The full design (durable YouCamTask rows, resume on launch) is the first thing to add
 * back if this ever becomes a real app.
 */
class TaskPoller(
    private val api: ClosetIqApi
) {

    suspend fun run(request: CreateTaskRequest): Result<TaskResult> = runCatching {
        val created = api.createTask(request)
        Log.d(TAG, "task ${created.taskId} created (${request.kind})")

        var waited = 0L
        var interval = INITIAL_INTERVAL_MS

        while (waited < TIMEOUT_MS) {
            delay(interval)
            waited += interval

            val task = api.getTask(created.taskId)

            when (task.status.lowercase()) {
                "success" -> {
                    return@runCatching task.result
                        ?: error("Task ${task.taskId} succeeded with no result body")
                }

                "failed" -> error(task.error ?: "Task ${task.taskId} failed")

                else -> {
                    // Back off: 1s, 2s, 4s, 8s, then hold at 8s.
                    interval = (interval * 2).coerceAtMost(MAX_INTERVAL_MS)
                }
            }
        }

        error("Task ${created.taskId} timed out after ${TIMEOUT_MS / 1000}s")
    }

    private companion object {
        const val TAG = "TaskPoller"
        const val INITIAL_INTERVAL_MS = 1_000L
        const val MAX_INTERVAL_MS = 8_000L
        const val TIMEOUT_MS = 90_000L
    }
}
