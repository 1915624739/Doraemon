package com.cofbro.qian.utils

import com.cofbro.qian.mapsetting.model.PlaceSuggestion

object TipUtils {

    /*
    为适配部分机型不适配getParcelableExtra，API33可以使用，使用将Tip转换为ArrayString
    {
      "name":,
      "address",
      "poiID"
      "latitude"
      "longitude"
    }
     */
    fun TipParseToArray(tip: PlaceSuggestion): ArrayList<String> {
        val tipArray:ArrayList<String> = ArrayList()
        tipArray.add(tip.name) //0
        tipArray.add(tip.address) //1
        tipArray.add(tip.poiId) //2
        tipArray.add(tip.latitude?.toString() ?: "") //3
        tipArray.add(tip.longitude?.toString() ?: "") //4
        tipArray.add(tip.city) //5
        return  tipArray
    }
}
