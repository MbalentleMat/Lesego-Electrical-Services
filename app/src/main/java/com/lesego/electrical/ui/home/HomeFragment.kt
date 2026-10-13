package com.lesego.electrical.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.lesego.electrical.R
import com.lesego.electrical.databinding.FragmentHomeBinding
import com.lesego.electrical.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint



/**
 * Home / landing screen.
 *
 * Mirrors the website's homepage:
 *  - Hero banner with tagline and CTA "Get a Free Quote"
 *  - 4 service category quick-access cards
 *  - Stats strip (years of experience, clients, projects)
 *  - Emergency call-out banner with tap-to-call
 *  - Testimonials carousel
 *
 * @author Lesego Electrical Team
 */
@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(
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

        setupGreeting()
        setupServiceCards()
        setupEmergencyBanner()
        setupObservers()

        // Find an Electrician button
        binding.btnFindContractors.setOnClickListener {
            findNavController().navigate(
                R.id.contractorsFragment
            )
        }
    }



    private fun setupGreeting() {
        val userName = viewModel.currentUser?.displayName?.split(" ")?.firstOrNull() ?: ""
        binding.tvWelcome.text = if (userName.isNotEmpty()) {
            "Welcome back, $userName!"
        } else {
            getString(R.string.welcome_message)
        }
        binding.tvTagline.text = getString(R.string.hero_subtitle)
    }

    private fun setupServiceCards() {
        // Each card navigates to Services with the correct category pre-selected
        binding.cardResidential.setOnClickListener {
            val action = HomeFragmentDirections.actionHomeToServices("residential")
            findNavController().navigate(action)
        }
        binding.cardCommercial.setOnClickListener {
            val action = HomeFragmentDirections.actionHomeToServices("commercial")
            findNavController().navigate(action)
        }
        binding.cardSolar.setOnClickListener {
            val action = HomeFragmentDirections.actionHomeToServices("solar")
            findNavController().navigate(action)
        }
        binding.cardMaintenance.setOnClickListener {
            val action = HomeFragmentDirections.actionHomeToServices("maintenance")
            findNavController().navigate(action)
        }

        // Hero CTA
        binding.btnGetQuote.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_booking)
        }
    }

    private fun setupEmergencyBanner() {
        // Tap-to-call the emergency line
        binding.btnCallNow.setOnClickListener {
            val phoneUri = Uri.parse("tel:${getString(R.string.emergency_line).replace(" ", "")}")
            startActivity(Intent(Intent.ACTION_DIAL, phoneUri))
        }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
