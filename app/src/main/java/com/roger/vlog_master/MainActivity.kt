@file:Suppress("DEPRECATION")

package com.roger.vlog_master

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Camera
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.view.SurfaceHolder
import android.view.View
import com.roger.match.library.util.Utils
import com.roger.vlog_master.jcodec.ListCache
import com.roger.vlog_master.jcodec.MediaMuxerUtils
import com.roger.vlog_master.jcodec.SequenceEncoderMp4
import com.roger.vlog_master.utils.*
import kotlinx.android.synthetic.main.activity_main.*
import kr.co.namee.permissiongen.PermissionFail
import kr.co.namee.permissiongen.PermissionGen
import kr.co.namee.permissiongen.PermissionSuccess
import java.io.IOException
import java.lang.ref.WeakReference
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import com.roger.shootrefreshview.DensityUtil.dp2px
import com.roger.vlog_master.menu.ScreenPopupWindow


class MainActivity : AppCompatActivity(), SurfaceHolder.Callback, CameraUtils.OnPreviewFrameResult,
    CameraUtils.OnCameraFocusResult {


    private lateinit var cameraUtils: CameraUtils
    private val delayHandler = DelayHandler(this)
    private var shouldCatchPreview: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initView()
        initMenu()
        cameraUtils = CameraUtils.getCamManagerInstance(this@MainActivity)
    }

    private fun initView() {
        record_surface.holder.addCallback(this)
        record_surface.setOnClickListener {
            cameraUtils.cameraFocus(this)
        }
        shoot_refresh_view.setOnClickListener {
            if (shoot_refresh_view.isStarted()) {
                shoot_refresh_view.reset()
                finishShootAndMakeFile()
            } else {
                shoot_refresh_view.start()
                beginShoot()
            }
        }

        matchTextView.setLineWidth(Utils.dp2px(5f))
        matchTextView.setInTime(0.3f)
        matchTextView.loadingAniDuration = 300
    }

    private fun initJcodec() {
        val file = FileUtils.getFile()
        try {
            SequenceEncoderMp4.instance = SequenceEncoderMp4(file, this)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun beginShoot() {
        MediaMuxerUtils.muxerRunnableInstance.startMuxerThread(cameraUtils.cameraDirection, false)
        delayHandler.sendEmptyMessage(HANDLER_SHOOT_WHAT)
    }

    fun continueShoot() {
        delayHandler.sendEmptyMessageDelayed(HANDLER_SHOOT_WHAT, HANDLER_SHOOT_DELAY)
    }

    private fun finishShootAndMakeFile() {
        delayHandler.removeMessages(HANDLER_SHOOT_WHAT)
        SequenceEncoderMp4.instance?.setFrameNo(ListCache.getInstance(this@MainActivity).lastIndex.toInt())
        SequenceEncoderMp4.instance?.finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        PermissionGen.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
    }

    @PermissionSuccess(requestCode = REQUEST_CAMERA_PERMISSION_CODE)
    fun onPermissionGranted() {
        initJcodec()
        cameraUtils.setOnPreviewResult(this)
        cameraUtils.createCamera()
        cameraUtils.startPreview()
    }

    @PermissionFail(requestCode = REQUEST_CAMERA_PERMISSION_CODE)
    fun onPermissionGrantedFail() {
        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) ||
            !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        ) {
            AlertDialog.Builder(this)
                .setTitle(R.string.notice)
                .setMessage(R.string.permission_message)
                .setCancelable(false)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", applicationContext.packageName, null)
                    intent.data = uri
                    startActivity(intent)
                }.show()
        } else {
            AlertDialog.Builder(this)
                .setTitle(R.string.notice)
                .setMessage(R.string.permission_message)
                .setCancelable(false)
                .setPositiveButton(R.string.ok) { _, _ ->
                    PermissionGen.with(this@MainActivity)
                        .addRequestCode(REQUEST_CAMERA_PERMISSION_CODE)
                        .permissions(
                            Manifest.permission.CAMERA,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                        .request()
                }.show()
        }
    }

    override fun onFocusResult(result: Boolean) {
    }

    override fun onPreviewResult(data: ByteArray, camera: Camera) {
        cameraUtils.cameraInstance?.addCallbackBuffer(data)
        if (shouldCatchPreview) {
            MediaMuxerUtils.muxerRunnableInstance.addVideoFrameData(data)
            shouldCatchPreview = false
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        cameraUtils.surfaceHolder = holder
        delayHandler.sendEmptyMessageDelayed(HANDLER_PREVIEW_WHAT, 2450)
    }

    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        cameraUtils.stopPreivew()
        cameraUtils.destroyCamera()
    }

    fun isPermissionOK(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hideLoading() {
        loadingContainer.visibility = View.GONE
    }

    private class DelayHandler(activity: MainActivity) : Handler() {
        private val mActivity: WeakReference<MainActivity> = WeakReference(activity)

        override fun handleMessage(msg: Message) {
            val activity = mActivity.get()
            activity?.let {
                if (msg.what == HANDLER_SHOOT_WHAT) {
                    activity.shouldCatchPreview = true
                    activity.continueShoot()
                } else if (msg.what == HANDLER_PREVIEW_WHAT) {
                    if (!activity.isPermissionOK()) {
                        PermissionGen.with(activity)
                            .addRequestCode(REQUEST_CAMERA_PERMISSION_CODE)
                            .permissions(
                                Manifest.permission.CAMERA,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            )
                            .request()
                    } else {
                        activity.onPermissionGranted()
                    }
                    activity.hideLoading()
                }
            }
        }
    }

    fun initMenu() {
        val height = resources.displayMetrics.widthPixels
        val with = dp2px(this, 200.toFloat()).toInt()
        var screenPopupMenu =
            ScreenPopupWindow(this, R.layout.popupwindow_screen, with, height)
        btn_screen.setOnClickListener {
            screenPopupMenu.showAsDropDown(menu, menu.width, 0)
        }
    }

}
