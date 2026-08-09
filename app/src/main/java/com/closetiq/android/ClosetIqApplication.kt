package com.closetiq.android

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ClosetIqApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Populate the demo closet on first launch. Without it there is no wear history,
        // and with no wear history there is nothing to demonstrate.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            container.wardrobeRepository.seedIfEmpty()
        }
    }
}
