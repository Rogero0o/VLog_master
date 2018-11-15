package com.roger.vlog_master.menu

import android.content.Context
import android.util.Log
import android.view.View
import android.view.View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION
import android.view.ViewGroup
import android.widget.TextView
import com.orhanobut.hawk.Hawk
import com.roger.vlog_master.MainActivity
import com.roger.vlog_master.R
import com.roger.vlog_master.utils.KEY_TIME_INTERVAL
import com.roger.vlog_master.utils.LOG_TAG
import java.util.regex.Pattern


class TimeIntervalPopupWindow(c: Context, layoutRes: Int, w: Int, h: Int) : BasePopupWindow(c, layoutRes, w, h) {

    private lateinit var imageViewList: ArrayList<View>

    override fun initView() {

    }

    override fun initEvent() {
        val views = ArrayList<View>()
        imageViewList = ArrayList()
        contentView?.findViewsWithText(
            views,
            context.resources.getString(R.string.second),
            FIND_VIEWS_WITH_CONTENT_DESCRIPTION
        )
        val interval = Hawk.get(KEY_TIME_INTERVAL, -1f) / 1000f
        for (view in views) {
            val imageView = (view as ViewGroup).getChildAt(1)
            val value = (view.getChildAt(0) as TextView).text
            if (value.contains(interval.toString())) {
                imageView.visibility = View.VISIBLE
            } else {
                imageView.visibility = View.INVISIBLE
            }
            imageViewList.add(imageView)
            view.setOnClickListener {
                setImageViewCheck(view.getChildAt(1).id)
                Hawk.put(KEY_TIME_INTERVAL, getValueFromText(value.toString()) * 1000)
                (context as MainActivity).updateVideoInfo(0L)
                instance.dismiss()
                Log.i(LOG_TAG, (Hawk.get(KEY_TIME_INTERVAL) as Float).toString())
            }
        }
    }

    private fun setImageViewCheck(checkedId: Int) {
        imageViewList.let {
            for (imageView in it) {
                if (imageView.id == checkedId) {
                    imageView.visibility = View.VISIBLE
                } else {
                    imageView.visibility = View.INVISIBLE
                }
            }
        }
    }

    private fun getValueFromText(str: String): Float {
        val p = Pattern.compile("\\d+")
        val m = p.matcher(str)
        while (m.find()) {
            if (m.group().toFloat() == 0f) {
                return 0.5f
            }
            return m.group().toFloat()
        }
        return 1.0f
    }
}