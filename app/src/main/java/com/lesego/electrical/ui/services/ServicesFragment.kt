package com.lesego.electrical.ui.services

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.lesego.electrical.R
import com.lesego.electrical.data.models.Service
import com.lesego.electrical.databinding.FragmentServicesBinding
import com.lesego.electrical.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Services catalogue screen.
 *
 * Displays all electrical services in a RecyclerView grouped by category.
 * A chip group at the top allows filtering by category (matching the website's
 * tab navigation: Residential | Commercial | Industrial | Solar | Maintenance).
 *
 * Each card shows the service title, description, and a "Book This Service" button
 * that deep-links to the Booking screen with that service pre-selected.
 *
 * @author Lesego Electrical Team
 */
@AndroidEntryPoint
class ServicesFragment : Fragment() {

    private var _binding: FragmentServicesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    // Safe Args — optional pre-selected category from HomeFragment
    private val args: ServicesFragmentArgs by navArgs()

    private lateinit var adapter: ServicesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentServicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupCategoryChips()
        setupObservers()

        // Apply pre-selected category from navigation args
        if (args.category.isNotEmpty()) {
            selectCategory(args.category)
        }
    }

    private fun setupRecyclerView() {
        adapter = ServicesAdapter { service ->
            // Navigate to Booking with service pre-filled
            val action = ServicesFragmentDirections.actionServicesToBooking(service.id, service.title)
            findNavController().navigate(action)
        }
        binding.rvServices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvServices.adapter = adapter
    }

    private fun setupCategoryChips() {
        val categories = listOf("all", "residential", "commercial", "industrial", "solar", "maintenance")
        val labels = listOf("All", "Residential", "Commercial", "Industrial", "Solar", "Maintenance")

        categories.forEachIndexed { i, cat ->
            val chip = Chip(requireContext()).apply {
                text = labels[i]
                isCheckable = true
                isChecked = cat == "all"
                setChipBackgroundColorResource(R.color.surface_dark)
                setTextColor(resources.getColor(R.color.text_light, null))
                chipStrokeWidth = 1f
                setChipStrokeColorResource(R.color.primary)
                tag = cat
            }
            binding.chipGroupCategories.addView(chip)

            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    val category = if (cat == "all") null else cat
                    filterByCategory(category)
                }
            }
        }
    }

    private fun selectCategory(category: String) {
        for (i in 0 until binding.chipGroupCategories.childCount) {
            val chip = binding.chipGroupCategories.getChildAt(i) as? Chip ?: continue
            if (chip.tag == category) {
                chip.isChecked = true
                break
            }
        }
    }

    private fun filterByCategory(category: String?) {
        val allServices = viewModel.services.value ?: return
        val filtered = if (category == null) allServices
        else allServices.filter { it.category == category }
        adapter.submitList(filtered)

        binding.tvNoServices.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setupObservers() {
        viewModel.services.observe(viewLifecycleOwner) { services ->
            adapter.submitList(services)
            binding.tvNoServices.visibility = if (services.isEmpty()) View.VISIBLE else View.GONE
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
//  RecyclerView Adapter
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Adapter that binds [Service] objects to list item views.
 * Uses [androidx.recyclerview.widget.ListAdapter] with DiffUtil for efficient updates.
 */
class ServicesAdapter(
    private val onBookClick: (Service) -> Unit
) : androidx.recyclerview.widget.ListAdapter<Service, ServicesAdapter.ServiceViewHolder>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<Service>() {
        override fun areItemsTheSame(a: Service, b: Service) = a.id == b.id
        override fun areContentsTheSame(a: Service, b: Service) = a == b
    }
) {
    inner class ServiceViewHolder(
        private val binding: com.lesego.electrical.databinding.ItemServiceBinding
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(service: Service) {
            binding.tvServiceTitle.text = service.title
            binding.tvServiceDesc.text = service.description
            binding.tvServicePrice.text = "From R${service.priceFrom.toInt()}"
            binding.tvCategory.text = service.category.replaceFirstChar { it.uppercase() }
            binding.btnBookService.setOnClickListener { onBookClick(service) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ServiceViewHolder(
        com.lesego.electrical.databinding.ItemServiceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
    )

    override fun onBindViewHolder(holder: ServiceViewHolder, position: Int) =
        holder.bind(getItem(position))
}
