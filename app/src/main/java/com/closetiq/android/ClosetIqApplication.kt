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

        // The closet starts empty and holds only what the user photographs. This clears
        // out the demo garments earlier versions seeded, so an existing install ends up in
        // the same place as a fresh one.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            container.wardrobeRepository.removeSeededGarments()
        }
    }
}
