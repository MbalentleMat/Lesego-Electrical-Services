package com.lesego.electrical.ui.admin

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import android.widget.ArrayAdapter
import android.widget.EditText
import android.text.InputType
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.lesego.electrical.data.models.Contractor
import com.lesego.electrical.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

@AndroidEntryPoint
class AdminDashboardFragment : Fragment() {

    private lateinit var totalBookings: TextView
    private lateinit var pendingBookings: TextView
    private lateinit var inProgressBookings: TextView
    private lateinit var completedBookings: TextView
    private lateinit var bookingsContainer: LinearLayout
    private lateinit var message: TextView

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private var availableContractors: List<Contractor> = emptyList()
    private var allContractorNames: Map<String, String> = emptyMap()
    private var availabilityByContractor: Map<String, DocumentSnapshot> = emptyMap()
    private var assignedJobsForScheduling: List<DocumentSnapshot> = emptyList()

    private val statuses = listOf(
        "pending", "confirmed", "in_progress", "completed", "cancelled"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_admin_dashboard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        totalBookings = view.findViewById(R.id.tvAdminTotal)
        pendingBookings = view.findViewById(R.id.tvAdminPending)
        inProgressBookings = view.findViewById(R.id.tvAdminInProgress)
        completedBookings = view.findViewById(R.id.tvAdminCompleted)
        bookingsContainer = view.findViewById(R.id.adminBookingsContainer)
        message = view.findViewById(R.id.tvAdminMessage)

        loadDashboard()
    }

    private fun loadDashboard() {
        viewLifecycleOwner.lifecycleScope.launch {
            message.text = "Loading bookings..."
            bookingsContainer.removeAllViews()

            try {
                val uid = auth.currentUser?.uid ?: error("Please sign in first.")
                val userDoc = firestore.collection("users").document(uid).get().await()

                if (userDoc.getString("role") != "admin") {
                    message.text = "Administrator access required."
                    return@launch
                }

                val contractorDocs = firestore.collection("contractors").get().await().documents
                allContractorNames = contractorDocs.associate { doc ->
                    doc.id to (doc.getString("name") ?: doc.id)
                }
                availableContractors = contractorDocs.mapNotNull { doc ->
                    val name = (doc.get("name") as? String)?.trim().orEmpty()
                    val available = (doc.get("isAvailable") as? Boolean) ?: true
                    if (name.isBlank() || !available) null else Contractor(
                        id = doc.id,
                        name = name,
                        specialty = (doc.get("specialty") as? String).orEmpty(),
                        location = (doc.get("location") as? String).orEmpty(),
                        isAvailable = available
                    )
                }.sortedBy { it.name.lowercase() }

                // Read scheduling information separately from contractor profiles.
                // Missing availability is treated as unverified, not available.
                availabilityByContractor = firestore.collection("contractor_availability")
                    .get().await().documents.associateBy { it.id }

                val documents = firestore.collection("bookings").get().await().documents
                totalBookings.text = documents.size.toString()
                pendingBookings.text = documents.count {
                    it.getString("status") == "pending"
                }.toString()
                inProgressBookings.text = documents.count {
                    it.getString("status") == "in_progress"
                }.toString()
                completedBookings.text = documents.count {
                    it.getString("status") == "completed"
                }.toString()

                // Reviews are independent of the booking list.
                // A reviews permission error must not hide booking management.
                try {
                    addReviewsOverview()
                } catch (reviewError: Exception) {
                    Timber.e(reviewError, "Could not load admin reviews")
                    addReviewMessage("Reviews unavailable: ${reviewError.localizedMessage}")
                }

                try {
                    addChangeRequestsOverview()
                } catch (requestError: Exception) {
                    Timber.e(requestError, "Could not load booking change requests")
                    addReviewMessage("Booking change requests unavailable: ${requestError.localizedMessage}")
                }

                if (documents.isEmpty()) {
                    message.text = "No bookings found."
                    return@launch
                }

                message.text = "${documents.size} customer booking(s)"
                val jobDocs = firestore.collection("electrical_jobs").get().await().documents
                assignedJobsForScheduling = jobDocs
                val jobsById = jobDocs.associateBy { it.id }
                documents.forEach { document ->
                    addBookingCard(document, jobsById[document.id])
                }
            } catch (e: Exception) {
                Timber.e(e, "Admin dashboard loading failed")
                message.text = "Could not load bookings: ${e.localizedMessage}"
            }
        }
    }

    private fun addBookingCard(document: DocumentSnapshot, job: DocumentSnapshot?) {
        val bookingView = layoutInflater.inflate(
            R.layout.item_admin_booking, bookingsContainer, false
        )

        bookingView.findViewById<TextView>(R.id.tvAdminBookingService).text =
            document.getString("serviceName") ?: "Electrical service"
        bookingView.findViewById<TextView>(R.id.tvAdminBookingCustomer).text =
            document.getString("customerName") ?: "Unknown customer"
        bookingView.findViewById<TextView>(R.id.tvAdminBookingReference).text =
            "Reference: ${document.getString("reference") ?: document.id}"
        bookingView.findViewById<TextView>(R.id.tvAdminBookingDate).text =
            "${document.getString("preferredDate") ?: ""} at ${document.getString("preferredTime") ?: ""}"

        val currentStatus = document.getString("status") ?: "pending"
        bookingView.findViewById<TextView>(R.id.tvAdminBookingStatus).apply {
            text = "STATUS: " + currentStatus.replace("_", " ").uppercase(Locale.US)
            setTextColor(when (currentStatus) {
                "completed" -> Color.rgb(21, 128, 61)
                "confirmed" -> Color.rgb(29, 78, 216)
                "in_progress" -> Color.rgb(109, 40, 217)
                "cancelled" -> Color.rgb(185, 28, 28)
                else -> Color.rgb(146, 64, 14)
            })
        }

        val spinner = bookingView.findViewById<Spinner>(R.id.spinnerAdminBookingStatus)
        val updateButton = bookingView.findViewById<Button>(R.id.btnUpdateBookingStatus)

        val adapter = readableSpinnerAdapter(
            statuses.map { it.replace("_", " ").replaceFirstChar { char -> char.uppercase() } }
        )
        spinner.adapter = adapter
        spinner.setSelection(statuses.indexOf(currentStatus).coerceAtLeast(0))

        updateButton.setOnClickListener {
            val newStatus = statuses[spinner.selectedItemPosition]
            if (newStatus == currentStatus) {
                Toast.makeText(requireContext(), "Booking already has this status.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            updateBookingStatus(document.id, newStatus, updateButton)
        }

        val assignedText = bookingView.findViewById<TextView>(R.id.tvAssignedContractor)
        val contractorSpinner = bookingView.findViewById<Spinner>(R.id.spinnerAssignContractor)
        val assignButton = bookingView.findViewById<Button>(R.id.btnAssignContractor)

        val currentContractorId = job?.getString("contractorId").orEmpty()
        val currentContractorName = job?.getString("contractorName").orEmpty()
        assignedText.text = if (currentContractorName.isBlank()) {
            "Assigned electrician: None"
        } else {
            "Assigned electrician: $currentContractorName"
        }

        val choices = listOf("Select an electrician") + availableContractors.map {
            val schedule = availabilityByContractor[it.id]
            val indicator = when {
                schedule == null -> "Schedule not set"
                schedule.getBoolean("isAvailable") == false -> "Unavailable"
                else -> "Schedule saved"
            }
            "${it.name} (${it.specialty.ifBlank { "General" }}) — $indicator"
        }
        val contractorAdapter = readableSpinnerAdapter(choices)
        contractorSpinner.adapter = contractorAdapter
        val assignedIndex = availableContractors.indexOfFirst { it.id == currentContractorId }
        contractorSpinner.setSelection(if (assignedIndex >= 0) assignedIndex + 1 else 0)

        assignButton.isEnabled = job != null && availableContractors.isNotEmpty()
        if (job == null) {
            assignedText.text = "Cannot assign: matching electrical job not found"
        } else if (availableContractors.isEmpty()) {
            assignedText.text = "No available electricians in contractor directory"
        }

        assignButton.setOnClickListener {
            val index = contractorSpinner.selectedItemPosition - 1
            if (index !in availableContractors.indices) {
                Toast.makeText(requireContext(), "Please select an electrician", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val contractor = availableContractors[index]
            if (contractor.id == currentContractorId) {
                Toast.makeText(requireContext(), "This electrician is already assigned", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val check = checkAvailability(
                contractor.id,
                document.getString("preferredDate").orEmpty(),
                document.getString("preferredTime").orEmpty(),
                document.id
            )
            if (check != null) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Cannot assign electrician")
                    .setMessage(check)
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }
            assignContractor(document.id, contractor, assignButton)
        }

        addQuoteControls(bookingView, document)
        bookingsContainer.addView(bookingView)
    }

    /** Returns null if the electrician can be assigned at the requested time. */
    private fun checkAvailability(
        contractorId: String,
        dateText: String,
        timeText: String,
        bookingId: String,
        scheduleOverride: DocumentSnapshot? = availabilityByContractor[contractorId],
        jobsOverride: List<DocumentSnapshot> = assignedJobsForScheduling
    ): String? {
        val schedule = scheduleOverride
            ?: return "This electrician has not saved a work schedule yet. Ask them to set their availability."
        if (schedule.getBoolean("isAvailable") != true) {
            return "This electrician is currently marked unavailable."
        }
        val date = try { LocalDate.parse(dateText.trim()) }
        catch (_: Exception) { return "Booking date must be in YYYY-MM-DD format for scheduling checks." }
        val time = parseAppointmentTime(timeText)
            ?: return "Booking time could not be verified. Use a time such as 09:00 or 9:00 AM."
        val days = (schedule.get("workingDays") as? List<*>)
            ?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
        if (date.dayOfWeek.value !in days) {
            return "The electrician does not work on ${date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}."
        }
        val daysOff = (schedule.get("daysOff") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        if (date.toString() in daysOff) return "The electrician has booked this date off."
        val start = parseAppointmentTime(schedule.getString("startTime").orEmpty())
        val end = parseAppointmentTime(schedule.getString("endTime").orEmpty())
        if (start == null || end == null || !end.isAfter(start)) {
            return "The electrician's working hours are missing or invalid."
        }
        if (time.isBefore(start) || !time.isBefore(end)) {
            return "Outside working hours (${schedule.getString("startTime")}–${schedule.getString("endTime")})."
        }
        val conflict = jobsOverride.any { job ->
            job.id != bookingId &&
                    job.getString("contractorId") == contractorId &&
                    job.getString("status") in listOf("pending", "confirmed", "in_progress") &&
                    (job.getString("preferredDate") ?: job.getString("scheduledDate")) == date.toString() &&
                    parseAppointmentTime(job.getString("preferredTime")
                        ?: job.getString("scheduledTime").orEmpty()) == time
        }
        if (conflict) return "This electrician already has an active job at the same date and time."
        return null
    }

    private fun parseAppointmentTime(value: String): LocalTime? {
        val cleaned = value.trim().uppercase(Locale.US).replace(".", "")
        val formats = listOf("H:mm", "HH:mm", "h:mm a", "hh:mm a", "h a", "ha")
        for (format in formats) {
            try {
                return LocalTime.parse(cleaned, DateTimeFormatter.ofPattern(format, Locale.US))
            } catch (_: DateTimeParseException) { }
        }
        return null
    }

    private fun assignContractor(bookingId: String, contractor: Contractor, button: Button) {
        viewLifecycleOwner.lifecycleScope.launch {
            button.isEnabled = false
            button.text = "Assigning..."
            try {
                val uid = auth.currentUser?.uid ?: error("Please sign in first")
                val adminDoc = firestore.collection("users").document(uid).get().await()
                if (adminDoc.getString("role") != "admin") {
                    error("Administrator access required")
                }

                val bookingRef = firestore.collection("bookings").document(bookingId)
                val jobRef = firestore.collection("electrical_jobs").document(bookingId)

                val booking = bookingRef.get().await()
                val job = jobRef.get().await()
                if (!booking.exists()) error("Customer booking not found")
                if (!job.exists()) error("Matching electrical job document not found")

                val customerId = booking.getString("userId")
                    ?: error("Booking has no customer ID")
                if (customerId != job.getString("customerId")) {
                    error("Booking and job customer IDs do not match")
                }

                // Refresh availability and assignments immediately before committing.
                // This is a client-side scheduling guard; concurrent admins may
                // still require a server-enforced slot reservation in future.
                val freshAvailability = firestore.collection("contractor_availability")
                    .document(contractor.id).get().await()
                val freshJobs = firestore.collection("electrical_jobs")
                    .whereEqualTo("contractorId", contractor.id).get().await().documents
                val latestCheck = checkAvailability(
                    contractor.id,
                    booking.getString("preferredDate").orEmpty(),
                    booking.getString("preferredTime").orEmpty(),
                    bookingId,
                    freshAvailability.takeIf { it.exists() },
                    freshJobs
                )
                if (latestCheck != null) error(latestCheck)

                // A stable ID prevents duplicate notifications for the same
                // electrician on the same booking.
                val notificationRef = firestore.collection("notifications")
                    .document("${bookingId}_assigned_${contractor.id}")

                val notification = hashMapOf<String, Any>(
                    "userId" to customerId,
                    "bookingId" to bookingId,
                    "title" to "Electrician Assigned",
                    "message" to "${contractor.name} has been assigned to your electrical service booking.",
                    "type" to "assigned",
                    "isRead" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )

                // Assignment and notification must succeed together.
                val batch = firestore.batch()
                batch.update(jobRef, mapOf(
                    "contractorId" to contractor.id,
                    "contractorName" to contractor.name
                ))
                batch.set(notificationRef, notification)
                batch.commit().await()

                Toast.makeText(
                    requireContext(),
                    "${contractor.name} assigned successfully!",
                    Toast.LENGTH_LONG
                ).show()
                loadDashboard()
            } catch (e: Exception) {
                Timber.e(e, "Contractor assignment failed")
                message.text = "Assignment error: ${e.localizedMessage}"
                Toast.makeText(
                    requireContext(),
                    "Assignment failed: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                button.isEnabled = true
                button.text = "Assign Contractor"
            }
        }
    }

    private fun updateBookingStatus(
        bookingId: String,
        newStatus: String,
        button: Button
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            button.isEnabled = false
            button.text = "Checking..."

            try {
                val bookingRef = firestore.collection("bookings").document(bookingId)
                val jobRef = firestore.collection("electrical_jobs").document(bookingId)

                // Confirm the matching job exists before attempting the atomic update.
                val jobDocument = jobRef.get().await()
                if (!jobDocument.exists()) {
                    message.text = "ERROR: No electrical job found for booking $bookingId"
                    Toast.makeText(requireContext(), "Missing electrical job document", Toast.LENGTH_LONG).show()
                    return@launch
                }

                button.text = "Updating..."
                val batch = firestore.batch()
                batch.update(bookingRef, "status", newStatus)
                batch.update(jobRef, "status", newStatus)
                batch.commit().await()

                Toast.makeText(requireContext(), "Booking status updated successfully!", Toast.LENGTH_LONG).show()
                loadDashboard()
            } catch (e: Exception) {
                Timber.e(e, "Firebase status update failed for booking %s", bookingId)
                message.text = "FIREBASE ERROR: ${e.localizedMessage}"
                Toast.makeText(requireContext(), "Firebase error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                button.isEnabled = true
                button.text = "Update Status"
            }
        }
    }
    /** Pending customer change requests, rendered without additional XML files. */
    private suspend fun addChangeRequestsOverview() {
        val requests = firestore.collection("booking_change_requests")
            .whereEqualTo("status", "pending").get().await().documents
        val panel = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.rgb(39, 48, 63))
        }
        panel.addView(reviewText("BOOKING CHANGE REQUESTS (${requests.size})", 17f, true))
        if (requests.isEmpty()) {
            panel.addView(reviewText("No pending cancellation or rescheduling requests."))
        }
        requests.forEach { request ->
            val type = request.getString("type").orEmpty()
            val bookingId = request.id
            val booking = firestore.collection("bookings").document(bookingId).get().await()
            val customer = booking.getString("customerName") ?: "Customer"
            val service = booking.getString("serviceName") ?: "Electrical service"
            val details = if (type == "reschedule") {
                "Requested: ${request.getString("requestedDate")} at ${request.getString("requestedTime")}"
            } else "Customer requests cancellation"
            panel.addView(reviewText("$customer — $service", 15f, true))
            panel.addView(reviewText("Reference: ${booking.getString("reference") ?: bookingId}"))
            panel.addView(reviewText(details))
            val controls = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val approve = Button(requireContext()).apply { text = "Approve" }
            val reject = Button(requireContext()).apply { text = "Reject" }
            controls.addView(approve, LinearLayout.LayoutParams(0, dp(48), 1f))
            controls.addView(reject, LinearLayout.LayoutParams(0, dp(48), 1f))
            panel.addView(controls)
            approve.setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("Approve ${if (type == "cancel") "cancellation" else "rescheduling"}?")
                    .setMessage("This decision will update the customer's booking. Continue?")
                    .setNegativeButton("Back", null)
                    .setPositiveButton("Approve") { _, _ ->
                        decideChangeRequest(bookingId, true, approve, reject)
                    }.show()
            }
            reject.setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("Reject request?")
                    .setMessage("The customer's original booking will remain unchanged.")
                    .setNegativeButton("Back", null)
                    .setPositiveButton("Reject") { _, _ ->
                        decideChangeRequest(bookingId, false, approve, reject)
                    }.show()
            }
        }
        bookingsContainer.addView(panel)
    }

    private fun decideChangeRequest(
        bookingId: String, approve: Boolean, approveButton: Button, rejectButton: Button
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            approveButton.isEnabled = false
            rejectButton.isEnabled = false
            try {
                val requestRef = firestore.collection("booking_change_requests").document(bookingId)
                val bookingRef = firestore.collection("bookings").document(bookingId)
                val jobRef = firestore.collection("electrical_jobs").document(bookingId)
                val notificationRef = firestore.collection("notifications")
                    .document("${bookingId}_change_decision")
                firestore.runTransaction { transaction ->
                    // All reads must precede writes in a Firestore transaction.
                    val request = transaction.get(requestRef)
                    val booking = transaction.get(bookingRef)
                    val job = transaction.get(jobRef)
                    check(request.exists() && booking.exists() && job.exists()) {
                        "Request, booking or job not found."
                    }
                    check(request.getString("status") == "pending") {
                        "This request has already been decided."
                    }
                    val customerId = request.getString("customerId") ?: error("Missing customer")
                    check(booking.getString("userId") == customerId &&
                            job.getString("customerId") == customerId) { "Customer IDs do not match." }
                    val type = request.getString("type") ?: error("Missing request type")
                    check(type == "cancel" || type == "reschedule") { "Invalid request type" }
                    if (approve) {
                        check(booking.getString("status") in listOf("pending", "confirmed") &&
                                job.getString("status") == booking.getString("status")) {
                            "Booking is no longer eligible. Reject the request instead."
                        }
                        if (type == "cancel") {
                            transaction.update(bookingRef, "status", "cancelled")
                            transaction.update(jobRef, "status", "cancelled")
                        } else {
                            val date = request.getString("requestedDate") ?: error("Missing date")
                            val time = request.getString("requestedTime") ?: error("Missing time")
                            check(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(date) && time.isNotBlank()) {
                                "Invalid proposed appointment"
                            }
                            transaction.update(bookingRef, mapOf("preferredDate" to date, "preferredTime" to time))
                            transaction.update(jobRef, mapOf("preferredDate" to date, "preferredTime" to time))
                        }
                    }
                    val outcome = if (approve) "approved" else "rejected"
                    transaction.update(requestRef, "status", outcome)
                    val action = if (type == "cancel") "cancellation" else "rescheduling"
                    val description = if (approve && type == "reschedule") {
                        "Your rescheduling request was approved. New appointment: ${request.getString("requestedDate")} at ${request.getString("requestedTime")}."
                    } else if (approve) {
                        "Your cancellation request was approved. Your booking is cancelled."
                    } else {
                        "Your $action request was rejected. Your original booking remains unchanged."
                    }
                    transaction.set(notificationRef, mapOf(
                        "userId" to customerId,
                        "bookingId" to bookingId,
                        "title" to "Booking Request ${if (approve) "Approved" else "Rejected"}",
                        "message" to description,
                        "type" to "change_decision",
                        "isRead" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    ))
                    null
                }.await()
                Toast.makeText(requireContext(), "Request ${if (approve) "approved" else "rejected"}.", Toast.LENGTH_LONG).show()
                loadDashboard()
            } catch (e: Exception) {
                Timber.e(e, "Could not decide booking request")
                Toast.makeText(requireContext(), "Decision failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                approveButton.isEnabled = true
                rejectButton.isEnabled = true
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun reviewText(value: String, size: Float = 14f, bold: Boolean = false): TextView =
        TextView(requireContext()).apply {
            text = value
            textSize = size
            setTextColor(Color.WHITE)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(4))
        }

    private fun addReviewMessage(value: String) {
        val panel = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.rgb(45, 35, 62))
        }
        panel.addView(reviewText("ELECTRICIAN RATINGS & REVIEWS", 17f, true))
        panel.addView(reviewText(value))
        bookingsContainer.addView(panel)
    }

    /** Displays reviews from Firestore; no changes to job/booking writes. */
    private suspend fun addReviewsOverview() {
        val reviews = firestore.collection("reviews").get().await().documents
        val panel = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.rgb(45, 35, 62))
        }
        panel.addView(reviewText("ELECTRICIAN RATINGS & REVIEWS", 17f, true))

        if (reviews.isEmpty()) {
            panel.addView(reviewText("No customer reviews yet."))
            bookingsContainer.addView(panel)
            return
        }

        val grouped = reviews.groupBy { it.getString("contractorId").orEmpty() }
        grouped.toSortedMap(compareBy { allContractorNames[it] ?: it }).forEach { (id, docs) ->
            if (id.isBlank()) return@forEach
            val rated = docs.mapNotNull { doc ->
                (doc.get("rating") as? Number)?.toDouble()?.takeIf { it in 1.0..5.0 }
            }
            if (rated.isEmpty()) return@forEach
            val average = rated.average()
            val name = allContractorNames[id]
                ?: docs.firstNotNullOfOrNull { it.getString("contractorName") }
                ?: id
            panel.addView(reviewText("$name  •  ${String.format(java.util.Locale.US, "%.1f", average)} / 5 ★  (${rated.size} reviews)", 15f, true))
            docs.sortedByDescending { it.getTimestamp("createdAt")?.seconds ?: 0L }
                .take(5).forEach { doc ->
                    val score = (doc.get("rating") as? Number)?.toInt() ?: return@forEach
                    val comment = sequenceOf("review", "comment", "reviewText")
                        .mapNotNull { doc.getString(it)?.trim() }
                        .firstOrNull { it.isNotBlank() }
                    panel.addView(reviewText("★ $score/5" +
                            if (comment == null) "" else " — $comment", 13f))
                }
        }
        if (panel.childCount == 1) {
            panel.addView(reviewText("No valid electrician ratings found."))
        }
        bookingsContainer.addView(panel)
    }

    // Quotation is stored separately from booking status and job assignment.
    private fun addQuoteControls(card: View, booking: DocumentSnapshot) {
        // Put quote controls INSIDE the card's vertical content layout.
        // Adding them directly to MaterialCardView caused an enormous overlapping panel.
        val parent = (card as? ViewGroup)?.getChildAt(0) as? LinearLayout ?: return
        val panel = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.rgb(243, 244, 246))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.rgb(209, 213, 219))
            }
        }
        panel.addView(quoteLabel("CUSTOMER QUOTATION", 16f, true))
        val info = quoteLabel("Checking quotation...")
        panel.addView(info)
        val create = Button(requireContext()).apply {
            text = "Create / Update Quote"
            isEnabled = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(109, 40, 217))
        }
        panel.addView(create)
        parent.addView(panel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14) })
        val quoteRef = firestore.collection("quotes").document(booking.id)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val quote = quoteRef.get().await()
                val status = quote.getString("status")
                info.text = if (!quote.exists()) "No quotation created yet." else
                    "Status: $status | Labour: ${money(quote.getLong("labourCents") ?: 0)} | " +
                            "Materials: ${money(quote.getLong("materialsCents") ?: 0)} | " +
                            "Total: ${money(quote.getLong("totalCents") ?: 0)}"
                create.isEnabled = !quote.exists() || status == "draft"
                create.text = when (status) {
                    "accepted" -> "Quote Accepted — Locked"
                    "declined" -> "Quote Declined — Locked"
                    "sent" -> "Awaiting Customer Decision"
                    "draft" -> "Edit Draft Quote"
                    else -> "Create Quote"
                }
            } catch (e: Exception) {
                info.text = "Quotation unavailable: ${e.localizedMessage}"
            }
        }
        create.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val existing = quoteRef.get().await()
                    if (existing.exists() && existing.getString("status") != "draft") {
                        Toast.makeText(requireContext(), "Sent quotations cannot be edited.", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    showQuoteEditor(booking, existing.takeIf { it.exists() })
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Unable to open quote: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun quoteLabel(value: String, size: Float = 14f, bold: Boolean = false): TextView =
        TextView(requireContext()).apply {
            text = value
            textSize = size
            setTextColor(Color.rgb(31, 41, 55))
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(5), 0, dp(5))
        }

    // Consistent high-contrast text and white backgrounds for BOTH spinner views.
    private fun readableSpinnerAdapter(options: List<String>): ArrayAdapter<String> =
        object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, options) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return spinnerLabel(options[position], false)
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return spinnerLabel(options[position], true)
            }
        }

    private fun spinnerLabel(label: String, dropdown: Boolean): TextView =
        TextView(requireContext()).apply {
            text = label
            textSize = if (dropdown) 15f else 14f
            setTextColor(Color.rgb(17, 24, 39))
            setBackgroundColor(Color.WHITE)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(52)
            isSingleLine = !dropdown
            if (!dropdown) ellipsize = android.text.TextUtils.TruncateAt.END
            else maxLines = 2
        }

    private fun money(cents: Long): String =
        String.format(Locale.US, "R %,.2f", cents / 100.0)

    private fun showQuoteEditor(booking: DocumentSnapshot, existing: DocumentSnapshot?) {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        fun field(hintText: String, value: String, decimal: Boolean = false): EditText =
            EditText(requireContext()).apply {
                hint = hintText
                setText(value)
                if (decimal) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                layout.addView(this)
            }
        val description = field("Work description", existing?.getString("description").orEmpty())
        val labour = field("Labour cost (R)", existing?.getLong("labourCents")?.let { String.format(Locale.US, "%.2f", it / 100.0) }.orEmpty(), true)
        val materials = field("Materials cost (R)", existing?.getLong("materialsCents")?.let { String.format(Locale.US, "%.2f", it / 100.0) }.orEmpty(), true)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Quotation for ${booking.getString("serviceName") ?: "booking"}")
            .setView(layout)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Save Draft", null)
            .setPositiveButton("Send to Customer", null)
            .create()
        dialog.setOnShowListener {
            fun submit(status: String, button: Button) {
                val details = description.text.toString().trim()
                val labourCents = parseCents(labour.text.toString())
                val materialsCents = parseCents(materials.text.toString())
                if (details.isBlank() || labourCents == null || materialsCents == null ||
                    labourCents > 100_000_000L || materialsCents > 100_000_000L) {
                    Toast.makeText(requireContext(), "Enter a description and valid non-negative costs.", Toast.LENGTH_LONG).show()
                    return
                }
                button.isEnabled = false
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val uid = auth.currentUser?.uid ?: error("Sign in first")
                        val ref = firestore.collection("quotes").document(booking.id)
                        firestore.runTransaction { tx ->
                            val previous = tx.get(ref)
                            if (previous.exists() && previous.getString("status") != "draft")
                                error("This quotation has already been sent.")
                            val data = hashMapOf<String, Any>(
                                "bookingId" to booking.id,
                                "customerId" to (booking.getString("userId") ?: error("Missing customer ID")),
                                "description" to details,
                                "labourCents" to labourCents,
                                "materialsCents" to materialsCents,
                                "totalCents" to Math.addExact(labourCents, materialsCents),
                                "currency" to "ZAR",
                                "status" to status,
                                "createdBy" to uid,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                            if (!previous.exists()) data["createdAt"] = FieldValue.serverTimestamp()
                            tx.set(ref, data, com.google.firebase.firestore.SetOptions.merge())
                            null
                        }.await()
                        Toast.makeText(requireContext(), if (status == "sent") "Quote sent to customer" else "Draft saved", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        loadDashboard()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Could not save quote: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        button.isEnabled = true
                    }
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { submit("draft", it as Button) }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit("sent", it as Button) }
        }
        dialog.show()
    }

    private fun parseCents(raw: String): Long? = try {
        val amount = java.math.BigDecimal(raw.trim()).setScale(2, java.math.RoundingMode.UNNECESSARY)
        if (amount.signum() < 0) null else amount.movePointRight(2).longValueExact()
    } catch (_: Exception) { null }

}
