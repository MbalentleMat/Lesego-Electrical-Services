package com.lesego.electrical.ui.jobs

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.lesego.electrical.data.models.Job
import com.lesego.electrical.databinding.FragmentJobsBinding
import com.lesego.electrical.databinding.ItemJobBinding
import com.lesego.electrical.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Careers / Job listings screen.
 * Shows open positions at Lesego Electrical with an expandable detail view
 * and "Apply Now" button per listing.
 *
 * @author Lesego Electrical Team
 */
@AndroidEntryPoint
class JobsFragment : Fragment() {

    private var _binding: FragmentJobsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: JobAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentJobsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = JobAdapter { job ->
            Toast.makeText(requireContext(), "Application form for: ${job.title}", Toast.LENGTH_SHORT).show()
            // Future: navigate to apply form
        }
        binding.rvJobs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvJobs.adapter = adapter
        viewModel.jobs.observe(viewLifecycleOwner) { jobs -> adapter.submitList(jobs) }
        viewModel.isLoading.observe(viewLifecycleOwner) { l ->
            binding.progressBar.visibility = if (l) View.VISIBLE else View.GONE
        }
        viewModel.loadJobs()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class JobAdapter(
    private val onApply: (Job) -> Unit
) : androidx.recyclerview.widget.ListAdapter<Job, JobAdapter.VH>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<Job>() {
        override fun areItemsTheSame(a: Job, b: Job) = a.id == b.id
        override fun areContentsTheSame(a: Job, b: Job) = a == b
    }
) {
    private val expanded = mutableSetOf<String>()

    inner class VH(private val b: ItemJobBinding) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(b.root) {
        fun bind(job: Job) {
            b.tvJobTitle.text = job.title
            b.tvJobType.text = job.type
            b.tvJobLocation.text = job.location
            b.tvJobSalary.text = job.salaryRange
            b.tvJobDesc.text = job.description

            val isExpanded = expanded.contains(job.id)
            b.layoutJobDetails.visibility = if (isExpanded) View.VISIBLE else View.GONE
            b.tvExpandIcon.text = if (isExpanded) "▲" else "▼"

            b.layoutJobHeader.setOnClickListener {
                if (expanded.contains(job.id)) expanded.remove(job.id) else expanded.add(job.id)
                notifyItemChanged(adapterPosition)
            }
            b.btnApply.setOnClickListener { onApply(job) }
        }
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, vt: Int) = VH(
        ItemJobBinding.inflate(android.view.LayoutInflater.from(parent.context), parent, false)
    )
    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))
}
