package com.lesego.electrical.ui.messaging

import android.graphics.Color
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.DateFormat

/** Firestore-backed, per-booking conversation. No XML or navigation changes required. */
object BookingMessageDialog {
    fun show(fragment: Fragment, bookingId: String, serviceName: String) {
        if (!fragment.isAdded) return
        val context = fragment.requireContext()
        val db = FirebaseFirestore.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Toast.makeText(context, "Please sign in first", Toast.LENGTH_SHORT).show()
            return
        }
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }
        val scroll = ScrollView(context)
        val messages = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(messages)
        content.addView(scroll, LinearLayout.LayoutParams(-1, dp(320)))
        val entry = EditText(context).apply {
            hint = "Type a message"
            maxLines = 4
            minLines = 1
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        content.addView(entry)
        val dialog = AlertDialog.Builder(context)
            .setTitle("Messages — $serviceName")
            .setView(content)
            .setNegativeButton("Close", null)
            .setPositiveButton("Send", null)
            .create()
        var listener: ListenerRegistration? = null
        dialog.setOnDismissListener { listener?.remove() }
        dialog.setOnShowListener {
            val send = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            send.isEnabled = false
            val jobRef = db.collection("electrical_jobs").document(bookingId)
            val bookingRef = db.collection("bookings").document(bookingId)
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val job = jobRef.get().await()
                    val booking = bookingRef.get().await()
                    val contractorId = job.getString("contractorId")
                    val customerId = job.getString("customerId")
                    val contractorProfiles = db.collection("contractors")
                        .whereEqualTo("userId", uid).get().await().documents
                    val isCustomer = customerId == uid && booking.getString("userId") == uid
                    val isContractor = contractorProfiles.any { it.id == contractorId }
                    if (!job.exists() || !booking.exists() || contractorId.isNullOrBlank() ||
                        (!isCustomer && !isContractor)) {
                        error("You do not have access to this booking conversation.")
                    }
                    if (!dialog.isShowing) return@launch
                    send.isEnabled = true
                    listener = db.collection("booking_messages").document(bookingId)
                        .collection("messages")
                        .orderBy("createdAt")
                        .limit(100)
                        .addSnapshotListener { snapshot, error ->
                            if (!dialog.isShowing) return@addSnapshotListener
                            messages.removeAllViews()
                            if (error != null) {
                                messages.addView(TextView(context).apply {
                                    text = "Could not load messages: ${error.localizedMessage}"
                                })
                                return@addSnapshotListener
                            }
                            val docs = snapshot?.documents.orEmpty()
                            if (docs.isEmpty()) {
                                messages.addView(TextView(context).apply {
                                    text = "No messages yet. Start the conversation!"
                                    setPadding(0, dp(12), 0, dp(12))
                                })
                            }
                            docs.forEach { item ->
                                val mine = item.getString("senderId") == uid
                                val timestamp = item.getTimestamp("createdAt")?.toDate()
                                val time = timestamp?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(it) }
                                    ?: "Sending..."
                                messages.addView(TextView(context).apply {
                                    text = "${if (mine) "You" else "Other person"} · $time\n${item.getString("text").orEmpty()}"
                                    setTextColor(Color.WHITE)
                                    setBackgroundColor(if (mine) Color.rgb(85, 48, 135) else Color.rgb(48, 57, 70))
                                    setPadding(dp(12), dp(10), dp(12), dp(10))
                                }, LinearLayout.LayoutParams(-1, -2).apply {
                                    setMargins(if (mine) dp(32) else 0, dp(5), if (mine) 0 else dp(32), dp(5))
                                })
                            }
                            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
                        }
                    send.setOnClickListener {
                        val text = entry.text.toString().trim()
                        if (text.isBlank()) { entry.error = "Enter a message"; return@setOnClickListener }
                        if (text.length > 2000) { entry.error = "Maximum 2000 characters"; return@setOnClickListener }
                        send.isEnabled = false
                        fragment.viewLifecycleOwner.lifecycleScope.launch {
                            try {
                                // Verify the current assignment again before writing.
                                val freshJob = jobRef.get().await()
                                val freshBooking = bookingRef.get().await()
                                val freshProfiles = db.collection("contractors")
                                    .whereEqualTo("userId", uid).get().await().documents
                                val allowed = freshJob.getString("customerId") == uid &&
                                        freshBooking.getString("userId") == uid ||
                                        freshProfiles.any { it.id == freshJob.getString("contractorId") }
                                if (!freshJob.exists() || !freshBooking.exists() ||
                                    freshJob.getString("contractorId").isNullOrBlank() || !allowed) {
                                    error("Conversation access has changed")
                                }
                                db.collection("booking_messages").document(bookingId)
                                    .collection("messages").add(mapOf(
                                        "bookingId" to bookingId,
                                        "senderId" to uid,
                                        "text" to text,
                                        "createdAt" to FieldValue.serverTimestamp()
                                    )).await()
                                if (dialog.isShowing) entry.text.clear()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Message not sent: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            } finally {
                                if (dialog.isShowing) send.isEnabled = true
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (dialog.isShowing) {
                        Toast.makeText(context, "Cannot open conversation: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }
}
