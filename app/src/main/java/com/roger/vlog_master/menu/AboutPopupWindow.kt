package com.roger.vlog_master.menu

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import com.roger.vlog_master.R




class AboutPopupWindow(c: Context, layoutRes: Int, w: Int, h: Int) : BasePopupWindow(c, layoutRes, w, h) {

    override fun initView() {
    }

    override fun initEvent() {

        contentView?.findViewById<View>(R.id.share)?.setOnClickListener {
            share()
            instance.dismiss()
        }

        contentView?.findViewById<View>(R.id.feedback)?.setOnClickListener {
            sendEmail()
            instance.dismiss()
        }
    }

    private fun share(){
        var shareIntent = Intent()
        shareIntent.action = Intent.ACTION_SEND
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.Share_VLog_Title))
        shareIntent.putExtra(Intent.EXTRA_TEXT, context.getString(R.string.Share_VLog_Text))
        shareIntent = Intent.createChooser(shareIntent, context.getString(R.string.Share_To_Friend_Chooser_Title))
        context.startActivity(shareIntent)
    }

    private fun sendEmail() {
        val data = Intent(Intent.ACTION_SENDTO)
        data.data = Uri.parse("mailto:flyrogermail@gmail.com")
        data.putExtra(Intent.EXTRA_SUBJECT, "VLogMaster Feedback")
        val info =
            "Device: " + android.os.Build.MANUFACTURER + "-" + android.os.Build.MODEL + "\n" + "Version: " + getVersionName()
        data.putExtra(Intent.EXTRA_TEXT, info)
        context.startActivity(data)
    }

    private fun getVersionName(): String {
        val packageManager = context.packageManager
        val packInfo = packageManager.getPackageInfo(context.packageName, 0)
        return packInfo.versionName
    }
}