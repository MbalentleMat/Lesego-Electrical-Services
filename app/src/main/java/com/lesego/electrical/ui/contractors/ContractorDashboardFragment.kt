package com.lesego.electrical.ui.contractors

import android.os.Bundle
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.CheckBox
import androidx.appcompat.app.AlertDialog
import java.util.Calendar
import java.util.Locale
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import com.google.android.material.button.MaterialButton
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.lesego.electrical.R
import com.lesego.electrical.ui.messaging.BookingMessageDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

@AndroidEntryPoint
class ContractorDashboardFragment : Fragment() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var message: TextView
    private lateinit var contractorName: TextView
    private lateinit var total: TextView
    private lateinit var inProgress: TextView
    private lateinit var completed: TextView
    private lateinit var jobsContainer: LinearLayout

    private var currentContractorId: String? = null
    private lateinit var reviewsContainer: LinearLayout
    private lateinit var availabilityContainer: LinearLayout
    private var availabilityData = AvailabilityData()

    private data class AvailabilityData(
        val available: Boolean = true,
        val days: List<Int> = listOf(1, 2, 3, 4, 5),
        val start: String = "08:00",
        val end: String = "17:00",
        val daysOff: List<String> = emptyList()
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(
            R.layout.fragment_contractor_dashboard,
            container,
            false
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        message = view.findViewById(
            R.id.tvContractorDashboardMessage
        )
        contractorName = view.findViewById(
            R.id.tvContractorDashboardName
        )
        total = view.findViewById(R.id.tvContractorTotal)
        inProgress = view.findViewById(
            R.id.tvContractorInProgress
        )
        completed = view.findViewById(
            R.id.tvContractorCompleted
        )
        jobsContainer = view.findViewById(
            R.id.contractorJobsContainer
        )

        view.findViewById<View>(
            R.id.btnRefreshContractorJobs
        ).setOnClickListener {
            loadJobs()
        }

        // Add a review section beneath the existing job cards without changing XML IDs.
        reviewsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = panelBackground()
        }
        availabilityContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = panelBackground()
        }
        loadJobs()
    }

    private fun loadJobs() {

        viewLifecycleOwner.lifecycleScope.launch {

            message.text = "Loading your assigned jobs..."
            jobsContainer.removeAllViews()
            reviewsContainer.removeAllViews()
            availabilityContainer.removeAllViews()
            currentContractorId = null

            try {
                val uid = auth.currentUser?.uid
                    ?: error("Please sign in first.")

                val profile = db.collection("users")
                    .document(uid)
                    .get()
                    .await()

                if (profile.getString("role") != "contractor") {
                    message.text =
                        "This dashboard is only available to contractor accounts."
                    return@launch
                }

                val matchingContractors =
                    db.collection("contractors")
                        .whereEqualTo("userId", uid)
                        .get()
                        .await()
                        .documents

                if (matchingContractors.isEmpty()) {
                    message.text =
                        "Your contractor profile is not linked to this account."
                    return@launch
                }

                val contractor = matchingContractors.first()
                val contractorId = contractor.id

                currentContractorId = contractorId

                contractorName.text =
                    "Welcome, ${
                        contractor.get("name") as? String
                            ?: "Contractor"
                    }"

                val jobs = db.collection("electrical_jobs")
                    .whereEqualTo("contractorId", contractorId)
                    .get()
                    .await()
                    .documents

                total.text = jobs.size.toString()

                inProgress.text = jobs.count {
                    it.getString("status") == "in_progress"
                }.toString()

                completed.text = jobs.count {
                    it.getString("status") == "completed"
                }.toString()

                if (jobs.isEmpty()) {
                    message.text =
                        "No jobs have been assigned to you yet."
                } else {
                    message.text = "${jobs.size} assigned job(s)"

                    jobs.forEach { job ->
                        addJobCard(job)
                    }
                }

                jobsContainer.addView(availabilityContainer, 0, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(14) })
                jobsContainer.addView(reviewsContainer, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) })
                loadAvailability(contractorId)
                loadReviews(contractorId)

            } catch (e: Exception) {
                Timber.e(e, "Contractor dashboard failed")

                message.text =
                    "Could not load assigned jobs: ${e.localizedMessage}"
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun panelBackground(): GradientDrawable = GradientDrawable().apply {
        setColor(Color.WHITE)
        cornerRadius = dp(16).toFloat()
        setStroke(dp(1), Color.parseColor("#7C3AED"))
    }

    private fun buttonTint(hex: String): ColorStateList =
        ColorStateList.valueOf(Color.parseColor(hex))

    private val weekDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    private fun loadAvailability(contractorId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            availabilityContainer.removeAllViews()
            availabilityText("MY AVAILABILITY", 18, true)
            availabilityText("Loading schedule...", 14)
            try {
                val doc = db.collection("contractor_availability").document(contractorId).get().await()
                if (currentContractorId != contractorId || !isAdded) return@launch
                availabilityData = if (doc.exists()) {
                    AvailabilityData(
                        available = doc.getBoolean("isAvailable") ?: true,
                        days = (doc.get("workingDays") as? List<*>)?.mapNotNull {
                            (it as? Number)?.toInt()
                        }?.filter { it in 1..7 }?.distinct() ?: listOf(1,2,3,4,5),
                        start = doc.getString("startTime") ?: "08:00",
                        end = doc.getString("endTime") ?: "17:00",
                        daysOff = (doc.get("daysOff") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    )
                } else AvailabilityData()
                renderAvailability(contractorId)
            } catch (e: Exception) {
                Timber.e(e, "Availability load failed")
                availabilityContainer.removeAllViews()
                availabilityText("MY AVAILABILITY", 18, true)
                availabilityText("Could not load schedule: ${e.localizedMessage}", 14)
            }
        }
    }

    private fun availabilityText(value: String, size: Int, bold: Boolean = false) {
        val tv = TextView(requireContext()).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(Color.parseColor("#263244"))
            setPadding(12, 8, 12, 8)
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
        }
        availabilityContainer.addView(tv)
    }

    private fun renderAvailability(contractorId: String) {
        availabilityContainer.removeAllViews()
        availabilityText("MY AVAILABILITY", 18, true)
        availabilityText(if (availabilityData.available) "Status: Available" else "Status: Unavailable", 15)
        availabilityText("Working days: " + availabilityData.days.sorted().joinToString { weekDays[it - 1] }.ifBlank { "None" }, 14)
        availabilityText("Hours: ${availabilityData.start} - ${availabilityData.end}", 14)
        availabilityText("Days off: " + availabilityData.daysOff.sorted().joinToString().ifBlank { "None" }, 14)
        fun action(label: String, color: String, click: () -> Unit) {
            val button = MaterialButton(requireContext()).apply {
                text = label
                isAllCaps = false
                setTextColor(Color.WHITE)
                backgroundTintList = buttonTint(color)
                cornerRadius = dp(12)
                minHeight = dp(48)
                setOnClickListener { click() }
            }
            availabilityContainer.addView(button, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) })
        }
        action(if (availabilityData.available) "Mark Unavailable" else "Mark Available", "#B45309") {
            saveAvailability(contractorId, availabilityData.copy(available = !availabilityData.available))
        }
        action("Set Working Days", "#6D28D9") {
            val selected = BooleanArray(7) { (it + 1) in availabilityData.days }
            AlertDialog.Builder(requireContext())
                .setTitle("Choose working days")
                .setMultiChoiceItems(weekDays.toTypedArray(), selected) { _, index, checked -> selected[index] = checked }
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save") { _, _ ->
                    saveAvailability(contractorId, availabilityData.copy(days = (1..7).filter { selected[it - 1] }))
                }.show()
        }
        action("Set Working Hours", "#1D4ED8") {
            val startParts = availabilityData.start.split(":").mapNotNull { it.toIntOrNull() }
            val endParts = availabilityData.end.split(":").mapNotNull { it.toIntOrNull() }
            TimePickerDialog(requireContext(), { _, sh, sm ->
                val start = String.format(Locale.US, "%02d:%02d", sh, sm)
                TimePickerDialog(requireContext(), { _, eh, em ->
                    val end = String.format(Locale.US, "%02d:%02d", eh, em)
                    if (start >= end) {
                        Toast.makeText(requireContext(), "Finish time must be after start time", Toast.LENGTH_LONG).show()
                    } else saveAvailability(contractorId, availabilityData.copy(start = start, end = end))
                }, endParts.getOrElse(0) { 17 }, endParts.getOrElse(1) { 0 }, true).show()
            }, startParts.getOrElse(0) { 8 }, startParts.getOrElse(1) { 0 }, true).show()
        }
        action("Add Day Off", "#0F766E") {
            val calendar = Calendar.getInstance()
            val picker = DatePickerDialog(requireContext(), { _, year, month, day ->
                val date = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                saveAvailability(contractorId, availabilityData.copy(daysOff = (availabilityData.daysOff + date).distinct().sorted()))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
            picker.datePicker.minDate = calendar.timeInMillis - 60_000L
            picker.show()
        }
        if (availabilityData.daysOff.isNotEmpty()) action("Remove Day Off", "#B91C1C") {
            val dates = availabilityData.daysOff.sorted()
            AlertDialog.Builder(requireContext()).setTitle("Remove a day off")
                .setItems(dates.toTypedArray()) { _, which ->
                    saveAvailability(contractorId, availabilityData.copy(daysOff = availabilityData.daysOff - dates[which]))
                }.setNegativeButton("Cancel", null).show()
        }
    }

    private fun saveAvailability(contractorId: String, updated: AvailabilityData) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val uid = auth.currentUser?.uid ?: error("Please sign in first")
                if (currentContractorId != contractorId) error("Contractor account changed")
                // Only write this contractor's availability; Firestore rules verify ownership.
                db.collection("contractor_availability").document(contractorId).set(
                    mapOf(
                        "contractorId" to contractorId,
                        "userId" to uid,
                        "isAvailable" to updated.available,
                        "workingDays" to updated.days,
                        "startTime" to updated.start,
                        "endTime" to updated.end,
                        "daysOff" to updated.daysOff,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                if (currentContractorId != contractorId || !isAdded) return@launch
                availabilityData = updated
                renderAvailability(contractorId)
                Toast.makeText(requireContext(), "Availability saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Timber.e(e, "Availability save failed")
                if (isAdded) Toast.makeText(requireContext(), "Could not save availability: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadReviews(contractorId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            reviewsContainer.removeAllViews()
            reviewText("CUSTOMER RATINGS & REVIEWS", 18, true)
            reviewText("Loading reviews...", 14)
            try {
                val snapshot = db.collection("reviews")
                    .whereEqualTo("contractorId", contractorId)
                    .get().await()
                if (currentContractorId != contractorId) return@launch
                reviewsContainer.removeAllViews()
                reviewText("CUSTOMER RATINGS & REVIEWS", 18, true)
                val reviews = snapshot.documents
                if (reviews.isEmpty()) {
                    reviewText("No reviews yet.", 14)
                    return@launch
                }
                val ratings = reviews.mapNotNull { it.getLong("rating")?.toInt() }
                    .filter { it in 1..5 }
                if (ratings.isNotEmpty()) {
                    val average = ratings.average()
                    reviewText(String.format(java.util.Locale.US,
                        "★ %.1f / 5  •  %d review(s)", average, ratings.size), 17, true)
                }
                reviews.sortedByDescending { it.getTimestamp("createdAt")?.seconds ?: 0L }
                    .take(10).forEach { doc ->
                        val stars = (doc.getLong("rating") ?: 0L).toInt().coerceIn(0, 5)
                        val comment = doc.getString("comment").orEmpty()
                        reviewText("★".repeat(stars) + "☆".repeat(5 - stars), 19, true)
                        if (comment.isNotBlank()) reviewText(comment, 14)
                    }
            } catch (e: Exception) {
                Timber.e(e, "Could not load contractor reviews")
                reviewsContainer.removeAllViews()
                reviewText("CUSTOMER RATINGS & REVIEWS", 18, true)
                reviewText("Could not load reviews: ${e.localizedMessage}", 14)
            }
        }
    }

    private fun reviewText(value: String, size: Int, bold: Boolean = false) {
        val tv = TextView(requireContext()).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(Color.parseColor("#263244"))
            setPadding(12, 8, 12, 8)
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
        }
        reviewsContainer.addView(tv)
    }

    private fun addJobCard(job: DocumentSnapshot) {

        val card = layoutInflater.inflate(
            R.layout.item_contractor_job,
            jobsContainer,
            false
        )

        fun safe(key: String): String {
            return job.get(key) as? String ?: ""
        }

        val status = safe("status").ifBlank { "pending" }

        card.findViewById<TextView>(
            R.id.tvJobService
        ).text = safe("serviceName").ifBlank {
            "Electrical service"
        }

        card.findViewById<TextView>(R.id.tvJobStatus).apply {
            text = "Status: ${status.replace('_', ' ').replaceFirstChar { it.uppercase() }}"
            setTextColor(Color.parseColor(when (status) {
                "completed" -> "#166534"
                "in_progress" -> "#1D4ED8"
                "cancelled" -> "#B91C1C"
                "confirmed" -> "#6D28D9"
                else -> "#92400E"
            }))
        }

        card.findViewById<TextView>(
            R.id.tvJobAddress
        ).text = "Address: ${
            safe("serviceAddress").ifBlank {
                "Not provided"
            }
        }"

        card.findViewById<TextView>(
            R.id.tvJobSchedule
        ).text = "Appointment: ${
            safe("scheduledDate")
        } ${safe("scheduledTime")}".trim()

        card.findViewById<TextView>(
            R.id.tvJobDescription
        ).text = "Details: ${
            safe("description").ifBlank {
                "No additional details"
            }
        }"

        card.findViewById<TextView>(
            R.id.tvJobReference
        ).text = "Job ID: ${job.id}"

        val startButton = card.findViewById<Button>(
            R.id.btnStartJob
        )

        val completeButton = card.findViewById<Button>(
            R.id.btnCompleteJob
        )

        startButton.visibility = View.GONE
        completeButton.visibility = View.GONE

        when (status) {

            "pending", "confirmed" -> {
                startButton.visibility = View.VISIBLE
            }

            "in_progress" -> {
                completeButton.visibility = View.VISIBLE
            }

            "completed", "cancelled" -> {
                // Finished or cancelled jobs cannot be changed here.
            }
        }

        startButton.setOnClickListener {
            updateJobStatus(
                jobId = job.id,
                newStatus = "in_progress",
                button = startButton
            )
        }

        completeButton.setOnClickListener {
            updateJobStatus(
                jobId = job.id,
                newStatus = "completed",
                button = completeButton
            )
        }

        val messageButton = card.findViewById<MaterialButton>(R.id.btnMessageCustomer)
        messageButton.isEnabled = safe("customerId").isNotBlank() && status != "cancelled"
        messageButton.setOnClickListener {
            BookingMessageDialog.show(this@ContractorDashboardFragment, job.id,
                safe("serviceName").ifBlank { "Electrical service" })
        }
        jobsContainer.addView(card)
    }

    private fun updateJobStatus(
        jobId: String,
        newStatus: String,
        button: Button
    ) {

        viewLifecycleOwner.lifecycleScope.launch {

            button.isEnabled = false
            val originalText = button.text
            button.text = "Updating..."

            try {
                val uid = auth.currentUser?.uid
                    ?: error("Please sign in first.")

                val contractorId = currentContractorId
                    ?: error("Contractor profile not loaded.")

                val jobRef = db.collection("electrical_jobs")
                    .document(jobId)

                val bookingRef = db.collection("bookings")
                    .document(jobId)

                // Recheck the job and booking immediately before
                // updating. The transaction prevents stale writes.
                db.runTransaction { transaction ->

                    val job = transaction.get(jobRef)
                    val booking = transaction.get(bookingRef)

                    if (!job.exists() || !booking.exists()) {
                        error("Job or booking was not found.")
                    }

                    if (job.getString("contractorId") != contractorId) {
                        error("This job is no longer assigned to you.")
                    }

                    if (booking.getString("userId") !=
                        job.getString("customerId")
                    ) {
                        error("Job and booking customer do not match.")
                    }

                    val currentStatus =
                        job.getString("status") ?: "pending"

                    val bookingStatus =
                        booking.getString("status") ?: "pending"

                    if (currentStatus != bookingStatus) {
                        error(
                            "Job and booking statuses do not match. " +
                                    "Please contact the administrator."
                        )
                    }

                    val allowed = when (newStatus) {
                        "in_progress" ->
                            currentStatus == "pending" ||
                                    currentStatus == "confirmed"

                        "completed" ->
                            currentStatus == "in_progress"

                        else -> false
                    }

                    if (!allowed) {
                        error("This status change is not allowed.")
                    }

                    transaction.update(
                        bookingRef,
                        "status",
                        newStatus
                    )

                    if (newStatus == "completed") {
                        transaction.update(
                            jobRef,
                            mapOf(
                                "status" to newStatus,
                                "completedAt" to
                                        FieldValue.serverTimestamp()
                            )
                        )
                    } else {
                        transaction.update(
                            jobRef,
                            "status",
                            newStatus
                        )
                    }

                    // Create a customer notification in the same transaction.
                    // A repeated status transition cannot create a duplicate.
                    val notificationType = if (newStatus == "in_progress") {
                        "started"
                    } else {
                        "completed"
                    }

                    val notificationTitle = if (newStatus == "in_progress") {
                        "Job Started"
                    } else {
                        "Job Completed"
                    }

                    val notificationMessage = if (newStatus == "in_progress") {
                        "Your electrician has started working on your electrical service booking."
                    } else {
                        "Your electrical service has been completed successfully."
                    }

                    val notificationRef = db.collection("notifications")
                        .document("${jobId}_$notificationType")

                    transaction.set(
                        notificationRef,
                        mapOf(
                            "userId" to (booking.getString("userId")
                                ?: error("Customer ID missing.")),
                            "bookingId" to jobId,
                            "title" to notificationTitle,
                            "message" to notificationMessage,
                            "type" to notificationType,
                            "isRead" to false,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )

                    null
                }.await()

                if (auth.currentUser?.uid != uid) {
                    return@launch
                }

                Toast.makeText(
                    requireContext(),
                    if (newStatus == "completed") {
                        "Job completed successfully!"
                    } else {
                        "Job started successfully!"
                    },
                    Toast.LENGTH_LONG
                ).show()

                loadJobs()

            } catch (e: Exception) {

                Timber.e(e, "Contractor job update failed")

                message.text =
                    "Could not update job: ${e.localizedMessage}"

                Toast.makeText(
                    requireContext(),
                    "Update failed: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()

            } finally {
                button.isEnabled = true
                button.text = originalText
            }
        }
    }
}




