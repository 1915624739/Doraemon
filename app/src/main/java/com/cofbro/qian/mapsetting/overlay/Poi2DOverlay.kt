package com.cofbro.qian.mapsetting.overlay

import android.content.Context
import android.view.View
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.Marker
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.model.LatLngBounds
import com.baidu.mapapi.search.core.PoiInfo
import com.cofbro.qian.R

/** Displays Baidu POI search results and keeps the marker-to-POI mapping. */
class Poi2DOverlay(
    private val context: Context,
    private val map: BaiduMap,
    private val pois: List<PoiInfo>
) {
    private val markers = LinkedHashMap<Marker, PoiInfo>()

    fun addToMap() {
        val icon = BitmapDescriptorFactory.fromView(
            View.inflate(context, R.layout.item_sign_default_mark, null)
        )
        pois.forEach { poi ->
            val location = poi.location ?: return@forEach
            val marker = map.addOverlay(MarkerOptions().position(location).icon(icon)) as Marker
            markers[marker] = poi
        }
    }

    fun removeFromMap() {
        markers.keys.forEach { it.remove() }
        markers.clear()
    }

    fun getPoiItem(marker: Marker): PoiInfo? = markers[marker]

    fun zoomToSpan() {
        val points = pois.mapNotNull { it.location }
        if (points.isEmpty()) return
        if (points.size == 1) {
            map.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(points.first(), 17f))
        } else {
            val bounds = LatLngBounds.Builder().apply {
                points.forEach { include(it) }
            }.build()
            map.animateMapStatus(MapStatusUpdateFactory.newLatLngBounds(bounds))
        }
    }
}
