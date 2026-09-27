package com.cofbro.qian.mapsetting.viewmodel

import com.cofbro.hymvvmutils.base.BaseViewModel
import com.cofbro.qian.mapsetting.adapter.InputTipsAdapter
import com.cofbro.qian.mapsetting.model.PlaceSuggestion
import com.cofbro.qian.mapsetting.repository.InputTipRepository

class InputTipViewModel :BaseViewModel<InputTipRepository>(){
     var mCurrentTipList: MutableList<PlaceSuggestion>? = null
     var mIntipAdapter: InputTipsAdapter? = null
}
