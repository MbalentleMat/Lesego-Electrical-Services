package com.lesego.electrical.ui.booking

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Button
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.lesego.electrical.R
import com.lesego.electrical.ui.messaging.BookingMessageDialog
import com.lesego.electrical.data.models.Booking
import com.lesego.electrical.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Locale

@AndroidEntryPoint
class MyBookingsFragment : Fragment() {
    private val viewModel: MainViewModel by activityViewModels()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var bookingsAdapter: BookingsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_my_bookings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvMyBookings)
        val emptyMessage = view.findViewById<TextView>(R.id.tvEmptyBookings)
        bookingsAdapter = BookingsAdapter(
            onRateClick = ::openReviewDialog,
            onCancelClick = ::confirmCancellation,
            onRescheduleClick = ::openRescheduleDialog,
            onQuoteClick = ::openQuoteDialog,
            onMessageClick = ::openBookingMessages
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = bookingsAdapter

        viewModel.myBookings.observe(viewLifecycleOwner) { bookings ->
            bookingsAdapter.updateBookings(bookings)

            emptyMessage.visibility = if (bookings.isEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.syncMyBookings()
    }


    private fun openBookingMessages(booking: Booking) {
        val uid = auth.currentUser?.uid ?: run { toast("Sign in first"); return }
        if (booking.userId != uid) { toast("This is not your booking."); return }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val job = db.collection("electrical_jobs").document(booking.id).get().await()
                if (!job.exists() || job.getString("customerId") != uid ||
                    job.getString("contractorId").isNullOrBlank()) {
                    toast("An electrician must be assigned before messaging.")
                    return@launch
                }
                BookingMessageDialog.show(this@MyBookingsFragment, booking.id, booking.serviceName)
            } catch (e: Exception) { toast("Could not open messages: ${e.localizedMessage}") }
        }
    }

    private fun openQuoteDialog(booking: Booking) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val uid = auth.currentUser?.uid ?: error("Sign in first")
                val quote = db.collection("quotes").document(booking.id).get().await()
                if (!quote.exists() || quote.getString("customerId") != uid || quote.getString("status") == "draft") {
                    toast("No quotation available for this booking yet.")
                    return@launch
                }
                val status = quote.getString("status").orEmpty()
                val summary = "Description: ${quote.getString("description").orEmpty()}\n\n" +
                        "Labour: ${quoteMoney(quote.getLong("labourCents") ?: 0L)}\n" +
                        "Materials: ${quoteMoney(quote.getLong("materialsCents") ?: 0L)}\n" +
                        "Total: ${quoteMoney(quote.getLong("totalCents") ?: 0L)}\n\n" +
                        "Status: ${status.uppercase(Locale.US)}"
                val dialog = AlertDialog.Builder(requireContext())
                    .setTitle("Quotation — ${booking.serviceName}")
                    .setMessage(summary)
                    .setNegativeButton("Close", null)
                if (status == "sent") {
                    dialog.setPositiveButton("Accept") { _, _ -> decideQuote(booking.id, "accepted") }
                    dialog.setNeutralButton("Decline") { _, _ -> decideQuote(booking.id, "declined") }
                }
                dialog.show()
            } catch (e: Exception) {
                toast("Could not load quotation: ${e.localizedMessage}")
            }
        }
    }

    private fun quoteMoney(cents: Long): String =
        String.format(Locale.US, "R %,.2f", cents / 100.0)

    private fun decideQuote(bookingId: String, outcome: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(if (outcome == "accepted") "Accept quotation?" else "Decline quotation?")
            .setMessage("Your decision will be sent to the administrator.")
            .setNegativeButton("Back", null)
            .setPositiveButton("Confirm") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val uid = auth.currentUser?.uid ?: error("Sign in first")
                        val ref = db.collection("quotes").document(bookingId)
                        db.runTransaction { tx ->
                            val quote = tx.get(ref)
                            check(quote.exists() && quote.getString("customerId") == uid) { "Quotation not found" }
                            check(quote.getString("status") == "sent") { "Quotation already decided" }
                            tx.update(ref, mapOf(
                                "status" to outcome,
                                "respondedAt" to FieldValue.serverTimestamp()
                            ))
                            null
                        }.await()
                        toast("Quotation ${outcome} successfully")

                    } catch (e: Exception) {
                        toast("Could not update quotation: ${e.localizedMessage}")
                    }
                }
            }.show()
    }
    private fun confirmCancellation(booking: Booking) {
        AlertDialog.Builder(requireContext())
            .setTitle("Request cancellation")
            .setMessage("Ask the administrator to cancel ${booking.serviceName}? Your booking will remain active until the request is approved.")
            .setNegativeButton("Keep Booking", null)
            .setPositiveButton("Send Request") { _, _ ->
                submitChangeRequest(booking, "cancel", "", "")
            }
            .show()
    }

    private fun openRescheduleDialog(booking: Booking) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val date = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                val now = Calendar.getInstance()
                TimePickerDialog(
                    requireContext(),
                    { _, hour, minute ->
                        val time = String.format(Locale.US, "%02d:%02d", hour, minute)
                        AlertDialog.Builder(requireContext())
                            .setTitle("Confirm rescheduling request")
                            .setMessage("Request $date at $time? Your current appointment stays unchanged until admin approval.")
                            .setNegativeButton("Back", null)
                            .setPositiveButton("Send Request") { _, _ ->
                                submitChangeRequest(booking, "reschedule", date, time)
                            }
                            .show()
                    },
                    now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true
                ).show()
            },
            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis() - 1000L
        }.show()
    }

    private fun submitChangeRequest(booking: Booking, type: String, date: String, time: String) {
        val uid = auth.currentUser?.uid
        if (uid == null || booking.userId != uid) {
            toast("Please sign in to your own account first.")
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val bookingDoc = db.collection("bookings").document(booking.id).get().await()
                val jobDoc = db.collection("electrical_jobs").document(booking.id).get().await()
                if (bookingDoc.getString("userId") != uid ||
                    jobDoc.getString("customerId") != uid ||
                    bookingDoc.getString("status") !in listOf("pending", "confirmed") ||
                    jobDoc.getString("status") !in listOf("pending", "confirmed")
                ) {
                    toast("This booking is no longer eligible for changes.")
                    return@launch
                }
                val requestRef = db.collection("booking_change_requests").document(booking.id)
                if (requestRef.get().await().exists()) {
                    toast("A request already exists for this booking. Please wait for an admin decision.")
                    return@launch
                }
                requestRef.set(mapOf(
                    "bookingId" to booking.id,
                    "customerId" to uid,
                    "type" to type,
                    "requestedDate" to date,
                    "requestedTime" to time,
                    "status" to "pending",
                    "createdAt" to FieldValue.serverTimestamp()
                )).await()
                toast("Request submitted. Your booking remains unchanged until admin approval.")
            } catch (e: Exception) {
                toast("Could not submit request: ${e.localizedMessage}")
            }
        }
    }

    private fun toast(text: String) {
        if (isAdded) Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show()
    }

    private fun openReviewDialog(booking: Booking) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            toast("Please sign in first.")
            return
        }
        if (booking.userId != uid || booking.status.trim().lowercase() != "completed") {
            toast("Only your completed bookings can be reviewed.")
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val job = db.collection("electrical_jobs").document(booking.id).get().await()
                if (!job.exists() || job.getString("customerId") != uid || job.getString("status") != "completed") {
                    throw IllegalStateException("The completed job could not be verified.")
                }
                val contractorId = job.getString("contractorId").orEmpty()
                val contractorName = job.getString("contractorName").orEmpty()
                if (contractorId.isBlank()) throw IllegalStateException("No electrician is assigned to this booking.")
                val reviewRef = db.collection("reviews").document(booking.id)
                if (reviewRef.get().await().exists()) {
                    toast("You have already reviewed this booking.")
                    return@launch
                }
                if (!isAdded) return@launch
                val content = layoutInflater.inflate(R.layout.dialog_rate_electrician, null)
                content.findViewById<TextView>(R.id.tvReviewContractor).text =
                    "Rate ${contractorName.ifBlank { "your electrician" }}"
                val ratingBar = content.findViewById<RatingBar>(R.id.ratingElectrician)
                val reviewInput = content.findViewById<TextInputEditText>(R.id.etElectricianReview)
                val dialog = AlertDialog.Builder(requireContext())
                    .setTitle("Rate Electrician")
                    .setView(content)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Submit", null)
                    .create()
                dialog.setOnShowListener {
                    val submit = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    submit.setOnClickListener {
                        val stars = ratingBar.rating.toInt()
                        if (stars !in 1..5) {
                            toast("Please select 1 to 5 stars.")
                            return@setOnClickListener
                        }
                        val comment = reviewInput.text?.toString()?.trim().orEmpty()
                        submit.isEnabled = false
                        viewLifecycleOwner.lifecycleScope.launch {
                            try {
                                reviewRef.set(mapOf(
                                    "bookingId" to booking.id,
                                    "customerId" to uid,
                                    "contractorId" to contractorId,
                                    "rating" to stars,
                                    "comment" to comment,
                                    "createdAt" to FieldValue.serverTimestamp()
                                )).await()
                                toast("Thank you for your review!")
                                dialog.dismiss()
                            } catch (e: Exception) {
                                toast("Could not submit review: ${e.localizedMessage}")
                                submit.isEnabled = true
                            }
                        }
                    }
                }
                dialog.show()
            } catch (e: Exception) {
                toast("Unable to open review: ${e.localizedMessage}")
            }
        }
    }
}

private class BookingsAdapter(
    private val onRateClick: (Booking) -> Unit,
    private val onCancelClick: (Booking) -> Unit,
    private val onRescheduleClick: (Booking) -> Unit,
    private val onQuoteClick: (Booking) -> Unit,
    private val onMessageClick: (Booking) -> Unit
) : RecyclerView.Adapter<BookingsAdapter.BookingViewHolder>() {
    private var bookings: List<Booking> = emptyList()
    fun currentBookings(): List<Booking> = bookings
    fun updateBookings(newBookings: List<Booking>) {
        bookings = newBookings
        notifyDataSetChanged()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_my_booking, parent, false)
        return BookingViewHolder(view)
    }
    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        val booking = bookings[position]
        val blue = Color.rgb(29, 78, 216)
        val green = Color.rgb(21, 128, 61)
        val purple = Color.rgb(109, 40, 217)
        val red = Color.rgb(185, 28, 28)
        val ink = Color.rgb(28, 35, 45)
        holder.service.text = booking.serviceName
        holder.reference.text = "Reference: ${booking.reference}"
        holder.date.text = "${booking.preferredDate} at ${booking.preferredTime}"
        holder.address.text = booking.serviceAddress
        val status = booking.status.trim().lowercase().replace("_", " ").replace("-", " ")
        holder.status.text = status.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
        val statusColor = when (status) {
            "pending" -> Color.rgb(146, 64, 14)
            "confirmed" -> blue
            "in progress", "inprogress" -> purple
            "completed" -> green
            "cancelled" -> red
            else -> ink
        }
        holder.status.setTextColor(statusColor)
        holder.status.setTypeface(null, Typeface.BOLD)
        val currentStage = when (status) {
            "pending" -> 1
            "confirmed" -> 2
            "in progress", "inprogress" -> 3
            "completed" -> 4
            else -> 0
        }
        val activeColor = Color.parseColor("#8B5CF6")
        val inactiveColor = Color.parseColor("#D1D5DB")
        listOf(holder.progressPending, holder.progressConfirmed, holder.progressInProgress, holder.progressCompleted)
            .forEachIndexed { index, progressView ->
                progressView.setBackgroundColor(if (index < currentStage) activeColor else inactiveColor)
            }
        holder.rateButton.visibility = if (status == "completed") View.VISIBLE else View.GONE
        holder.rateButton.setOnClickListener { onRateClick(booking) }
        val canChange = status in listOf("pending", "confirmed")
        holder.cancelButton.visibility = if (canChange) View.VISIBLE else View.GONE
        holder.rescheduleButton.visibility = if (canChange) View.VISIBLE else View.GONE
        holder.cancelButton.setOnClickListener { onCancelClick(booking) }
        holder.rescheduleButton.setOnClickListener { onRescheduleClick(booking) }
        holder.quoteButton.setOnClickListener { onQuoteClick(booking) }
        holder.messageButton.setOnClickListener { onMessageClick(booking) }
        holder.messageButton.visibility = if (status == "cancelled") View.GONE else View.VISIBLE
    }
    override fun getItemCount(): Int = bookings.size
    class BookingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val service: TextView = view.findViewById(R.id.tvBookingService)
        val reference: TextView = view.findViewById(R.id.tvBookingReference)
        val date: TextView = view.findViewById(R.id.tvBookingDate)
        val address: TextView = view.findViewById(R.id.tvBookingAddress)
        val status: TextView = view.findViewById(R.id.tvBookingStatus)
        val progressPending: View = view.findViewById(R.id.progressPending)
        val progressConfirmed: View = view.findViewById(R.id.progressConfirmed)
        val progressInProgress: View = view.findViewById(R.id.progressInProgress)
        val progressCompleted: View = view.findViewById(R.id.progressCompleted)
        val rateButton: Button = view.findViewById(R.id.btnRateElectrician)
        val cancelButton: Button = view.findViewById(R.id.btnCancelBooking)
        val rescheduleButton: Button = view.findViewById(R.id.btnRescheduleBooking)
        val quoteButton: Button = view.findViewById(R.id.btnViewQuotation)
        val messageButton: Button = view.findViewById(R.id.btnMessageElectrician)
    }
}
