package com.lesego.electrical.ui.contact

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.lesego.electrical.databinding.FragmentContactBinding
import com.lesego.electrical.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Contact Us screen.
 *
 * Matches website contact page:
 *  - Office details (address, phone, email, hours)
 *  - Message form with service selector
 *  - Tap-to-call / tap-to-email shortcuts
 *
 * @author Lesego Electrical Team
 */
@AndroidEntryPoint
class ContactFragment : Fragment() {

    private var _binding: FragmentContactBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentContactBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Pre-fill name and email from auth
        viewModel.currentUser?.let { user ->
            binding.etContactName.setText(user.displayName ?: "")
            binding.etContactEmail.setText(user.email ?: "")
        }

        setupClickListeners()
        setupObservers()
    }

    private fun setupClickListeners() {
        binding.tvPhone1.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:0111234567")))
        }
        binding.tvPhone2.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:0821234567")))
        }
        binding.tvEmail.setOnClickListener {
            startActivity(Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:info@lethaboelectric.co.za")
            })
        }
        binding.tvAddress.setOnClickListener {
            val uri = Uri.parse("geo:0,0?q=123+Main+Street,+Johannesburg")
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }

        binding.btnSendMessage.setOnClickListener { sendMessage() }
    }

    private fun sendMessage() {
        val name = binding.etContactName.text.toString().trim()
        val email = binding.etContactEmail.text.toString().trim()
        val phone = binding.etContactPhone.text.toString().trim()
        val service = binding.spinnerService.selectedItem.toString()
        val message = binding.etMessage.text.toString().trim()

        if (name.isEmpty() || email.isEmpty() || message.isEmpty()) {
            Toast.makeText(requireContext(), "Please fill in all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.sendContactMessage(name, email, phone, service, message)
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.btnSendMessage.isEnabled = !loading
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }
        viewModel.contactSent.observe(viewLifecycleOwner) { sent ->
            if (sent) {
                Toast.makeText(requireContext(), "Message sent! We'll be in touch shortly.", Toast.LENGTH_LONG).show()
                binding.etMessage.text?.clear()
                viewModel.clearContactSent()
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
