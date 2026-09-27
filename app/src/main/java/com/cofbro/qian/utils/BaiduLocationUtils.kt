package com.cofbro.qian.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption

object BaiduLocationUtils {
    fun checkLocationPermission(activity: FragmentActivity): Boolean {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) return true
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            1005
        )
        return false
    }

    /** Returns BD-09 latitude/longitude, matching Baidu MapView and location sign-in. */
    fun getCurrentLocationLatLng(
        context: Context,
        onSuccess: (Double, Double, String) -> Unit = { _, _, _ -> },
        onError: (String) -> Unit = {}
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post {
                getCurrentLocationLatLng(context, onSuccess, onError)
            }
            return
        }
        if (!BaiduSdk.initializeIfAllowed(context)) {
            onError("请先同意使用百度地图与定位服务")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            onError("缺少精确定位权限")
            return
        }
        try {
            val client = LocationClient(context.applicationContext)
            val option = LocationClientOption().apply {
                setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy)
                setCoorType("bd09ll")
                setIsNeedAddress(true)
                setOnceLocation(true)
            }
            client.locOption = option
            client.registerLocationListener(object : BDAbstractLocationListener() {
                override fun onReceiveLocation(location: BDLocation?) {
                    client.stop()
                    Handler(Looper.getMainLooper()).post {
                        if (location == null || location.latitude == 0.0 || location.longitude == 0.0) {
                            onError("百度定位失败：${location?.locType ?: "无结果"}")
                        } else {
                            onSuccess(location.latitude, location.longitude, location.addrStr.orEmpty())
                        }
                    }
                }
            })
            client.start()
        } catch (error: Exception) {
            onError("百度定位启动失败：${error.message.orEmpty()}")
        }
    }

    fun openLocation(context: Context) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
            !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    }

    data class GeoPoint(val latitude: Double, val longitude: Double)
}
