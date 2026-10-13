package com.lesego.electrical

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.lesego.electrical.auth.LoginActivity
import com.lesego.electrical.databinding.ActivityMainBinding
import com.lesego.electrical.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Host activity for all post-login screens.
 *
 * Uses the Jetpack Navigation Component:
 *  - A single [NavHostFragment] swaps fragments via the nav graph.
 *  - A [BottomNavigationView] provides top-level navigation.
 *  - Back stack is managed automatically by the NavController.
 *
 * @author Lesego Electrical Team
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        prefetchData()
    }

    private fun setupNavigation() {
        // Wire up the NavHostFragment
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Top-level destinations (no "up" arrow shown)
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.homeFragment,
                R.id.servicesFragment,
                R.id.bookingFragment,
                R.id.contractorsFragment,
                R.id.profileFragment
            )
        )

        // Sync toolbar and bottom nav with navController
        setSupportActionBar(binding.toolbar)
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.bottomNavigation.setupWithNavController(navController)

        // Hide bottom nav on non-top-level fragments (e.g., booking form detail)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val topLevel = appBarConfiguration.topLevelDestinations
            if (destination.id in topLevel) {
                binding.bottomNavigation.visibility =
                    android.view.View.VISIBLE
            } else {
                binding.bottomNavigation.visibility =
                    android.view.View.GONE
            }
        }
    }

    /** Pre-fetches services and user profile when the main screen opens. */
    private fun prefetchData() {
        viewModel.refreshServices()
        viewModel.loadUserProfile()
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    /** Called from Settings or toolbar — signs the user out. */
    fun signOut() {
        viewModel.signOut()
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }
}
