package com.cofbro.qian.utils

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.baidu.location.LocationClient
import com.baidu.mapapi.SDKInitializer
import androidx.fragment.app.FragmentActivity

/** Initializes Baidu only after the user has accepted its map/location use. */
object BaiduSdk {
    private const val PREFS = "baidu_map_privacy"
    private const val CONSENT = "accepted"
    private const val AK_META_DATA = "com.baidu.lbsapi.API_KEY"
    private var initialized = false

    fun hasConsent(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(CONSENT, false)

    private fun hasConfiguredKey(context: Context): Boolean {
        val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        return !info.metaData?.getString(AK_META_DATA).isNullOrBlank()
    }

    @Synchronized
    fun initializeIfAllowed(context: Context): Boolean {
        if (initialized) return true
        if (!hasConsent(context) || !hasConfiguredKey(context)) return false
        val appContext = context.applicationContext
        return try {
            SDKInitializer.setAgreePrivacy(appContext, true)
            LocationClient.setAgreePrivacy(true)
            SDKInitializer.initialize(appContext)
            initialized = true
            true
        } catch (error: Exception) {
            Log.e("BaiduSdk", "Baidu SDK initialization failed", error)
            false
        }
    }

    fun ensureConsent(activity: FragmentActivity, onReady: () -> Unit) {
        if (!hasConfiguredKey(activity)) {
            Toast.makeText(activity, "百度地图 AK 尚未配置，地图暂不可用", Toast.LENGTH_LONG).show()
            return
        }
        if (hasConsent(activity)) {
            if (initializeIfAllowed(activity)) onReady()
            else Toast.makeText(activity, "百度地图初始化失败", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("使用地图与定位服务")
            .setMessage("地点搜索、地图选点和当前定位由百度地图 SDK 提供。使用时百度地图会处理位置及设备信息。是否同意启用？")
            .setNegativeButton("暂不使用", null)
            .setPositiveButton("同意并继续") { _, _ ->
                activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(CONSENT, true).apply()
                if (initializeIfAllowed(activity)) onReady()
                else Toast.makeText(activity, "百度地图初始化失败", Toast.LENGTH_LONG).show()
            }
            .show()
    }
}
