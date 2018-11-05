package com.roger.vlog_master.utils

import android.app.Activity
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import android.hardware.Camera.PreviewCallback
import android.hardware.Camera.Size
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import java.io.IOException
import java.lang.ref.WeakReference

class CameraUtils private constructor() {
    private lateinit var cameraInstance: Camera
    private var cameraDirection = false
    private var mPreviewListener: OnPreviewFrameResult? = null
    private var mHolderRef: WeakReference<SurfaceHolder>? = null

    private val previewCallback =
        PreviewCallback { data, camera -> mPreviewListener!!.onPreviewResult(data, camera) }

    private val previewRotateDegree: Int
        get() {
            var phoneDegree = 0
            var result = 0
            val phoneRotate = (mContext as Activity).windowManager.defaultDisplay.orientation
            when (phoneRotate) {
                Surface.ROTATION_0 -> phoneDegree = 0
                Surface.ROTATION_90 -> phoneDegree = 90
                Surface.ROTATION_180 -> phoneDegree = 180
                Surface.ROTATION_270 -> phoneDegree = 270
            }
            val cameraInfo = CameraInfo()
            if (cameraDirection) {
                Camera.getCameraInfo(CameraInfo.CAMERA_FACING_FRONT, cameraInfo)
                result = (cameraInfo.orientation + phoneDegree) % 360
                result = (360 - result) % 360
            } else {
                Camera.getCameraInfo(CameraInfo.CAMERA_FACING_BACK, cameraInfo)
                result = (cameraInfo.orientation - phoneDegree + 360) % 360
            }
            return result
        }

    val previewFormat: Int
        get() = if (!::cameraInstance.isInitialized) {
            -1
        } else cameraInstance.parameters.previewFormat

    var surfaceHolder: SurfaceHolder?
        get() = if (mHolderRef == null) {
            null
        } else mHolderRef!!.get()
        set(mSurfaceHolder) {
            if (mHolderRef != null) {
                mHolderRef!!.clear()
                mHolderRef = null
            }
            mHolderRef = WeakReference<SurfaceHolder>(mSurfaceHolder)
        }

    interface OnPreviewFrameResult {
        fun onPreviewResult(data: ByteArray, camera: Camera)
    }

    interface OnCameraFocusResult {
        fun onFocusResult(result: Boolean)
    }

    fun setOnPreviewResult(mPreviewListener: OnPreviewFrameResult) {
        this.mPreviewListener = mPreviewListener
    }

    fun startPreview() {
        if (!::cameraInstance.isInitialized) {
            return
        }
        try {
            Log.i(TAG, "CameraManager-->begin preview")
            cameraInstance.setPreviewDisplay(mHolderRef!!.get())
        } catch (e: IOException) {
            e.printStackTrace()
        }

        //begin preview Camera
        try {
            cameraInstance.startPreview()
        } catch (e: RuntimeException) {
            Log.i(TAG, "begin preview Camera fail，reopen Camera.")
            stopPreivew()
            destroyCamera()
            createCamera()
            startPreview()
        }

        //autoFocus
        cameraInstance.autoFocus(null)
        val previewFormat = cameraInstance.parameters.previewFormat
        val previewSize = cameraInstance.parameters.previewSize
        val size = previewSize.width * previewSize.height * ImageFormat.getBitsPerPixel(previewFormat) / 8
        cameraInstance.addCallbackBuffer(ByteArray(size))
        cameraInstance.setPreviewCallbackWithBuffer(previewCallback)
    }

    fun stopPreivew() {
        if (!::cameraInstance.isInitialized) {
            return
        }
        try {
            cameraInstance.setPreviewDisplay(null)
            cameraInstance.setPreviewCallbackWithBuffer(null)
            cameraInstance.stopPreview()
            Log.i(TAG, "CameraManager-->stop camera preview")
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    fun createCamera() {
        openCamera()
        setCamParameters()
    }

    fun openCamera() {
        if (::cameraInstance.isInitialized) {
            stopPreivew()
            destroyCamera()
        }
        //open font camera
        if (cameraDirection) {
            val cameraInfo = CameraInfo()
            val camNums = Camera.getNumberOfCameras()
            for (i in 0 until camNums) {
                Camera.getCameraInfo(i, cameraInfo)
                if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
                    try {
                        cameraInstance = Camera.open(i)
                        Log.i(TAG, "CameraManager-->create Camera, open font camera")
                        break
                    } catch (e: Exception) {
                        Log.d(TAG, "open fail：" + e.message)
                    }

                }
            }
        } else {
            try {
                cameraInstance = Camera.open()
                Log.i(TAG, "CameraManager-->create Camera, open back camera")
            } catch (e: Exception) {
                Log.d(TAG, "open fail：" + e.message)
            }

        }
    }

    fun destroyCamera() {
        cameraInstance.release()
        Log.i(TAG, "CameraManager-->release camera")
    }

    private fun setCamParameters() {
        if (!::cameraInstance.isInitialized)
            return
        val params = cameraInstance.parameters
        if (isUsingYv12) {
            params.previewFormat = ImageFormat.YV12
        } else {
            params.previewFormat = ImageFormat.NV21
        }
        val focusModes = params.supportedFocusModes
        if (isSupportFocusAuto(focusModes)) {
            params.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
        }
        val previewSizes = params.supportedPreviewSizes
        if (!isSupportPreviewSize(previewSizes)) {
            PREVIEW_WIDTH = previewSizes[0].width
            PREVIEW_HEIGHT = previewSizes[0].height
        }
        params.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT)
        val max = determineMaximumSupportedFramerate(params)
        params.setPreviewFpsRange(max[0], max[1])
        cameraInstance.parameters = params
        val rotateDegree = previewRotateDegree
        cameraInstance.setDisplayOrientation(rotateDegree)
    }

    fun cameraFocus(listener: OnCameraFocusResult?) {
        if (::cameraInstance.isInitialized) {
            cameraInstance.autoFocus { success, camera ->
                listener?.onFocusResult(success)
            }
        }
    }

    private fun isSupportFocusAuto(focusModes: List<String>): Boolean {
        var isSupport = false
        for (mode in focusModes) {
            if (mode == Camera.Parameters.FLASH_MODE_AUTO) {
                isSupport = true
                break
            }
        }
        return isSupport
    }

    private fun isSupportPreviewSize(previewSizes: List<Size>): Boolean {
        var isSupport = false
        for (size in previewSizes) {
            if (size.width == PREVIEW_WIDTH && size.height == PREVIEW_HEIGHT || size.width == PREVIEW_HEIGHT && size.height == PREVIEW_WIDTH) {
                isSupport = true
                break
            }
        }
        return isSupport
    }

    fun switchCamera() {
        cameraDirection = !cameraDirection
        createCamera()
        startPreview()
    }

    fun setPreviewSize(width: Int, height: Int) {
        PREVIEW_WIDTH = width
        PREVIEW_HEIGHT = height
    }

    companion object {
        private val TAG = "CameraManager"
        var PREVIEW_WIDTH = 1920
        var PREVIEW_HEIGHT = 1080
        var isUsingYv12 = false
        private var mContext: Context? = null


        private var mCameraManager: CameraUtils? = null

        fun getCamManagerInstance(mContext: Context): CameraUtils {
            CameraUtils.mContext = mContext
            if (mCameraManager == null) {
                mCameraManager = CameraUtils()
            }
            return mCameraManager!!
        }

        fun determineMaximumSupportedFramerate(parameters: Camera.Parameters): IntArray {
            var maxFps = intArrayOf(0, 0)
            val supportedFpsRanges = parameters.supportedPreviewFpsRange
            val it = supportedFpsRanges.iterator()
            while (it.hasNext()) {
                val interval = it.next()
                if (interval[1] > maxFps[1] || interval[0] > maxFps[0] && interval[1] == maxFps[1]) {
                    maxFps = interval
                }
            }
            return maxFps
        }
    }
}
