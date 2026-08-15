package com.closetiq.android

import android.content.Context
import com.closetiq.android.data.image.ColorExtractor
import com.closetiq.android.data.image.ImageStore
import com.closetiq.android.data.local.ClosetDatabase
import com.closetiq.android.data.local.ProfileStore
import com.closetiq.android.data.remote.NetworkModule
import com.closetiq.android.data.remote.TaskPoller
import com.closetiq.android.data.repository.HeroRenderStrategy
import com.closetiq.android.data.repository.ProfileRepositoryImpl
import com.closetiq.android.data.repository.RenderStrategy
import com.closetiq.android.data.repository.SkinRepositoryImpl
import com.closetiq.android.data.repository.TryOnRepositoryImpl
import com.closetiq.android.data.repository.WardrobeRepositoryImpl
import com.closetiq.android.domain.engine.ScoringWeights
import com.closetiq.android.domain.repository.ProfileRepository
import com.closetiq.android.domain.repository.SkinRepository
import com.closetiq.android.domain.repository.TryOnRepository
import com.closetiq.android.domain.repository.WardrobeRepository
import com.closetiq.android.domain.usecase.CheckDuplicateUseCase
import com.closetiq.android.domain.usecase.GetTodaysPickUseCase
import com.closetiq.android.domain.usecase.LogWearUseCase
import com.closetiq.android.domain.usecase.RankDormantUseCase
import com.closetiq.android.domain.usecase.ScoreGarmentUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

/**
 * Dependency injection, by hand.
 *
 * Hilt was cut from the 8-day scope. For a graph this small it costs an annotation
 * processor, a plugin, and a class of build errors that are genuinely hard to read when
 * you are new — in exchange for saving about thirty lines. This object is those thirty
 * lines, and everything in it is a plain constructor call you can follow.
 *
 * Everything is `by lazy`, so nothing is built until something asks for it.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Outlives any single screen — used for background work like colour extraction. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ---- infrastructure ----

    val database: ClosetDatabase by lazy { ClosetDatabase.build(appContext) }

    val imageStore: ImageStore by lazy { ImageStore(appContext) }

    /** Public because the buy check extracts a colour without ever saving a garment. */
    val colorExtractor: ColorExtractor by lazy { ColorExtractor(imageStore) }

    private val api by lazy { NetworkModule.buildApi() }

    private val taskPoller: TaskPoller by lazy { TaskPoller(api) }

    // ---- repositories ----

    val wardrobeRepository: WardrobeRepository by lazy {
        WardrobeRepositoryImpl(
            db = database,
            imageStore = imageStore,
            colorExtractor = colorExtractor,
            scope = appScope
        )
    }

    val profileRepository: ProfileRepository by lazy {
        ProfileRepositoryImpl(ProfileStore(appContext))
    }

    val skinRepository: SkinRepository by lazy {
        SkinRepositoryImpl(
            db = database,
            imageStore = imageStore,
            poller = taskPoller
        )
    }

    val tryOnRepository: TryOnRepository by lazy {
        TryOnRepositoryImpl(imageStore = imageStore, poller = taskPoller)
    }

    /**
     * Swap this to ChainedRenderStrategy once the playground test says chaining survives.
     * It is the only line that has to change.
     */
    val renderStrategy: RenderStrategy by lazy {
        HeroRenderStrategy(imageStore = imageStore, poller = taskPoller)
    }

    // ---- use cases ----

    val scoringWeights = ScoringWeights.Default

    val rankDormant: RankDormantUseCase by lazy { RankDormantUseCase() }

    val scoreGarment: ScoreGarmentUseCase by lazy {
        ScoreGarmentUseCase(scoringWeights, rankDormant)
    }

    val getTodaysPick: GetTodaysPickUseCase by lazy {
        GetTodaysPickUseCase(scoreGarment = scoreGarment, rankDormant = rankDormant)
    }

    val logWear: LogWearUseCase by lazy { LogWearUseCase(wardrobeRepository) }

    val checkDuplicate: CheckDuplicateUseCase by lazy { CheckDuplicateUseCase(rankDormant) }
}
