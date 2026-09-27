package com.cofbro.qian.mapsetting.util

import android.content.Context
import android.widget.Toast

object ToastUtil {
    fun show(context: Context?, info: String?) {
        Toast.makeText(context, info, Toast.LENGTH_LONG).show()
    }

    fun show(context: Context?, info: Int) {
        Toast.makeText(context, info, Toast.LENGTH_LONG).show()
    }

    fun showerror(context: Context?, code: Int) {
        show(context, "地点查询失败：$code")
    }
}
