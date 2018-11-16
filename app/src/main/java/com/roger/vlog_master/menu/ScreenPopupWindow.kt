package com.roger.vlog_master.menu

import android.content.Context
import android.view.View
import com.orhanobut.hawk.Hawk
import com.roger.vlog_master.MainActivity
import com.roger.vlog_master.R
import com.roger.vlog_master.utils.*

class ScreenPopupWindow(c: Context, layoutRes: Int, w: Int, h: Int) : BasePopupWindow(c, layoutRes, w, h) {

    override fun initView() {
        if (Hawk.get(KEY_SCREEN, VALUE_SCREEN_1080) == VALUE_SCREEN_1080) {
            CameraUtils.getCamManagerInstance(context).setPreviewSize(VALUE_SCREEN_1080_WITH, VALUE_SCREEN_1080_HEIGH)
            contentView?.findViewById<View>(R.id.screen_1080_checked)?.visibility = View.VISIBLE
            contentView?.findViewById<View>(R.id.screen_720_checked)?.visibility = View.INVISIBLE
        } else {
            CameraUtils.getCamManagerInstance(context).setPreviewSize(VALUE_SCREEN_720_WITH, VALUE_SCREEN_720_HEIGH)
            contentView?.findViewById<View>(R.id.screen_1080_checked)?.visibility = View.INVISIBLE
            contentView?.findViewById<View>(R.id.screen_720_checked)?.visibility = View.VISIBLE
        }
    }

    override fun initEvent() {
        contentView?.findViewById<View>(R.id.screen_1080)?.setOnClickListener {
            contentView?.findViewById<View>(R.id.screen_1080_checked)?.visibility = View.VISIBLE
            contentView?.findViewById<View>(R.id.screen_720_checked)?.visibility = View.INVISIBLE
            Hawk.put(KEY_SCREEN, VALUE_SCREEN_1080)
            CameraUtils.getCamManagerInstance(context).setPreviewSize(VALUE_SCREEN_1080_WITH, VALUE_SCREEN_1080_HEIGH)
            (context as MainActivity).rePreview()
            instance.dismiss()
        }

        contentView?.findViewById<View>(R.id.screen_720)?.setOnClickListener {
            contentView?.findViewById<View>(R.id.screen_1080_checked)?.visibility = View.INVISIBLE
            contentView?.findViewById<View>(R.id.screen_720_checked)?.visibility = View.VISIBLE
            Hawk.put(KEY_SCREEN, VALUE_SCREEN_720)
            CameraUtils.getCamManagerInstance(context).setPreviewSize(VALUE_SCREEN_720_WITH, VALUE_SCREEN_720_HEIGH)
            (context as MainActivity).rePreview()
            instance.dismiss()
        }
    }
}