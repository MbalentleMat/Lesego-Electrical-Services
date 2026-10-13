package com.lesego.electrical.ui.notifications

import android.os.Bundle
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lesego.electrical.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.text.DateFormat

@AndroidEntryPoint
class NotificationsFragment : Fragment() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var message: TextView
    private lateinit var container: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_notifications, parent, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        message = view.findViewById(R.id.tvNotificationsMessage)
        container = view.findViewById(R.id.notificationsContainer)
        view.findViewById<Button>(R.id.btnRefreshNotifications).setOnClickListener { loadNotifications() }
        loadNotifications()
    }

    private fun loadNotifications() {
        viewLifecycleOwner.lifecycleScope.launch {
            message.text = "Loading notifications..."
            container.removeAllViews()
            try {
                val uid = auth.currentUser?.uid ?: error("Please sign in first.")
                // Sort on the client to avoid requiring a composite Firestore index.
                val notifications = db.collection("notifications")
                    .whereEqualTo("userId", uid)
                    .get().await().documents
                    .sortedByDescending { it.getTimestamp("createdAt")?.seconds ?: 0L }
                message.text = if (notifications.isEmpty()) "No notifications yet." else
                    "${notifications.count { it.getBoolean("isRead") != true }} unread notification(s)"
                notifications.forEach { doc ->
                    val card = layoutInflater.inflate(R.layout.item_customer_notification, container, false)
                    card.findViewById<TextView>(R.id.tvNotificationTitle).text =
                        doc.getString("title") ?: "Booking update"
                    card.findViewById<TextView>(R.id.tvNotificationMessage).text =
                        doc.getString("message") ?: ""
                    card.findViewById<TextView>(R.id.tvNotificationDate).text =
                        doc.getTimestamp("createdAt")?.toDate()?.let {
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(it)
                        } ?: ""
                    val markRead = card.findViewById<Button>(R.id.btnMarkNotificationRead)
                    val isRead = doc.getBoolean("isRead") == true
                    markRead.visibility = if (isRead) View.GONE else View.VISIBLE
                    markRead.setOnClickListener {
                        markRead.isEnabled = false
                        viewLifecycleOwner.lifecycleScope.launch {
                            try {
                                db.collection("notifications").document(doc.id)
                                    .update("isRead", true).await()
                                loadNotifications()
                            } catch (e: Exception) {
                                Timber.e(e, "Mark notification read failed")
                                if (isAdded) Toast.makeText(requireContext(),
                                    "Could not mark as read: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                markRead.isEnabled = true
                            }
                        }
                    }
                    container.addView(card)
                }
            } catch (e: Exception) {
                Timber.e(e, "Loading notifications failed")
                message.text = "Could not load notifications: ${e.localizedMessage}"
            }
        }
    }
}
