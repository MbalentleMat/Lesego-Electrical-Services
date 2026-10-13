
package com.lesego.electrical.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.lesego.electrical.MainActivity
import com.lesego.electrical.R
import com.lesego.electrical.databinding.FragmentProfileBinding
import com.lesego.electrical.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(
            inflater,
            container,
            false
        )
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        setupProfile()
        setupSettings()
        setupObservers()
        setupRoleDashboards()

        binding.btnMyNotifications.setOnClickListener {
            findNavController().navigate(
                R.id.notificationsFragment
            )
        }

        binding.cardMyBookings.setOnClickListener {
            Toast.makeText(
                requireContext(),
                "Opening My Bookings",
                Toast.LENGTH_SHORT
            ).show()

            findNavController().navigate(R.id.myBookingsFragment)
        }
    }

    // ---------------------------------------------------------
    // USER PROFILE
    // ---------------------------------------------------------

    private fun setupProfile() {

        val user = viewModel.currentUser

        binding.tvUserName.text =
            user?.displayName ?: "Guest"

        binding.tvUserEmail.text =
            user?.email ?: ""

        user?.photoUrl?.let { uri ->
            Glide.with(this)
                .load(uri)
                .circleCrop()
                .placeholder(R.drawable.ic_person_placeholder)
                .into(binding.ivProfilePhoto)
        }
    }

    // ---------------------------------------------------------
    // ADMIN AND CONTRACTOR DASHBOARDS
    // ---------------------------------------------------------

    private fun setupRoleDashboards() {

        // Hide both buttons until the role is verified.
        binding.btnAdminDashboard.visibility = View.GONE
        binding.btnContractorDashboard.visibility = View.GONE

        val uid = viewModel.currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->

                if (_binding == null) {
                    return@addOnSuccessListener
                }

                val role =
                    document.getString("role") ?: "customer"

                // Administrator access
                binding.btnAdminDashboard.visibility =
                    if (role == "admin") {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }

                // Contractor access
                binding.btnContractorDashboard.visibility =
                    if (role == "contractor") {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
            }
            .addOnFailureListener {

                if (_binding != null) {

                    binding.btnAdminDashboard.visibility =
                        View.GONE

                    binding.btnContractorDashboard.visibility =
                        View.GONE

                    Toast.makeText(
                        requireContext(),
                        "Unable to verify account role.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        // Existing Admin Dashboard button
        binding.btnAdminDashboard.setOnClickListener {
            findNavController().navigate(
                R.id.adminDashboardFragment
            )
        }

        // New Contractor Dashboard button
        binding.btnContractorDashboard.setOnClickListener {
            findNavController().navigate(
                R.id.contractorDashboardFragment
            )
        }
    }

    // ---------------------------------------------------------
    // NOTIFICATIONS AND ACCOUNT SETTINGS
    // ---------------------------------------------------------

    private fun setupSettings() {

        binding.switchPushNotifications.isChecked =
            loadPref("push_notif", true)

        binding.switchEmailNotifications.isChecked =
            loadPref("email_notif", true)

        binding.switchSmsNotifications.isChecked =
            loadPref("sms_notif", false)

        binding.switchPushNotifications
            .setOnCheckedChangeListener { _, checked ->
                savePref("push_notif", checked)
            }

        binding.switchEmailNotifications
            .setOnCheckedChangeListener { _, checked ->
                savePref("email_notif", checked)
            }

        binding.switchSmsNotifications
            .setOnCheckedChangeListener { _, checked ->
                savePref("sms_notif", checked)
            }

        binding.btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }

        binding.btnChangePassword.setOnClickListener {
            sendPasswordReset()
        }

        binding.btnSignOut.setOnClickListener {
            confirmSignOut()
        }

        binding.btnDeleteAccount.setOnClickListener {
            confirmDeleteAccount()
        }
    }

    // ---------------------------------------------------------
    // EDIT PROFILE
    // ---------------------------------------------------------

    private fun showEditProfileDialog() {

        val dialogView = layoutInflater.inflate(
            R.layout.dialog_edit_profile,
            null
        )

        val nameInput =
            dialogView.findViewById<TextInputEditText>(
                R.id.etDialogName
            )

        val phoneInput =
            dialogView.findViewById<TextInputEditText>(
                R.id.etDialogPhone
            )

        nameInput.setText(
            viewModel.currentUser?.displayName ?: ""
        )

        viewModel.userProfile.value?.phone?.let {
            phoneInput.setText(it)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Profile")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->

                val name =
                    nameInput.text.toString().trim()

                val phone =
                    phoneInput.text.toString().trim()

                if (name.isNotEmpty()) {

                    viewModel.updateProfile(
                        mapOf(
                            "name" to name,
                            "phone" to phone
                        )
                    )

                    val profileUpdates =
                        UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build()

                    viewModel.currentUser
                        ?.updateProfile(profileUpdates)

                    _binding?.tvUserName?.text = name
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------------------------------------------------
    // PASSWORD RESET
    // ---------------------------------------------------------

    private fun sendPasswordReset() {

        val email =
            viewModel.currentUser?.email ?: return

        FirebaseAuth.getInstance()
            .sendPasswordResetEmail(email)
            .addOnSuccessListener {

                if (!isAdded) return@addOnSuccessListener

                Toast.makeText(
                    requireContext(),
                    "Password reset email sent to $email",
                    Toast.LENGTH_LONG
                ).show()
            }
            .addOnFailureListener { error ->

                if (!isAdded) return@addOnFailureListener

                Toast.makeText(
                    requireContext(),
                    "Error: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    // ---------------------------------------------------------
    // SIGN OUT
    // ---------------------------------------------------------

    private fun confirmSignOut() {

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sign Out")
            .setMessage(
                "Are you sure you want to sign out?"
            )
            .setPositiveButton("Sign Out") { _, _ ->

                (requireActivity() as? MainActivity)
                    ?.signOut()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------------------------------------------------
    // DELETE ACCOUNT
    // ---------------------------------------------------------

    private fun confirmDeleteAccount() {

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Account")
            .setMessage(
                "This will permanently delete your account and all your data. This cannot be undone."
            )
            .setPositiveButton("Delete") { _, _ ->

                viewModel.currentUser
                    ?.delete()
                    ?.addOnCompleteListener { task ->

                        if (!isAdded) {
                            return@addOnCompleteListener
                        }

                        if (task.isSuccessful) {

                            (requireActivity() as? MainActivity)
                                ?.signOut()

                        } else {

                            Toast.makeText(
                                requireContext(),
                                "Error: ${task.exception?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------------------------------------------------
    // OBSERVERS
    // ---------------------------------------------------------

    private fun setupObservers() {

        viewModel.myBookings.observe(
            viewLifecycleOwner
        ) { bookings ->

            binding.tvBookingCount.text =
                "${bookings.size} booking(s)"

            val pending = bookings.count {
                it.status == "pending"
            }

            val confirmed = bookings.count {
                it.status == "confirmed"
            }

            binding.tvBookingSummary.text =
                "$pending pending · $confirmed confirmed"
        }

        viewModel.userProfile.observe(
            viewLifecycleOwner
        ) { user ->

            user?.phone?.let {
                binding.tvUserPhone.text = it
            }
        }
    }

    // ---------------------------------------------------------
    // SHARED PREFERENCES
    // ---------------------------------------------------------

    private fun loadPref(
        key: String,
        default: Boolean
    ): Boolean {

        val prefs =
            requireContext().getSharedPreferences(
                "settings",
                0
            )

        return prefs.getBoolean(
            key,
            default
        )
    }

    private fun savePref(
        key: String,
        value: Boolean
    ) {

        requireContext()
            .getSharedPreferences("settings", 0)
            .edit()
            .putBoolean(key, value)
            .apply()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
