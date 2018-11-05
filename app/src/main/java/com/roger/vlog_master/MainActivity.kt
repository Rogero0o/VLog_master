package com.roger.vlog_master

import android.Manifest
import android.content.DialogInterface
import android.hardware.Camera
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.view.SurfaceHolder
import com.roger.vlog_master.utils.CameraUtils
import com.roger.vlog_master.utils.REQUEST_CAMERA_PERMISSION_CODE
import kotlinx.android.synthetic.main.activity_main.*
import kr.co.namee.permissiongen.PermissionFail
import kr.co.namee.permissiongen.PermissionGen
import kr.co.namee.permissiongen.PermissionSuccess

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback, CameraUtils.OnPreviewFrameResult, CameraUtils.OnCameraFocusResult {


    private lateinit var cameraUtils: CameraUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cameraUtils = CameraUtils.getCamManagerInstance(this@MainActivity)

        record_surface.holder.addCallback(this)
        record_surface.setOnClickListener {
            cameraUtils.cameraFocus(this)
        }

    }

    private fun init(){

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        PermissionGen.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
    }

    @PermissionSuccess(requestCode = REQUEST_CAMERA_PERMISSION_CODE)
    fun onPermissionGranted() {
        init()
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

}
