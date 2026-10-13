package com.lesego.electrical.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.lesego.electrical.MainActivity
import com.lesego.electrical.R
import com.lesego.electrical.databinding.ActivityRegisterBinding
import com.lesego.electrical.utils.ValidationUtils
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Registration screen.
 *
 * Creates a new user account using Firebase Authentication.
 * Firebase handles password encryption server-side (bcrypt).
 * Additional user profile data (name, phone) is stored in Firestore.
 *
 * @author Lesego Electrical Team
 */
@AndroidEntryPoint
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    @Inject
    lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnRegister.setOnClickListener {
            val name = binding.etFullName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val confirmPass = binding.etConfirmPassword.text.toString()

            if (validateInputs(name, email, phone, password, confirmPass)) {
                registerUser(name, email, phone, password)
            }
        }

        binding.tvLogin.setOnClickListener {
            finish() // go back to login
        }
    }

    /**
     * Registers the user with Firebase Auth then saves extra profile fields
     * to Firestore under /users/{uid}.
     */
    private fun registerUser(
        name: String,
        email: String,
        phone: String,
        password: String
    ) {
        showLoading(true)
        Timber.d("Registering new user: %s", email)

        firebaseAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = firebaseAuth.currentUser!!

                    // Update display name in Firebase Auth profile
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .build()

                    user.updateProfile(profileUpdates).addOnCompleteListener {
                        // Save extra fields to Firestore
                        saveUserToFirestore(user.uid, name, email, phone)
                    }
                } else {
                    showLoading(false)
                    Timber.w(task.exception, "Registration failed")
                    showError(task.exception?.localizedMessage ?: getString(R.string.error_generic))
                }
            }
    }

    /**
     * Stores user profile document in Firestore collection "users".
     * This allows the app to query user details (name, phone) later.
     */
    private fun saveUserToFirestore(uid: String, name: String, email: String, phone: String) {
        val userDoc = hashMapOf(
            "uid" to uid,
            "name" to name,
            "email" to email,
            "phone" to phone,
            "role" to "customer",
            "createdAt" to com.google.firebase.Timestamp.now()
        )

        firestore.collection("users").document(uid)
            .set(userDoc)
            .addOnSuccessListener {
                showLoading(false)
                Timber.i("User profile saved to Firestore: %s", uid)
                navigateToMain()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Timber.e(e, "Failed to save user to Firestore")
                // Auth succeeded even if Firestore fails; still let them in
                navigateToMain()
            }
    }

    private fun validateInputs(
        name: String, email: String, phone: String,
        password: String, confirmPassword: String
    ): Boolean {
        var valid = true

        if (name.isEmpty()) {
            binding.tilFullName.error = getString(R.string.error_name_required)
            valid = false
        } else { binding.tilFullName.error = null }

        if (email.isEmpty() || !ValidationUtils.isValidEmail(email)) {
            binding.tilEmail.error = getString(R.string.error_email_invalid)
            valid = false
        } else { binding.tilEmail.error = null }

        if (password.length < 8) {
            binding.tilPassword.error = getString(R.string.error_password_short)
            valid = false
        } else { binding.tilPassword.error = null }

        if (password != confirmPassword) {
            binding.tilConfirmPassword.error = getString(R.string.error_passwords_no_match)
            valid = false
        } else { binding.tilConfirmPassword.error = null }

        return valid
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !show
    }

    private fun showError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }
}
