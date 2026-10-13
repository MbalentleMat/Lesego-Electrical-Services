package com.lesego.electrical

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Application entry point.
 * Annotated with @HiltAndroidApp to trigger Hilt's code generation
 * and provide the application-level dependency container.
 *
 * @author Lesego Electrical Team
 * @version 1.0.0
 */
@HiltAndroidApp
class LesesoApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialise Timber for logging (debug builds only)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.i("LesesoApp started — version ${BuildConfig.VERSION_NAME}")
    }
}
