package com.borasarang.promptfactoryjupjup.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.borasarang.promptfactoryjupjup.databinding.PfFragmentSettingsBinding
import com.borasarang.promptfactoryjupjup.util.DebugLogger

class SettingsFragment : Fragment() {

    private var _binding: PfFragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = PfFragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        DebugLogger.i("설정", "설정 화면 진입")
        // TODO: PF7에서 웹 페이지와 연동하거나 설정 UI 구현
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
