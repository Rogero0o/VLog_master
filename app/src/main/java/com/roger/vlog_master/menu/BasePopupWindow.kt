package com.roger.vlog_master.menu

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.widget.PopupWindow
import android.view.LayoutInflater
import android.view.View
import com.roger.vlog_master.R


abstract class BasePopupWindow(c: Context, layoutRes: Int, w: Int, h: Int) {
    protected var context: Context = c
    protected var contentView: View? = null
    private var mInstance: PopupWindow

    init {
        contentView = LayoutInflater.from(c).inflate(layoutRes, null, false)
        initView()
        initEvent()
        mInstance = PopupWindow(contentView, w, h, true)
        initWindow()
    }

    protected abstract fun initView()
    protected abstract fun initEvent()

    private fun initWindow() {
        mInstance.setBackgroundDrawable(ColorDrawable(context.resources.getColor(R.color.color_66000000)))
        mInstance.isOutsideTouchable = true
        mInstance.isTouchable = true
    }

    fun showAsDropDown(anchor: View, xoff: Int, yoff: Int) {
        mInstance.showAsDropDown(anchor, xoff + 5, yoff)
    }
}