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
import android.os.CountDownTimer
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.Toast
import com.orhanobut.hawk.Hawk
import com.roger.shootrefreshview.DensityUtil.dp2px
import com.roger.vlog_master.menu.AboutPopupWindow
import com.roger.vlog_master.menu.ScreenPopupWindow
import com.roger.vlog_master.menu.TimeIntervalPopupWindow
import com.roger.vlog_master.menu.TimeLongPopupWindow


class MainActivity : AppCompatActivity(), SurfaceHolder.Callback, CameraUtils.OnPreviewFrameResult,
    CameraUtils.OnCameraFocusResult {


    private lateinit var cameraUtils: CameraUtils
    private val delayHandler = DelayHandler(this)
    private var shouldCatchPreview: Boolean = false
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initThirdPackage()
        initView()
        initMenu()
        updateVideoUIInfo(0L)
        cameraUtils = CameraUtils.getCamManagerInstance(this@MainActivity)
    }

    private fun initThirdPackage() {
        Hawk.init(this).build()
    }

    private fun initView() {
        record_surface.holder.addCallback(this)
        record_surface.setOnClickListener {
            cameraUtils.cameraFocus(this)
        }
        shoot_refresh_view.setOnClickListener {
            if (shoot_refresh_view.isStarted()) {
                finishShootAndMakeFile()
            } else {

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
        shoot_refresh_view.start()
        MediaMuxerUtils.muxerRunnableInstance.startMuxerThread(cameraUtils.cameraDirection, false)
        HANDLER_SHOOT_DELAY = Hawk.get(KEY_TIME_INTERVAL, HANDLER_SHOOT_DELAY)
        delayHandler.sendEmptyMessage(HANDLER_SHOOT_WHAT)
        val timeLong = Hawk.get<Float>(KEY_TIME_LONG, -1f)
        if (timeLong != -1f) {
            countDownTimer = object : CountDownTimer(timeLong.toLong(), 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    runOnUiThread {
                        updateVideoUIInfo(millisUntilFinished)
                    }
                    Log.i(LOG_TAG, "millisUntilFinished:$millisUntilFinished")
                }

                override fun onFinish() {
                    runOnUiThread {
                        finishShootAndMakeFile()
                    }
                }
            }.start()
        }
    }

    fun continueShoot() {
        delayHandler.sendEmptyMessageDelayed(HANDLER_SHOOT_WHAT, HANDLER_SHOOT_DELAY)
    }

    private fun finishShootAndMakeFile() {
        updateVideoUIInfo(0)
        countDownTimer?.cancel()
        shoot_refresh_view.reset()
        delayHandler.removeMessages(HANDLER_SHOOT_WHAT)
        SequenceEncoderMp4.instance?.setFrameNo(ListCache.getInstance(this@MainActivity).lastIndex.toInt())
        SequenceEncoderMp4.instance?.finish()
        val filePath = SequenceEncoderMp4.instance?.getFilePath()
        Toast.makeText(this,getString(R.string.video_save_success,SequenceEncoderMp4.instance?.getFilePath())
            ,Toast.LENGTH_LONG).show()
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

    private fun initMenu() {
        val height = resources.displayMetrics.heightPixels
        val with = dp2px(this, 200.toFloat()).toInt()
        val screenPopupMenu =
            ScreenPopupWindow(this, R.layout.popupwindow_screen, with, height)
        val timeIntervalPopupMenu =
            TimeIntervalPopupWindow(this, R.layout.popupwindow_timeinterval, with, height)
        val timeLongPopupMenu =
            TimeLongPopupWindow(this, R.layout.popupwindow_timelong, with, height)
        val aboutPopupMenu =
            AboutPopupWindow(this, R.layout.popupwindow_about, with, height)

        btn_screen.setOnClickListener {
            screenPopupMenu.showAsDropDown(menu, menu.width, 0)
        }
        btn_time.setOnClickListener {
            timeIntervalPopupMenu.showAsDropDown(menu, menu.width, 0)
        }
        btn_long.setOnClickListener {
            timeLongPopupMenu.showAsDropDown(menu, menu.width, 0)
        }
        btn_settings.setOnClickListener {
            aboutPopupMenu.showAsDropDown(menu, menu.width, 0)
        }
    }

    fun updateVideoUIInfo(millisUntilFinished: Long) {
        val intervalTime = Hawk.get<Float>(KEY_TIME_INTERVAL, 500f) / 1000f
        main_text_time_interval.text = getString(R.string.main_time_interval, intervalTime.toString())
        val recordingTime = (Hawk.get<Float>(KEY_TIME_LONG, -1f) - millisUntilFinished) / 1000f
        if (recordingTime > 0) {
            main_text_time_long.text =
                    getString(R.string.main_recording_duration, TimeUtils.secToTime(recordingTime.toLong()))
            val videoTime = recordingTime / (intervalTime * 6)
            main_text_video_long.text = getString(R.string.main_video_duration, TimeUtils.secToTime(videoTime.toLong()))
        } else {
            main_text_time_long.text = getString(R.string.main_recording_duration, getString(R.string.infinite))
            main_text_video_long.text = getString(R.string.main_video_duration, getString(R.string.infinite))
        }
    }

    fun rePreview(){
        cameraUtils.stopPreivew()
        cameraUtils.destroyCamera()
        cameraUtils.createCamera()
        cameraUtils.startPreview()
    }


}
