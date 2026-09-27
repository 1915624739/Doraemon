package com.cofbro.qian.mapsetting


import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.MapPoi
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.Marker
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.core.SearchResult
import com.baidu.mapapi.search.geocode.GeoCodeResult
import com.baidu.mapapi.search.geocode.GeoCoder
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener
import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult
import com.baidu.mapapi.search.poi.OnGetPoiSearchResultListener
import com.baidu.mapapi.search.poi.PoiCitySearchOption
import com.baidu.mapapi.search.poi.PoiDetailResult
import com.baidu.mapapi.search.poi.PoiDetailSearchResult
import com.baidu.mapapi.search.poi.PoiIndoorResult
import com.baidu.mapapi.search.poi.PoiResult
import com.baidu.mapapi.search.poi.PoiSearch
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.cofbro.hymvvmutils.base.BaseActivity
import com.cofbro.hymvvmutils.base.getBySp
import com.cofbro.qian.R
import com.cofbro.qian.data.URL
import com.cofbro.qian.databinding.ActivityMapBinding
import com.cofbro.qian.main.MainActivity
import com.cofbro.qian.mapsetting.overlay.Poi2DOverlay
import com.cofbro.qian.mapsetting.util.Constants
import com.cofbro.qian.mapsetting.util.ToastUtil
import com.cofbro.qian.mapsetting.viewmodel.MapViewModel
import com.cofbro.qian.utils.AccountManager
import com.cofbro.qian.utils.BaiduLocationUtils
import com.cofbro.qian.utils.CacheUtils
import com.cofbro.qian.utils.SignRecorder
import com.cofbro.qian.utils.dp2px
import com.cofbro.qian.utils.getStringExt
import com.cofbro.qian.utils.safeParseToJson
import com.cofbro.qian.view.dialog.FullScreenDialog
import com.hjq.toast.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import kotlin.math.abs
import com.cofbro.qian.utils.BaiduLocationUtils.GeoPoint

class MapActivity : BaseActivity<MapViewModel, ActivityMapBinding>(), BaiduMap.OnMarkerClickListener,
    OnGetPoiSearchResultListener, OnGetGeoCoderResultListener {
    private var poiOverlay: Poi2DOverlay? = null
    private var geoCoder: GeoCoder? = null
    private var alreadySign = false
    private var cookies = ""
    private var remark = ""
    private var mStatus = true
    private var otherSignUsers: JSONArray? = null
    private var alreadySignCount = 0
    private var loadingDialog: Dialog? = null
    private var preSignOther = false
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        BaiduLocationUtils.checkLocationPermission(this)
        BaiduLocationUtils.openLocation(this)
        geoCoder = GeoCoder.newInstance().also { it.setOnGetGeoCodeResultListener(this) }
        getAvtarImage()
        initArgs()
        initObserver()
        doNetwork()
        initViewClick()
        initMap(savedInstanceState)
        initLocationData()
    }


    private fun doNetwork() {
        lifecycleScope.launch(Dispatchers.IO) {
            analysisAndStartSign(viewModel.aid)
            viewModel.preSign(viewModel.preUrl)
        }
    }

    private fun initLocationData() {
        viewModel.default_Sign_Lating =
            CacheUtils.cache["default_Sign_latitude"]?.toDouble()
                ?.let {
                    CacheUtils.cache["default_Sign_longitude"]?.toDouble()
                        ?.let { it1 -> GeoPoint(it, it1) }
                }
    }
    private fun initArgs() {
        viewModel.aid = intent.getStringExtra("aid") ?: ""
        viewModel.preUrl = intent.getStringExtra("preUrl") ?: ""
        viewModel.uid = CacheUtils.cache["uid"] ?: ""
        viewModel.courseName = intent.getStringExtra("courseName") ?: ""
    }

    override fun onResume() {
        super.onResume()
        binding?.maps?.onResume()

    }

    override fun onPause() {
        super.onPause()
        // 在activity执行onPause时执行mMapView.onPause ()，暂停地图的绘制
        binding?.maps?.onPause()
    }

    override fun onDestroy() {
        viewModel.poiSearch?.destroy()
        geoCoder?.destroy()
        binding?.maps?.onDestroy()
        super.onDestroy()
    }

    /**
     * 设置页面监听
     */
    private fun setUpMap() {
        binding?.maps?.map?.setOnMarkerClickListener(this) // 添加点击marker监听事件

    }

    /**
     * 显示进度框
     */
    private fun showProgressDialog() {
        if (viewModel.progressDialog == null) viewModel.progressDialog = Dialog(this)
        viewModel.progressDialog?.setCancelable(false)
        viewModel.progressDialog?.show()
    }

    /**
     * 隐藏进度框
     */
    private fun dismissProgressDialog() {
        if (viewModel.progressDialog != null) {
            viewModel.progressDialog?.dismiss()
        }
    }

    /**
     * 开始进行poi搜索
     */
    private fun doSearchQuery(keywords: String?) {
        if (keywords.isNullOrBlank()) return
        showProgressDialog()
        val search = viewModel.poiSearch ?: PoiSearch.newInstance().also {
            it.setOnGetPoiSearchResultListener(this)
            viewModel.poiSearch = it
        }
        search.searchInCity(
            PoiCitySearchOption().city(Constants.DEFAULT_CITY)
                .cityLimit(false).keyword(keywords).pageNum(0).pageCapacity(10)
        )
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        val poi = poiOverlay?.getPoiItem(marker) ?: return false
        val point = poi.location ?: return false
        binding?.maps?.map?.clear()
        poiOverlay = null
        viewModel.currentTipPoint = point
        viewModel.Tip_name = poi.name
        viewModel.Tip_address = poi.address
        viewModel.Tip_City = poi.city
        binding?.mainKeywords?.text = poi.name
        binding?.etLocationName?.setText(poi.name)
        binding?.selectButton?.visibility = View.VISIBLE
        binding?.etLocationName?.visibility = View.VISIBLE
        addLatLngMarker(point)
        return true
    }

    override fun onGetPoiResult(result: PoiResult) {
        dismissProgressDialog()
        if (result.error != SearchResult.ERRORNO.NO_ERROR) {
            ToastUtil.show(this, "地点搜索失败：${result.error}")
            return
        }
        val pois = result.allPoi.orEmpty()
        val map = binding?.maps?.map ?: return
        if (pois.isEmpty()) {
            ToastUtil.show(this, "没有找到地点")
            return
        }
        map.clear()
        poiOverlay = Poi2DOverlay(this, map, pois).also {
            it.addToMap()
            it.zoomToSpan()
        }
    }

    override fun onGetPoiDetailResult(result: PoiDetailResult) = Unit
    override fun onGetPoiDetailResult(result: PoiDetailSearchResult) = Unit
    override fun onGetPoiIndoorResult(result: PoiIndoorResult) = Unit
    override fun onGetGeoCodeResult(result: GeoCodeResult) = Unit

    override fun onGetReverseGeoCodeResult(result: ReverseGeoCodeResult) {
        if (result.error != SearchResult.ERRORNO.NO_ERROR) return
        val location = result.location ?: return
        if (abs(location.latitude - viewModel.currentTipPoint.latitude) > 0.00001 ||
            abs(location.longitude - viewModel.currentTipPoint.longitude) > 0.00001) return
        val address = result.address.orEmpty()
        if (address.isNotBlank()) {
            viewModel.Tip_name = address
            viewModel.Tip_address = address
            viewModel.Tip_City = result.addressDetail?.city
            binding?.etLocationName?.setText(address)
            binding?.mainKeywords?.text = address
        }
    }

    /**
     * 用marker展示输入提示list选中数据
     *
     * @param tip
     */
    private fun addTipMarker(tip: ArrayList<String>) {
        if (tip[0] == "") {
            return
        }
        val view = View.inflate(applicationContext, R.layout.item_sign_default_mark, null)
        val imageView: ImageView = view.findViewById(R.id.avatar_default)
        imageView.setImageDrawable(binding!!.search.drawable)
        val descriptor = BitmapDescriptorFactory.fromView(view)
        val markerPosition = LatLng(tip[3].toDouble(), tip[4].toDouble())
        viewModel.mPoiMarker = binding?.maps?.map?.addOverlay(
            MarkerOptions().position(markerPosition).icon(descriptor).title(tip[0])
        ) as? Marker
        binding?.maps?.map?.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(markerPosition, 17F))
    }

    private fun addLatingDefaultMarker(point: GeoPoint?) {
        if (point == null) {
            return
        }
        val view = View.inflate(applicationContext, R.layout.item_sign_default_mark, null)
        val descriptor = BitmapDescriptorFactory.fromView(view)
        val markerPosition = LatLng(point.latitude, point.longitude)
        viewModel.default_mark = binding?.maps?.map?.addOverlay(
            MarkerOptions().position(markerPosition).icon(descriptor)
        ) as? Marker
    }

    private fun addLatLngMarker(latLng: LatLng?, default: Boolean = false) {
        if (latLng == null) {
            return
        }
        val view = View.inflate(applicationContext, R.layout.item_sign_default_mark, null)
        val imageView: ImageView = view.findViewById(R.id.avatar_default)
        imageView.setImageDrawable(binding!!.search.drawable)
        val descriptor = BitmapDescriptorFactory.fromView(view)
        val point = latLng
        val markerPosition = LatLng(point.latitude, point.longitude)
        viewModel.mPoiMarker = binding?.maps?.map?.addOverlay(
            MarkerOptions().position(markerPosition).icon(descriptor)
        ) as? Marker
        if (!default) {
            binding?.maps?.map?.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(markerPosition, 17F))
        }
    }

    private fun getAvtarImage() {
        // 用户头像
        val uid = CacheUtils.cache["uid"]
        uid?.let {
            val options = RequestOptions().transform(
                CenterCrop(),
                RoundedCorners(dp2px(applicationContext, 5))
            )
            Glide.with(this@MapActivity)
                .load(URL.getAvtarImgPath(it))
                .apply(options)
                .into(binding!!.search)
        }

    }

    /**
     * 点击事件回调方法
     */

    companion object {
        const val REQUEST_CODE = 100
        const val RESULT_CODE_INPUTTIPS = 101
        const val RESULT_CODE_KEYWORDS = 102
    }

    private fun initViewClick() {
        binding?.selectButton?.setOnClickListener {
            if (viewModel.statuscontent == "签到成功") {
                ToastUtil.show(applicationContext, "您已经签到过了")
                return@setOnClickListener
            }
            val point = viewModel.currentTipPoint
            if (point.latitude !in -90.0..90.0 || point.longitude !in -180.0..180.0 ||
                point.latitude == 0.0 || point.longitude == 0.0) {
                ToastUtils.show("请先在地图上选择位置")
                return@setOnClickListener
            }
            val enteredAddress = binding?.etLocationName?.text?.toString()?.trim().orEmpty()
            val address = enteredAddress.ifEmpty {
                viewModel.Tip_name?.takeIf { it.isNotBlank() }
                    ?: viewModel.default_Sign_Location?.takeIf { it.isNotBlank() }
                    ?: "已选位置"
            }
            viewModel.signUrl = URL.getLocationSignPath(
                urlEncodeChinese(address), viewModel.aid, viewModel.uid,
                point.latitude.toString(), point.longitude.toString()
            )
            sign(viewModel.signUrl)
        }
        binding?.mainKeywords?.apply {
            setOnClickListener {
                val intent = Intent(this@MapActivity, InputTipsActivity::class.java)
                intent.putExtra("code", REQUEST_CODE);
                intent.putExtra("aid", viewModel.aid)
                /**
                 * 保存并传递数据
                 */
                startActivityForResult(intent,100)
//                startActivity(intent)
//                finish()
            }

        }


    }

    private fun signRecord(body: String = "", cookies: String = "") {
        if (alreadySign) return
        if (body.isNotEmpty()) {
            val status = body.contains("成功") || body.contains("success")
            val uid = if (cookies.isEmpty()) CacheUtils.cache["uid"] ?: "" else findUID(cookies)
            record(uid, status = status)
        } else {
            val uid = if (cookies.isEmpty()) CacheUtils.cache["uid"] ?: "" else findUID(cookies)
            record(uid, status = mStatus)
        }

    }

    private fun record(uid: String, status: Boolean) {
        val courseName = viewModel.courseName
        val statusName = if (status) "成功" else "失败"
        val username = if (remark.isNotEmpty()) "$uid - ($remark)" else uid
        SignRecorder.record(applicationContext, username, courseName!!, statusName)
    }

    private suspend fun analysisAndStartSign(aid: String) {
        viewModel.analysis(URL.getAnalysisPath(aid))
    }

    private fun initObserver() {
        // 签到
        viewModel.signLiveData.observe(this) {
            lifecycleScope.launch(Dispatchers.IO) {
                val data = it.data?.body?.string()
                withContext(Dispatchers.Main) {
                    /**
                     * 回到TaskActivity
                     */
                    if (data == "不在可签到范围内") {
                        ToastUtils.show("签到失败")
                        signRecord(data)
                        finish()
                    } else {
                        /**
                         * 回到TaskFragment
                         */
                        if (data!!.contains("success")) {
                            mStatus = true
                            ToastUtils.show("签到已成功")

                            signRecord(data)
                            /**
                             * 开始代签
                             */
                            // 开始代签
                            showLoadingView()
                            startSignTogether(data)
//                            startActivity(intent)

                        } else {
                            mStatus = false
                            ToastUtil.show(applicationContext, "签到失败")
//                            val intent = Intent(applicationContext, MainActivity::class.java)
                            signRecord(data)
//                            startActivity(intent)
                        }

                        /**
                         * 回去缺少网络请求
                         */

                    }
                }
            }
        }
        viewModel.analysisLiveData.observe(this) {
            lifecycleScope.launch(Dispatchers.IO) {
                val data = it.data?.body?.string()
                val analysis2Code = data?.substringAfter("code='+'")?.substringBefore("'") ?: ""
                viewModel.analysis2(URL.getAnalysis2Path(analysis2Code))
            }

        }
        viewModel.preSignLiveData.observe(this) {
            lifecycleScope.launch(Dispatchers.IO) {
                val html = it.data?.body?.string()
                withContext(Dispatchers.Main) {
                html?.let {
                    if (preSignOther) {
                        /**
                         * 代签无需进行操作
                         */
                    } else {
                        viewModel.preSignWebGet(it,
                            onSuccess = { preWeb ->
                                binding?.mainKeywords?.apply {
                                    hint = if (preWeb.locationText?.isNotEmpty() == true) {
                                        preWeb.locationText
                                    } else {
                                        "老师未设置位置,请点击搜索"
                                    }
                                }
                                if (preWeb.latitude?.toDoubleOrNull() != null && preWeb.latitude != "-1" &&
                                    preWeb.longitude?.toDoubleOrNull() != null && preWeb.longitude != "-1") {
                                    viewModel.currentTipPoint =
                                        LatLng(
                                            preWeb.latitude.toDouble(),
                                            preWeb.longitude.toDouble()
                                        )

                                    addLatLngMarker(
                                        LatLng(
                                            viewModel.currentTipPoint.latitude,
                                            viewModel.currentTipPoint.longitude
                                        ), default = false
                                    )
                                    viewModel.default_Sign_Location = preWeb.locationText
                                    viewModel.default_Sign_Location = preWeb.locationText
                                    viewModel.statuscontent = preWeb.statusContent
                                    viewModel.default_Sign_Lating =
                                        GeoPoint(
                                            preWeb.latitude.toDouble(),
                                            preWeb.longitude.toDouble()
                                        )
                                    /**
                                     * 老师未设置位置 设置提醒
                                     */
                                    if (preWeb.locationText?.isEmpty() == true && preWeb.statusContent != "签到成功") {
                                        ToastUtil.show(
                                            applicationContext,
                                            "老师未设置位置，默认位置为自己位置"
                                        )
                                        viewModel.default_Sign_Lating = viewModel.default_My_Lating
                                    } else if (preWeb.statusContent == "签到成功") {
                                        alreadySign = true
                                    }
                                    CacheUtils.cache["default_Sign_latitude"] = preWeb.latitude
                                    CacheUtils.cache["default_Sign_longitude"] = preWeb.longitude
                                } else {
                                    val lat = preWeb.html.getElementById("latitude")?.`val`() ?: ""
                                    val long =
                                        preWeb.html.getElementById("longitude")?.`val`() ?: ""
                                    if (lat.toDoubleOrNull() != null && long.toDoubleOrNull() != null &&
                                        lat != "-1" && long != "-1") {
                                        viewModel.currentTipPoint =
                                            LatLng(lat.toDouble(), long.toDouble())
                                        addLatLngMarker(
                                            LatLng(lat.toDouble(), long.toDouble()),
                                            default = false
                                        )
                                        viewModel.default_Sign_Lating =
                                            GeoPoint(lat.toDouble(), long.toDouble())
                                        viewModel.default_Sign_Location = preWeb.locationText
                                        viewModel.statuscontent = preWeb.statusContent
                                        if (preWeb.locationText?.isEmpty() == true && preWeb.statusContent != "签到成功") {
                                            ToastUtil.show(
                                                applicationContext,
                                                "老师未设置位置，默认位置为自己位置"
                                            )
                                            viewModel.default_Sign_Lating =
                                                viewModel.default_My_Lating
                                        } else if (preWeb.statusContent == "签到成功") {
                                            alreadySign = true
                                        }
                                        CacheUtils.cache["default_Sign_latitude"] = lat
                                        CacheUtils.cache["default_Sign_longitude"] = long
                                    }
                                }

                            })
                        preSignOther = false
                    }
                }
                }
            }
        }
        // 尝试登录
        viewModel.loginLiveData.observe(this) { response ->
            val data = response.data ?: return@observe
            lifecycleScope.launch(Dispatchers.IO) {
                val body = data.body?.string()?.safeParseToJson()
                val headers = data.headers
                cookies = headers.values("Set-Cookie").toString()
                if (body?.getBoolean("status") == true) {
                    signWith(viewModel.aid, cookies)
                }
            }
        }
        // 绑定签到
        viewModel.signTogetherLiveData.observe(this) { response ->
            val data = response.data ?: return@observe
            lifecycleScope.launch(Dispatchers.IO) {
                val body = data.body?.string() ?: ""
                signRecord(body, cookies)
                if (alreadySignCount < (otherSignUsers?.size ?: 0)) {
                    val itemUser =
                        otherSignUsers?.getOrNull(alreadySignCount) as? JSONObject ?: JSONObject()
                    remark = itemUser.getStringExt(com.cofbro.qian.utils.Constants.Account.REMARK)
                    tryLogin(itemUser)
                    alreadySignCount++
                } else {
                    withContext(Dispatchers.Main) {
                        hideLoadingView()
                        val intent = Intent(applicationContext, MainActivity::class.java)
                        startActivity(intent)
                    }
                }

            }
        }
    }

    private fun showLoadingView() {
        if (loadingDialog == null) {
            loadingDialog = FullScreenDialog(this)
        }
        loadingDialog?.setCancelable(false)
        loadingDialog?.show()
    }

    private fun hideLoadingView() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    private suspend fun signWith(id: String, cookies: String) {
        viewModel.analysisForSignTogether(URL.getAnalysisPath(id),
            cookies,
            onSuccess = {
                lifecycleScope.launch(Dispatchers.IO) {
                    val data = it.body?.string()
                    val analysis2Code = data?.substringAfter("code='+'")?.substringBefore("'") ?: ""
                    viewModel.analysis2(URL.getAnalysis2Path(analysis2Code), cookies)
                    delay(200)
                    val uid = findUID(cookies)
                    val tempSignpre = viewModel.preUrl.replace(viewModel.uid, uid)
                    viewModel.preSign(tempSignpre, cookies)
                    /*
                    拼接URL
                     */
                    signTogether(cookies)
                }
            },
            onFailure = { msg ->
                ToastUtils.show(msg)
            }
        )
    }

    private fun sign(url: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            /**
             * 绑定签到  判断
             */
//            analysisAndStartSign(viewModel.aid)
            viewModel.sign(url)
        }

    }

    private suspend fun signTogether(cookies: String) {
        val uid = findUID(cookies)
        val tempUrl = viewModel.signUrl.replace(viewModel.uid, uid)
        viewModel.signTogether(tempUrl, cookies)
    }

    private suspend fun startSignTogether(data: String) {
        // 开始代签
        val signWith = applicationContext.getBySp("signWith")?.toBoolean() ?: false
        if (signWith && (data.contains("success") || data.contains("签到成功"))) {
            // 如果本账号签到成功，则开始自动签到其他绑定账号
            signWithAccounts()
            preSignOther = true
        } else {
            val intent = Intent(applicationContext, MainActivity::class.java)
            preSignOther = false
            startActivity(intent)
        }


    }

    private suspend fun signWithAccounts() {
        withContext(Dispatchers.IO) {
            val data = AccountManager.loadAllAccountData(applicationContext)
            otherSignUsers = data.getJSONArray(com.cofbro.qian.utils.Constants.Account.USERS)
            val firstUser = otherSignUsers?.getOrNull(0) as? JSONObject
            if (firstUser != null) {
                alreadySignCount++
                remark = firstUser.getStringExt(com.cofbro.qian.utils.Constants.Account.REMARK)
                tryLogin(firstUser)
            }
        }
    }

    private fun tryLogin(user: JSONObject) {
        val username = user.getStringExt(com.cofbro.qian.utils.Constants.Account.USERNAME)
        val password = user.getStringExt(com.cofbro.qian.utils.Constants.Account.PASSWORD)
        if (username.isNotEmpty() && password.isNotEmpty()) {
            viewModel.tryLogin(URL.getLoginPath(username, password))
        }
    }

    private fun findUID(cookies: String): String {
        val uid = cookies.substringAfter("UID=")
        return uid.substringBefore(";")
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        when (requestCode) {
            REQUEST_CODE -> {
                    if (resultCode == RESULT_CODE_KEYWORDS) {
                        doSearchQuery(intent?.getStringExtra(Constants.KEY_WORDS_NAME))
                        return
                    }
                    if (intent != null && intent.hasExtra(Constants.EXTRA_TIP)) {
                        val tip = intent.getStringArrayListExtra(Constants.EXTRA_TIP)
                        if (tip != null && tip.size >= 6 && tip[3].toDoubleOrNull() != null && tip[4].toDoubleOrNull() != null) {
                            /**
                            获取完整Tip
                             */
                            binding?.maps?.map?.clear()
                            viewModel.currentTipPoint = LatLng(tip[3].toDouble(), tip[4].toDouble())
                            if (tip[2] == null || tip[2] == "") {
                                doSearchQuery(tip[0])
                            } else {
                                addTipMarker(tip)
                            }
                            if (tip[0].isNotEmpty()) {
                                binding?.selectButton?.visibility = View.VISIBLE
                                binding?.etLocationName?.visibility = View.VISIBLE
                                binding?.mainKeywords?.text = tip[0]
                            }

                            if (tip[0] != "") {
                                //binding?.cleanKeywords?.visibility = View.VISIBLE
                            }
                            // 获取完整的name和address
                            viewModel.Tip_name = tip[0]
                            viewModel.Tip_address = tip[1]
                            viewModel.Tip_City = tip[5]
                            binding?.etLocationName?.setText(tip[0])
                        }
                    }
            }
        }
    }
    private fun initMap(savedInstanceState: Bundle?) {
        binding?.maps?.onCreate(this, savedInstanceState)
        setUpMap()
        binding?.maps?.map?.setOnMapClickListener(object : BaiduMap.OnMapClickListener {
            override fun onMapClick(point: LatLng) {
                binding?.maps?.map?.clear()
                poiOverlay = null
                viewModel.currentTipPoint = point
                viewModel.Tip_name = null
                viewModel.Tip_address = null
                viewModel.Tip_City = null
                binding?.etLocationName?.setText("")
                binding?.selectButton?.visibility = View.VISIBLE
                binding?.etLocationName?.visibility = View.VISIBLE
                addLatLngMarker(point, default = true)
                addLatingDefaultMarker(viewModel.default_Sign_Lating)
                geoCoder?.reverseGeoCode(ReverseGeoCodeOption().location(point))
            }

            override fun onMapPoiClick(poi: MapPoi) {
                onMapClick(poi.position)
            }
        })
        loadCurrentLocation()
    }

    private fun loadCurrentLocation() {
        BaiduLocationUtils.getCurrentLocationLatLng(applicationContext,
            onSuccess = { lat, lon, address ->
                viewModel.default_My_Lating = GeoPoint(lat, lon)
                viewModel.default_My_Location = address
                if (viewModel.currentTipPoint.latitude == 0.0) {
                    binding?.maps?.map?.animateMapStatus(
                        MapStatusUpdateFactory.newLatLngZoom(LatLng(lat, lon), 17F)
                    )
                }
            },
            onError = { error ->
                if (error != "缺少精确定位权限") ToastUtils.show(error)
            })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1005 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            loadCurrentLocation()
        }
    }

    private fun urlEncodeChinese(urlString: String): String {
        return URLEncoder.encode(urlString, "UTF-8")
    }

    override fun onStop() {
        super.onStop()
        SignRecorder.writeJson(applicationContext)
    }


}
