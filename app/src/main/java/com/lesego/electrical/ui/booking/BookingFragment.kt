package com.lesego.electrical.ui.booking

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import com.lesego.electrical.R
import com.lesego.electrical.data.models.BookingRequest
import com.lesego.electrical.data.models.BookingResponse
import com.lesego.electrical.databinding.FragmentBookingBinding
import com.lesego.electrical.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.*

/**
 * 3-step booking wizard — mirrors the website's booking flow:
 *
 *  Step 1 → Service selection (spinner pre-filled if coming from ServicesFragment)
 *  Step 2 → Customer details + preferred date & time
 *  Step 3 → Confirmation summary + submit button
 *
 * On success, shows a confirmation card with the booking reference number.
 *
 * @author Lesego Electrical Team
 */
@AndroidEntryPoint
class BookingFragment : Fragment() {

    private var _binding: FragmentBookingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    // Safe Args: pre-selected service from Services or Home screen
    private val args: BookingFragmentArgs by navArgs()

    // Wizard state
    private var currentStep = 1
    private val totalSteps = 3

    // User selections
    private var selectedServiceId = ""
    private var selectedServiceName = ""
    private var selectedDate = ""
    private var selectedTime = ""

    // Available services list from ViewModel
    private val serviceOptions = mutableListOf<Pair<String, String>>() // id → name

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()
        setupStep1()
        setupStep2()
        setupNavigationButtons()
        showStep(1)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Step 1: Service Selection
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupStep1() {
        viewModel.services.observe(viewLifecycleOwner) { services ->
            serviceOptions.clear()
            serviceOptions.addAll(services.map { it.id to it.title })

            val names = services.map { it.title }
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                names
            )
            binding.spinnerService.adapter = adapter

            // Pre-select if navigated from a service card
            if (args.serviceId.isNotEmpty()) {
                val idx = services.indexOfFirst { it.id == args.serviceId }
                if (idx >= 0) {
                    binding.spinnerService.setSelection(idx)
                    selectedServiceId = args.serviceId
                    selectedServiceName = args.serviceName
                }
            }
        }

        binding.spinnerService.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>, v: View?, pos: Int, id: Long
                ) {
                    if (pos < serviceOptions.size) {
                        selectedServiceId = serviceOptions[pos].first
                        selectedServiceName = serviceOptions[pos].second
                    }
                }
                override fun onNothingSelected(p: android.widget.AdapterView<*>) {}
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Step 2: Details & Scheduling
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupStep2() {
        // Pre-fill auth user details
        viewModel.currentUser?.let { user ->
            binding.etCustomerName.setText(user.displayName ?: "")
            binding.etCustomerEmail.setText(user.email ?: "")
        }

        // Date picker
        binding.etPreferredDate.setOnClickListener { showDatePicker() }
        binding.tilPreferredDate.setEndIconOnClickListener { showDatePicker() }

        // Time picker
        binding.etPreferredTime.setOnClickListener { showTimePicker() }
        binding.tilPreferredTime.setEndIconOnClickListener { showTimePicker() }
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                selectedDate = "%04d-%02d-%02d".format(year, month + 1, day)
                val display = "%02d/%02d/%04d".format(day, month + 1, year)
                binding.etPreferredDate.setText(display)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).also { it.datePicker.minDate = cal.timeInMillis }.show()
    }

    private fun showTimePicker() {
        val cal = Calendar.getInstance()
        TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                selectedTime = "%02d:%02d".format(hour, minute)
                binding.etPreferredTime.setText(selectedTime)
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            true
        ).show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Step 3: Confirmation
    // ─────────────────────────────────────────────────────────────────────────

    private fun populateConfirmation() {
        binding.tvConfirmService.text = selectedServiceName
        binding.tvConfirmName.text = binding.etCustomerName.text.toString()
        binding.tvConfirmEmail.text = binding.etCustomerEmail.text.toString()
        binding.tvConfirmPhone.text = binding.etCustomerPhone.text.toString()
        binding.tvConfirmAddress.text = binding.etServiceAddress.text.toString()
        binding.tvConfirmDate.text = binding.etPreferredDate.text.toString()
        binding.tvConfirmTime.text = binding.etPreferredTime.text.toString()
    }

    private fun submitBooking() {
        val request = BookingRequest(
            userId = viewModel.currentUser?.uid ?: "",
            serviceId = selectedServiceId,
            customerName = binding.etCustomerName.text.toString().trim(),
            customerEmail = binding.etCustomerEmail.text.toString().trim(),
            customerPhone = binding.etCustomerPhone.text.toString().trim(),
            serviceAddress = binding.etServiceAddress.text.toString().trim(),
            preferredDate = selectedDate,
            preferredTime = selectedTime,
            notes = binding.etNotes.text.toString().trim()
        )
        viewModel.createBooking(request)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Wizard Navigation
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupNavigationButtons() {
        binding.btnNext.setOnClickListener {
            if (validateCurrentStep()) {
                if (currentStep < totalSteps) {
                    if (currentStep == 2) populateConfirmation()
                    showStep(currentStep + 1)
                } else {
                    // Final step: submit
                    submitBooking()
                }
            }
        }

        binding.btnBack.setOnClickListener {
            if (currentStep > 1) showStep(currentStep - 1)
        }
    }

    private fun showStep(step: Int) {
        currentStep = step
        binding.stepIndicator.text = "Step $step of $totalSteps"

        binding.layoutStep1.visibility = if (step == 1) View.VISIBLE else View.GONE
        binding.layoutStep2.visibility = if (step == 2) View.VISIBLE else View.GONE
        binding.layoutStep3.visibility = if (step == 3) View.VISIBLE else View.GONE

        binding.btnBack.visibility = if (step > 1) View.VISIBLE else View.GONE
        binding.btnNext.text = if (step == totalSteps) "Confirm Booking" else "Next"

        // Update progress bar
        binding.progressSteps.progress = ((step.toFloat() / totalSteps) * 100).toInt()
    }

    private fun validateCurrentStep(): Boolean {
        return when (currentStep) {
            1 -> {
                if (selectedServiceId.isEmpty()) {
                    Toast.makeText(requireContext(), "Please select a service", Toast.LENGTH_SHORT).show()
                    false
                } else true
            }
            2 -> {
                var valid = true
                if (binding.etCustomerName.text.isNullOrBlank()) {
                    binding.tilCustomerName.error = "Name is required"
                    valid = false
                } else binding.tilCustomerName.error = null

                if (binding.etCustomerEmail.text.isNullOrBlank()) {
                    binding.tilCustomerEmail.error = "Email is required"
                    valid = false
                } else binding.tilCustomerEmail.error = null

                if (binding.etCustomerPhone.text.isNullOrBlank()) {
                    binding.tilCustomerPhone.error = "Phone is required"
                    valid = false
                } else binding.tilCustomerPhone.error = null

                if (binding.etServiceAddress.text.isNullOrBlank()) {
                    binding.tilServiceAddress.error = "Address is required"
                    valid = false
                } else binding.tilServiceAddress.error = null

                if (selectedDate.isEmpty()) {
                    binding.tilPreferredDate.error = "Please select a date"
                    valid = false
                } else binding.tilPreferredDate.error = null

                if (selectedTime.isEmpty()) {
                    binding.tilPreferredTime.error = "Please select a time"
                    valid = false
                } else binding.tilPreferredTime.error = null

                valid
            }
            else -> true
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Observers
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupObservers() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnNext.isEnabled = !loading
        }

        viewModel.bookingResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            result.onSuccess { response -> showSuccessState(response) }
            result.onFailure { e ->
                Toast.makeText(requireContext(), "Booking failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
            viewModel.clearBookingResult()
        }
    }

    private fun showSuccessState(response: BookingResponse) {
        binding.layoutBookingForm.visibility = View.GONE
        binding.layoutSuccess.visibility = View.VISIBLE
        binding.tvBookingRef.text = response.reference
        binding.tvSuccessService.text = selectedServiceName
        binding.tvSuccessDate.text = "${binding.etPreferredDate.text} at ${binding.etPreferredTime.text}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
