package com.lesego.electrical

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.lesego.electrical.auth.LoginActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Splash screen displayed on app launch.
 * - Shows the brand logo and tagline for 2 seconds.
 * - Checks if the user is already authenticated via Firebase.
 *   - If yes → navigates directly to MainActivity (home feed).
 *   - If no  → navigates to LoginActivity.
 *
 * @author Lesego Electrical Team
 */
@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    // Splash duration in milliseconds
    private val SPLASH_DURATION = 2000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Delay then route based on auth state
        Handler(Looper.getMainLooper()).postDelayed({
            navigateNext()
        }, SPLASH_DURATION)
    }

    /**
     * Checks Firebase auth state and routes accordingly.
     * This implements the "login-first" gate described in the WIL spec:
     * all features (services, booking, dashboard, support) are behind auth.
     */
    private fun navigateNext() {
        val currentUser = firebaseAuth.currentUser
        val destination = if (currentUser != null && currentUser.isEmailVerified) {
            // User already signed in — go straight to main screen
            Intent(this, MainActivity::class.java)
        } else {
            // Not signed in — require login
            Intent(this, LoginActivity::class.java)
        }
        startActivity(destination)
        // Disable back navigation to splash
        finish()
    }
}
