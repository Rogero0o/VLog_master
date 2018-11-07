@file:Suppress("DEPRECATION")

package com.roger.vlog_master

import android.Manifest
import android.hardware.Camera
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.SurfaceHolder
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

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback, CameraUtils.OnPreviewFrameResult,
    CameraUtils.OnCameraFocusResult {


    private lateinit var cameraUtils: CameraUtils
    private val delayHandler = DelayHandler(this)
    private var shouldCatchPreview: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initView()
        cameraUtils = CameraUtils.getCamManagerInstance(this@MainActivity)
        record_surface.holder.addCallback(this)
        record_surface.setOnClickListener {
            cameraUtils.cameraFocus(this)
        }

    }

    private fun initView() {
        shoot_refresh_view.setOnClickListener {
            if (shoot_refresh_view.isStarted()) {
                shoot_refresh_view.reset()
                finishShootAndMakeFile()
            } else {
                shoot_refresh_view.start()
                beginShoot()
            }
        }
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

    override fun onFocusResult(result: Boolean) {
    }

    override fun onPreviewResult(data: ByteArray, camera: Camera) {
        cameraUtils.cameraInstance.addCallbackBuffer(data)
        if (shouldCatchPreview) {
            Log.d(LOG_TAG, "---PreviewCatch---:$data")
            MediaMuxerUtils.muxerRunnableInstance.addVideoFrameData(data)
            shouldCatchPreview = false
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        cameraUtils.surfaceHolder = holder
        PermissionGen.with(this@MainActivity)
            .addRequestCode(REQUEST_CAMERA_PERMISSION_CODE)
            .permissions(
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            .request()
    }

    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        cameraUtils.stopPreivew()
        cameraUtils.destroyCamera()
    }

    private class DelayHandler(activity: MainActivity) : Handler() {
        private val mActivity: WeakReference<MainActivity> = WeakReference(activity)

        override fun handleMessage(msg: Message) {
            if (msg.what == HANDLER_SHOOT_WHAT) {
                val activity = mActivity.get()
                if (activity != null) {
                    activity.shouldCatchPreview = true
                    activity.continueShoot()
                }
            }
        }
    }

}
