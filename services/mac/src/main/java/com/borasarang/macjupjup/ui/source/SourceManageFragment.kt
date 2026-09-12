package com.borasarang.macjupjup.ui.source

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.borasarang.macjupjup.data.repository.SourceListItem
import com.borasarang.macjupjup.databinding.MacFragmentSourceManageBinding
import com.borasarang.macjupjup.databinding.MacItemSourceBinding
import com.borasarang.macjupjup.util.DebugLogger
import kotlinx.coroutines.launch

class SourceManageFragment : Fragment() {

    private var _binding: MacFragmentSourceManageBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SourceManageViewModel by viewModels()
    private lateinit var adapter: SourceAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = MacFragmentSourceManageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        DebugLogger.i("소스관리", "수집 소스 화면 진입")

        adapter = SourceAdapter(
            onToggle = { id, enabled -> viewModel.toggleSource(id, enabled) },
            onRunNow = { id -> viewModel.runNow(id) },
            onInterval = { id, minutes -> viewModel.setInterval(id, minutes) },
            statusColor = { viewModel.statusColor(it) },
        )
        binding.macSourceRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.macSourceRecycler.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sources.collect { adapter.submitList(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class SourceAdapter(
        private val onToggle: (String, Boolean) -> Unit,
        private val onRunNow: (String) -> Unit,
        private val onInterval: (String, Int) -> Unit,
        private val statusColor: (String) -> Int,
    ) : ListAdapter<SourceListItem, SourceAdapter.ViewHolder>(DIFF) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = MacItemSourceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class ViewHolder(private val binding: MacItemSourceBinding) :
            RecyclerView.ViewHolder(binding.root) {

            private var boundId: String? = null

            fun bind(item: SourceListItem) {
                val status = item.status
                boundId = status.id
                binding.macSourceName.text = status.name
                binding.macSourceMeta.text = status.describe()
                binding.macSourceStats.text = item.latestLog?.describeStats() ?: "아직 수집 기록 없음"
                binding.macSourceStatus.text = status.lastStatus
                binding.macSourceStatus.setTextColor(statusColor(status.lastStatus))
                binding.macSourceToggle.setOnCheckedChangeListener(null)
                binding.macSourceToggle.isChecked = status.enabled
                binding.macSourceToggle.setOnCheckedChangeListener { _, checked ->
                    onToggle(status.id, checked)
                }
                binding.macBtnRunNow.isEnabled = status.enabled
                binding.macBtnRunNow.setOnClickListener { onRunNow(status.id) }

                val options = com.borasarang.macjupjup.util.TimeUtils.INTERVAL_OPTIONS_MINUTES
                if (binding.macSourceInterval.adapter == null) {
                    binding.macSourceInterval.adapter = android.widget.ArrayAdapter(
                        binding.root.context,
                        android.R.layout.simple_spinner_dropdown_item,
                        options.map { com.borasarang.macjupjup.util.TimeUtils.formatInterval(it) },
                    )
                }
                binding.macSourceInterval.onItemSelectedListener = null
                val position = options.indexOf(status.intervalMinutes).coerceAtLeast(0)
                binding.macSourceInterval.setSelection(position)
                binding.macSourceInterval.onItemSelectedListener =
                    object : android.widget.AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(
                            parent: android.widget.AdapterView<*>?,
                            view: android.view.View?,
                            pos: Int,
                            id: Long,
                        ) {
                            val current = boundId ?: return
                            val minutes = options[pos]
                            if (minutes != status.intervalMinutes) {
                                onInterval(current, minutes)
                            }
                        }

                        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                    }
                binding.macSourceInterval.isEnabled = status.enabled
            }
        }

        companion object {
            private val DIFF = object : DiffUtil.ItemCallback<SourceListItem>() {
                override fun areItemsTheSame(old: SourceListItem, new: SourceListItem) =
                    old.status.id == new.status.id
                override fun areContentsTheSame(old: SourceListItem, new: SourceListItem) = old == new
            }
        }
    }
}
