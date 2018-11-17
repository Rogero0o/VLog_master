package com.roger.vlog_master.menu

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.orhanobut.hawk.Hawk
import com.roger.vlog_master.MainActivity
import com.roger.vlog_master.R
import com.roger.vlog_master.utils.KEY_TIME_LONG
import com.roger.vlog_master.utils.LOG_TAG
import java.util.regex.Pattern

class TimeLongPopupWindow(c: Context, layoutRes: Int, w: Int, h: Int) : BasePopupWindow(c, layoutRes, w, h) {

    private lateinit var imageViewList: ArrayList<View>

    override fun initView() {

    }

    override fun initEvent() {
        val views = ArrayList<View>()
        imageViewList = ArrayList()
        contentView?.findViewsWithText(
            views,
            context.resources.getString(R.string.time_long),
            View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION
        )

        val timeLong = Hawk.get(KEY_TIME_LONG, -1f) / 60000f

        for (view in views) {

            val imageView = (view as ViewGroup).getChildAt(1)
            val value = (view.getChildAt(0) as TextView).text.toString()
            if (getValueFromText(value) == timeLong
                || (timeLong < 0 && value == context.getString(R.string.infinite))) {
                imageView.visibility = View.VISIBLE
            } else {
                imageView.visibility = View.INVISIBLE
            }
            imageViewList.add(imageView)
            view.setOnClickListener {
                setImageViewCheck(view.getChildAt(1).id)
                Hawk.put(KEY_TIME_LONG, getValueFromText(value) * 1000 * 60)
                (context as MainActivity).updateVideoUIInfo(0L)
                instance.dismiss()
                Log.i(LOG_TAG, (Hawk.get(KEY_TIME_LONG) as Float).toString())
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

    /**
     * return minutes
     */
    private fun getValueFromText(str: String): Float {
        val p = Pattern.compile("\\d+")
        val m = p.matcher(str)
        while (m.find()) {
            val value = m.group().toFloat()
            return if (value < 10f) {
                value * 60
            } else value
        }
        return -1.0f
    }
}