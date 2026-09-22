package com.borasarang.promptjournaljupjup.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.borasarang.promptjournaljupjup.R
import com.borasarang.promptjournaljupjup.databinding.PjFragmentSettingsBinding
import com.borasarang.promptjournaljupjup.ui.settings.SettingsViewModel
import com.borasarang.promptjournaljupjup.util.DebugLogger
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: PjFragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = PjFragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        DebugLogger.i("설정", "설정 화면 진입")

        binding.pjSwitchAutoStart.setOnCheckedChangeListener { _, checked ->
            if (checked != viewModel.settings.value.autoStart) {
                viewModel.saveAutoStart(checked)
            }
        }
        binding.pjBtnBatteryRequest.setOnClickListener { requestBatteryExemption() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.settings.collect {
                        if (binding.pjSwitchAutoStart.isChecked != it.autoStart) {
                            binding.pjSwitchAutoStart.isChecked = it.autoStart
                        }
                    }
                }
            }
        }
        viewModel.refresh()
    }

    override fun onResume() {
        super.onResume()
        updateBatteryStatus()
        viewModel.refresh()
    }

    private fun updateBatteryStatus() {
        val unrestricted = viewModel.isIgnoringBatteryOptimizations()
        binding.pjBatteryStatus.text =
            if (unrestricted) "배터리 최적화 예외: 허용됨 (백그라운드 안정 동작)"
            else "배터리 최적화 예외: 미허용"
        binding.pjBatteryStatus.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (unrestricted) R.color.pj_status_success else R.color.pj_status_error,
            ),
        )
        binding.pjBtnBatteryRequest.visibility = if (unrestricted) View.GONE else View.VISIBLE
        binding.pjBatteryDesc.visibility = if (unrestricted) View.GONE else View.VISIBLE
    }

    private fun requestBatteryExemption() {
        DebugLogger.i("설정", "배터리 예외 요청 인텐트 실행")
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${requireContext().packageName}"),
                ),
            )
        } catch (e: Exception) {
            DebugLogger.e("설정", "E-AND-REPORT-0805", "배터리 예외 요청 실패: ${e.message}", e)
            Toast.makeText(requireContext(), "설정 화면을 열 수 없습니다", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
