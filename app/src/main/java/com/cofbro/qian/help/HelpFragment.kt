package com.cofbro.qian.help

import android.os.Bundle
import android.view.ViewGroup.MarginLayoutParams
import com.cofbro.hymvvmutils.base.BaseFragment
import com.cofbro.qian.databinding.FragmentHelpBinding
import com.cofbro.qian.utils.CacheUtils
import com.cofbro.qian.utils.Constants
import com.cofbro.qian.utils.dp2px
import com.cofbro.qian.utils.getStatusBarHeight

class HelpFragment : BaseFragment<HelpViewModel, FragmentHelpBinding>() {

    override fun onAllViewCreated(savedInstanceState: Bundle?) {
        initView()
    }

    private fun initView() {
        adjustStatusBarMargin()
        adjustBottomMargin()
    }

    private fun adjustStatusBarMargin() {
        val statusBarHeight = getStatusBarHeight(requireContext())
        val layoutParams = binding?.llHelpHeader?.layoutParams as? MarginLayoutParams
        layoutParams?.topMargin = statusBarHeight + dp2px(requireContext(), 10)
        binding?.llHelpHeader?.layoutParams = layoutParams
    }

    private fun adjustBottomMargin() {
        binding?.helpBottomSpacer?.post {
            val bottomHeight = CacheUtils.cache[Constants.Cache.BOTTOM_BAR_HEIGHT]?.toInt()
                ?: dp2px(requireContext(), 80)
            val layoutParams = binding?.helpBottomSpacer?.layoutParams
            layoutParams?.height = bottomHeight + dp2px(requireContext(), 20)
            binding?.helpBottomSpacer?.layoutParams = layoutParams
        }
    }
}
