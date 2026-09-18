package com.borasarang.promptfactoryjupjup.ui.provider

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.borasarang.promptfactoryjupjup.databinding.PfFragmentProviderBinding
import com.borasarang.promptfactoryjupjup.util.DebugLogger

class ProviderManageFragment : Fragment() {

    private var _binding: PfFragmentProviderBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = PfFragmentProviderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        DebugLogger.i("공급자", "공급자 관리 화면 진입 — 웹에서 관리(현황만)")
        // 앱은 현황만. 공급자·API키·모델 관리는 웹 포털(http://IP:3002)에서
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
