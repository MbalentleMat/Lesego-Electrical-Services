package com.lesego.electrical.ui.contractors

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.lesego.electrical.data.models.Contractor
import com.lesego.electrical.databinding.FragmentContractorsBinding
import com.lesego.electrical.databinding.ItemContractorBinding
import com.lesego.electrical.viewmodel.MainViewModel
import com.bumptech.glide.Glide
import dagger.hilt.android.AndroidEntryPoint

/**
 * Contractor directory screen.
 *
 * Matches the website's "Find a Licensed Contractor" page:
 *  - SearchView for name / license search
 *  - Spinner filters for Area and Specialty
 *  - RecyclerView of contractor cards with rating, experience, license
 *  - "Hire Now" and "View Profile" actions per card
 *
 * @author Lesego Electrical Team
 */
@AndroidEntryPoint
class ContractorsFragment : Fragment() {

    private var _binding: FragmentContractorsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: ContractorAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContractorsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearch()
        setupFilters()
        setupObservers()
        viewModel.loadContractors() // load initial list
    }

    private fun setupRecyclerView() {
        adapter = ContractorAdapter(
            onHire = { contractor ->
                Toast.makeText(
                    requireContext(),
                    "Sending hire request to ${contractor.name}…",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onViewProfile = { contractor ->
                // Future: navigate to ContractorDetailFragment
                Toast.makeText(requireContext(), "Profile: ${contractor.name}", Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvContractors.layoutManager = LinearLayoutManager(requireContext())
        binding.rvContractors.adapter = adapter
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                applyFilters(search = query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrEmpty()) applyFilters()
                return true
            }
        })
    }

    private fun setupFilters() {
        val areas = arrayOf("All Areas", "Johannesburg", "Sandton", "Pretoria", "Midrand", "Soweto", "Roodepoort")
        val specialties = arrayOf("All Specialties", "Residential", "Commercial", "Industrial", "Solar", "Maintenance")

        binding.spinnerArea.adapter = android.widget.ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, areas
        )
        binding.spinnerSpecialty.adapter = android.widget.ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, specialties
        )

        val listener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>, v: View?, pos: Int, id: Long) {
                applyFilters()
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>) {}
        }
        binding.spinnerArea.onItemSelectedListener = listener
        binding.spinnerSpecialty.onItemSelectedListener = listener

        binding.btnSearch.setOnClickListener { applyFilters() }
    }

    private fun applyFilters(search: String? = null) {
        val area = binding.spinnerArea.selectedItem.toString().let {
            if (it == "All Areas") null else it
        }
        val specialty = binding.spinnerSpecialty.selectedItem.toString().let {
            if (it == "All Specialties") null else it
        }
        val query = search ?: binding.searchView.query.toString().trim().ifEmpty { null }
        viewModel.loadContractors(area, specialty, query)
    }

    private fun setupObservers() {
        viewModel.contractors.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.tvNoContractors.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
            msg?.let { Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show() }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Adapter
// ─────────────────────────────────────────────────────────────────────────────

class ContractorAdapter(
    private val onHire: (Contractor) -> Unit,
    private val onViewProfile: (Contractor) -> Unit
) : androidx.recyclerview.widget.ListAdapter<Contractor, ContractorAdapter.VH>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<Contractor>() {
        override fun areItemsTheSame(a: Contractor, b: Contractor) = a.id == b.id
        override fun areContentsTheSame(a: Contractor, b: Contractor) = a == b
    }
) {
    inner class VH(private val b: ItemContractorBinding) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(b.root) {

        fun bind(c: Contractor) {
            b.tvContractorName.text = c.name
            b.tvSpecialty.text = c.specialty
            b.tvLocation.text = c.location
            b.tvExperience.text = "${c.yearsExperience} years experience"
            b.tvLicense.text = "License: ${c.licenseNumber}"
            b.tvRating.text = "%.1f".format(c.rating)
            b.tvReviews.text = "(${c.reviewCount} reviews)"
            b.ratingBar.rating = c.rating

            if (c.profileImageUrl.isNotEmpty()) {
                Glide.with(b.root).load(c.profileImageUrl)
                    .placeholder(com.lesego.electrical.R.drawable.ic_person_placeholder)
                    .into(b.ivContractorPhoto)
            }

            b.btnHireNow.setOnClickListener { onHire(c) }
            b.btnViewProfile.setOnClickListener { onViewProfile(c) }
        }
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int) = VH(
        ItemContractorBinding.inflate(android.view.LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}
