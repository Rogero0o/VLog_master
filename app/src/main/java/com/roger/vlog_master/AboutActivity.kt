package com.roger.vlog_master

import android.content.res.Configuration
import android.os.Bundle
import android.support.v7.app.AppCompatActivity

import android.support.v7.app.AppCompatDelegate
import mehdi.sakout.aboutpage.AboutPage
import mehdi.sakout.aboutpage.Element
import android.widget.Toast
import android.view.Gravity
import java.util.*
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.net.Uri


class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        simulateDayNight(0)
        val aboutPage = AboutPage(this)
            .isRTL(false)
            .setDescription(getString(R.string.About_desc))
            .setImage(R.mipmap.ic_launcher)
            .addItem(Element().setTitle("Version " + versionName(this)))
            .addItem(getPrivacyPolicy())
            .addGroup("Connect with us")
            .addEmail("flyrogermail@gmail.com")
            .addWebsite("https://github.com/Rogero0o")
//            .addPlayStore("com.ideashower.readitlater.pro")
            .addGitHub("Rogero0o")
            .addItem(getCopyRightsElement())
            .create()

        setContentView(aboutPage)
    }

    private fun getCopyRightsElement(): Element {
        val copyRightsElement = Element()
        val copyrights = String.format(getString(R.string.copy_right), Calendar.getInstance().get(Calendar.YEAR))
        copyRightsElement.title = copyrights
        copyRightsElement.iconDrawable = R.drawable.about_icon_copy_right
        copyRightsElement.iconTint = mehdi.sakout.aboutpage.R.color.about_item_icon_color
        copyRightsElement.iconNightTint = android.R.color.white
        copyRightsElement.gravity = Gravity.CENTER
        copyRightsElement.setOnClickListener {
            Toast.makeText(this, copyrights, Toast.LENGTH_SHORT).show()
        }
        return copyRightsElement
    }

    private fun getPrivacyPolicy(): Element {
        val policyElement = Element()
        policyElement.title = getString(R.string.privacy_policy)
        policyElement.setOnClickListener {
            val uri = Uri.parse("https://termsfeed.com/privacy-policy/b6dec66702956ad62968568b0116044d")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }
        return policyElement
    }

    private fun simulateDayNight(currentSetting: Int) {
        val day = 0
        val night = 1
        val system = 3

        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (currentSetting == day && currentNightMode != Configuration.UI_MODE_NIGHT_NO) {
            AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_NO
            )
        } else if (currentSetting == night && currentNightMode != Configuration.UI_MODE_NIGHT_YES) {
            AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_YES
            )
        } else if (currentSetting == system) {
            AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            )
        }
    }

    private fun versionName(context: Context): String? {
        val manager = context.packageManager
        var name: String? = null
        try {
            val info = manager.getPackageInfo(context.packageName, 0)
            name = info.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return name
    }

}
