package com.lesego.electrical.ui.support

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.lesego.electrical.R
import com.lesego.electrical.databinding.FragmentSupportBinding
import com.lesego.electrical.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Support & Help Center screen.
 *
 * Mirrors the website's Support page:
 *  - Searchable FAQ accordions grouped by category
 *    (General | Technical | Billing | Appointments)
 *  - Emergency call-out banner
 *  - Live chat, email, and phone contact shortcuts
 *
 * @author Lesego Electrical Team
 */
@AndroidEntryPoint
class SupportFragment : Fragment() {

    private var _binding: FragmentSupportBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    // Hard-coded FAQ data (replace with API/Firestore in production)
    private val faqs = listOf(
        Triple("general", "How do I book a service?", "Use the 'Book' tab in the bottom navigation or visit our Services page and tap 'Book This Service' on any listing."),
        Triple("general", "What areas do you serve?", "We serve all of Gauteng province including Johannesburg, Sandton, Pretoria, Midrand, Soweto, and surrounding areas."),
        Triple("technical", "What should I do in a power emergency?", "Do not attempt to fix electrical faults yourself. Call our 24/7 emergency line: 082 123 4567 immediately."),
        Triple("technical", "Are your electricians certified?", "Yes, all our technicians are registered with the Electrical Contractors Association and hold valid ECB licenses."),
        Triple("billing", "How do I pay for a service?", "We accept EFT, credit/debit cards, and cash. A quote is provided before any work begins."),
        Triple("billing", "Can I get an invoice?", "Yes, invoices are emailed automatically upon job completion and are also available in your booking history."),
        Triple("appointments", "Can I reschedule a booking?", "Yes, contact us at least 24 hours before your appointment via the app or by calling 011 123 4567."),
        Triple("appointments", "How long does a typical job take?", "Job duration varies by service. A standard inspection takes 1–2 hours; full installations may take a full day.")
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentSupportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupFaqs()
        setupContactShortcuts()
        setupSearch()
    }

    private fun setupFaqs() {
        populateFaqGroup(binding.layoutGeneralFaqs, faqs.filter { it.first == "general" })
        populateFaqGroup(binding.layoutTechnicalFaqs, faqs.filter { it.first == "technical" })
        populateFaqGroup(binding.layoutBillingFaqs, faqs.filter { it.first == "billing" })
        populateFaqGroup(binding.layoutAppointmentFaqs, faqs.filter { it.first == "appointments" })
    }

    private fun populateFaqGroup(
        container: android.widget.LinearLayout,
        items: List<Triple<String, String, String>>
    ) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        items.forEach { (_, q, a) ->
            val itemView = inflater.inflate(R.layout.item_faq, container, false)
            val tvQuestion = itemView.findViewById<android.widget.TextView>(R.id.tvQuestion)
            val tvAnswer = itemView.findViewById<android.widget.TextView>(R.id.tvAnswer)
            val tvIcon = itemView.findViewById<android.widget.TextView>(R.id.tvExpandIcon)
            tvQuestion.text = q
            tvAnswer.text = a
            tvAnswer.visibility = View.GONE
            itemView.setOnClickListener {
                val expanded = tvAnswer.visibility == View.VISIBLE
                tvAnswer.visibility = if (expanded) View.GONE else View.VISIBLE
                tvIcon.text = if (expanded) "+" else "−"
            }
            container.addView(itemView)
        }
    }

    private fun setupSearch() {
        binding.etSearchHelp.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                val query = s.toString().lowercase()
                val filtered = if (query.isEmpty()) faqs
                else faqs.filter { it.second.lowercase().contains(query) || it.third.lowercase().contains(query) }
                updateSearchResults(filtered)
            }
        })
    }

    private fun updateSearchResults(results: List<Triple<String, String, String>>) {
        val showAll = results.size == faqs.size
        binding.layoutSearchResults.removeAllViews()
        binding.groupFaqCategories.visibility = if (showAll) View.VISIBLE else View.GONE
        binding.layoutSearchResults.visibility = if (showAll) View.GONE else View.VISIBLE
        if (!showAll) {
            populateFaqGroup(binding.layoutSearchResults, results)
        }
    }

    private fun setupContactShortcuts() {
        binding.btnCallSupport.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:0111234567")))
        }
        binding.btnEmailSupport.setOnClickListener {
            startActivity(Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:support@lethaboelectric.co.za")
                putExtra(Intent.EXTRA_SUBJECT, "Support Request")
            })
        }
        binding.btnEmergencyCall.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:0821234567")))
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
