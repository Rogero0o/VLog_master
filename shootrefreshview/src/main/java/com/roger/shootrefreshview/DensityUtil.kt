package com.roger.shootrefreshview

import android.content.Context

object DensityUtil {

    fun dp2px(context: Context, dpValue: Float): Float {
        val scale = context.resources.displayMetrics.density
        return dpValue * scale
    }
}