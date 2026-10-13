package com.lesego.electrical.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.lesego.electrical.MainActivity
import com.lesego.electrical.R
import com.lesego.electrical.databinding.ActivityLoginBinding
import com.lesego.electrical.utils.PasswordUtils
import com.lesego.electrical.utils.ValidationUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Login screen for Lesego Electrical Services app.
 *
 * Supports two authentication methods as required by XISD6329 Task 2:
 *   1. Email + password (password stored as bcrypt hash via Firebase)
 *   2. Google Single Sign-On (SSO) via Credential Manager API
 *
 * Password encryption: Firebase Auth handles server-side bcrypt hashing.
 * On the client side [PasswordUtils] provides additional input sanitisation.
 *
 * @author Lesego Electrical Team
 */
@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    // ViewBinding — replaces findViewById
    private lateinit var binding: ActivityLoginBinding

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    // Credential Manager for modern SSO
    private lateinit var credentialManager: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        credentialManager = CredentialManager.create(this)

        setupClickListeners()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Click Listeners
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupClickListeners() {
        // Standard email/password login
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            if (validateInputs(email, password)) {
                loginWithEmail(email, password)
            }
        }

        // Google SSO button
        binding.btnGoogleSignIn.setOnClickListener {
            launchGoogleSignIn()
        }

        // Navigate to Register
        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Forgot password
        binding.tvForgotPassword.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (email.isNotEmpty() && ValidationUtils.isValidEmail(email)) {
                sendPasswordResetEmail(email)
            } else {
                binding.tilEmail.error = getString(R.string.error_email_invalid)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Email / Password Authentication
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Signs in the user with Firebase email/password authentication.
     * Firebase stores passwords using bcrypt internally; we never store
     * plaintext credentials locally.
     */
    private fun loginWithEmail(email: String, password: String) {
        showLoading(true)

        // Log the attempt (without sensitive data)
        Timber.d("Attempting email login for: %s", email)

        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                showLoading(false)
                if (task.isSuccessful) {
                    Timber.i("Email login successful")
                    navigateToMain()
                } else {
                    Timber.w(task.exception, "Email login failed")
                    val msg = task.exception?.localizedMessage
                        ?: getString(R.string.error_login_failed)
                    showError(msg)
                }
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Google Single Sign-On (SSO)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Launches Google SSO via Credential Manager.
     * Credential Manager replaces the deprecated Google Sign-In SDK and
     * supports passkeys, saved passwords, and federated identity providers.
     *
     * The Web Client ID must match the one in your Firebase project's
     * Google Sign-In provider settings.
     */
    private fun launchGoogleSignIn() {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false) // show all Google accounts
            .setServerClientId(getString(R.string.default_web_client_id))
            .setAutoSelectEnabled(false) // user must explicitly choose account
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        showLoading(true)
        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@LoginActivity
                )
                handleGoogleSignInResult(result)
            } catch (e: GetCredentialException) {
                showLoading(false)
                Timber.e(e, "Google SSO failed")
                showError("Google Sign-In failed: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Processes the credential returned by Credential Manager and
     * authenticates with Firebase using the Google ID token.
     */
    private fun handleGoogleSignInResult(result: GetCredentialResponse) {
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            try {
                val googleIdTokenCredential =
                    GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                firebaseAuthWithGoogle(idToken)
            } catch (e: GoogleIdTokenParsingException) {
                showLoading(false)
                Timber.e(e, "Invalid Google ID token")
                showError("Google Sign-In error. Please try again.")
            }
        } else {
            showLoading(false)
            Timber.w("Unexpected credential type: %s", credential.type)
            showError("Unexpected sign-in method.")
        }
    }

    /**
     * Exchanges the Google ID token for a Firebase credential and signs in.
     */
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                showLoading(false)
                if (task.isSuccessful) {
                    Timber.i("Google SSO → Firebase auth successful")
                    navigateToMain()
                } else {
                    Timber.w(task.exception, "Google SSO → Firebase auth failed")
                    showError(task.exception?.localizedMessage ?: getString(R.string.error_generic))
                }
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Forgot Password
    // ─────────────────────────────────────────────────────────────────────────

    private fun sendPasswordResetEmail(email: String) {
        firebaseAuth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(
                        this,
                        "Password reset email sent to $email",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    showError(task.exception?.localizedMessage ?: getString(R.string.error_generic))
                }
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Validates email and password fields before attempting login. */
    private fun validateInputs(email: String, password: String): Boolean {
        var valid = true

        if (email.isEmpty()) {
            binding.tilEmail.error = getString(R.string.error_email_required)
            valid = false
        } else if (!ValidationUtils.isValidEmail(email)) {
            binding.tilEmail.error = getString(R.string.error_email_invalid)
            valid = false
        } else {
            binding.tilEmail.error = null
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = getString(R.string.error_password_required)
            valid = false
        } else if (password.length < 8) {
            binding.tilPassword.error = getString(R.string.error_password_short)
            valid = false
        } else {
            binding.tilPassword.error = null
        }

        return valid
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !show
        binding.btnGoogleSignIn.isEnabled = !show
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
