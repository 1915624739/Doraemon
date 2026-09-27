package com.cofbro.qian.mapsetting

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.baidu.mapapi.search.core.SearchResult
import com.baidu.mapapi.search.sug.OnGetSuggestionResultListener
import com.baidu.mapapi.search.sug.SuggestionResult
import com.baidu.mapapi.search.sug.SuggestionSearch
import com.baidu.mapapi.search.sug.SuggestionSearchOption
import com.cofbro.hymvvmutils.base.BaseActivity
import com.cofbro.qian.R
import com.cofbro.qian.databinding.ActivityInputTipsBinding
import com.cofbro.qian.mapsetting.adapter.InputTipsAdapter
import com.cofbro.qian.mapsetting.model.PlaceSuggestion
import com.cofbro.qian.mapsetting.util.Constants
import com.cofbro.qian.mapsetting.util.ToastUtil
import com.cofbro.qian.mapsetting.viewmodel.InputTipViewModel
import com.cofbro.qian.utils.TipUtils


class InputTipsActivity : BaseActivity<InputTipViewModel,ActivityInputTipsBinding>(), SearchView.OnQueryTextListener,
    OnGetSuggestionResultListener, View.OnClickListener {
    var aid:String? = null
    private var suggestionSearch: SuggestionSearch? = null
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        suggestionSearch = SuggestionSearch.newInstance().also {
            it.setOnGetSuggestionResultListener(this)
        }
        initArgs()
        initSearchView()
        initViewClick()

    }
    private fun initArgs(){
        val intent = intent
        aid = intent.getStringExtra("aid")
        /**
         * 传递数据
         */
    }
    private fun  initViewClick(){
        binding?.back?.setOnClickListener(this)
    }

    private fun initSearchView() {
        binding?.keyWord?.setOnQueryTextListener(this)
        //设置SearchView默认为展开显示
        binding?.keyWord?.isIconified = false
        binding?.keyWord?.onActionViewExpanded()
        binding?.keyWord?.setIconifiedByDefault(true)
        binding?.keyWord?.isSubmitButtonEnabled = false
    }

    /**
     * 输入提示回调
     *
     * @param tipList
     * @param rCode
     */
    override fun onGetSuggestionResult(result: SuggestionResult) {
        if (result.error == SearchResult.ERRORNO.NO_ERROR) {
            viewModel.mCurrentTipList = result.allSuggestions.orEmpty().map { tip ->
                PlaceSuggestion(
                    name = tip.key.orEmpty(),
                    address = tip.address.orEmpty().ifBlank {
                        listOf(tip.city, tip.district).filterNotNull().joinToString(" ")
                    },
                    poiId = tip.uid.orEmpty(),
                    latitude = tip.pt?.latitude,
                    longitude = tip.pt?.longitude,
                    city = tip.city.orEmpty()
                )
            }.toMutableList()
            viewModel.mIntipAdapter = InputTipsAdapter(
                this, currentTip = viewModel.mCurrentTipList!!
            )
            binding?.inputtipList?.apply {
                adapter = viewModel.mIntipAdapter
                layoutManager = LinearLayoutManager(this@InputTipsActivity, RecyclerView.VERTICAL,false)
            }
            viewModel.mIntipAdapter?.setItemClickListener {
                if (viewModel.mCurrentTipList != null) {
                    /**
                     *  实现跳转
                     */
                    val intent = Intent(this, MapActivity::class.java)
                    if(it.latitude != null && it.longitude != null){
                        intent.putExtra(Constants.EXTRA_TIP, TipUtils.TipParseToArray(it))
                        intent.putExtra("aid",aid)
                        /**
                         * 保存并传递数据
                         */
                        setResult(100,intent)
                        finish()
                    } else {
                        intent.putExtra(Constants.KEY_WORDS_NAME, it.name)
                        setResult(MapActivity.RESULT_CODE_KEYWORDS, intent)
                        finish()
                    }

                }
            }
        } else {
            viewModel.mCurrentTipList?.clear()
            viewModel.mIntipAdapter?.notifyDataSetChanged()
            ToastUtil.show(this, "地点提示失败：${result.error}")
        }
    }
    /**
     * 按下确认键触发，本例为键盘回车或搜索键
     *
     * @param query
     * @return
     */
    override fun onQueryTextSubmit(query: String?): Boolean {
        val intent = Intent()
        intent.putExtra(Constants.KEY_WORDS_NAME, query)
        setResult(MapActivity.RESULT_CODE_KEYWORDS, intent)
        finish()
        return false
    }

    /**
     * 输入字符变化时触发
     *
     * @param newText
     * @return
     */
    override fun onQueryTextChange(newText: String?): Boolean {
        if (!IsEmptyOrNullString(newText)) {
            suggestionSearch?.requestSuggestion(
                SuggestionSearchOption()
                    .city(Constants.DEFAULT_CITY)
                    .citylimit(false)
                    .keyword(newText)
            )
        } else {
            if (viewModel.mIntipAdapter != null && viewModel.mCurrentTipList != null) {
                viewModel.mCurrentTipList!!.clear()
                viewModel.mIntipAdapter!!.notifyDataSetChanged()
            }
        }
        return false
    }

    override fun onClick(view: View) {
        if (view.id == R.id.back) {
            finish()
        }
    }

    override fun onDestroy() {
        suggestionSearch?.destroy()
        suggestionSearch = null
        super.onDestroy()
    }

    companion object {
        fun IsEmptyOrNullString(s: String?): Boolean {
            return s == null || s.trim { it <= ' ' }.length == 0
        }
    }
}
