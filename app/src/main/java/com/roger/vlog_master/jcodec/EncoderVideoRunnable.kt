package com.roger.vlog_master.jcodec

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.Log

import com.roger.vlog_master.utils.CameraUtils
import com.roger.vlog_master.utils.CameraUtils.Companion.PREVIEW_HEIGHT
import com.roger.vlog_master.utils.CameraUtils.Companion.PREVIEW_WIDTH

import java.io.IOException
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.Vector

class EncoderVideoRunnable(
    private val muxerRunnableRf: WeakReference<MediaMuxerUtils>
) : Runnable {
    var isPhoneHorizontal = false
    private var mVideoEncodec: MediaCodec? = null
    private var mColorFormat: Int = 0
    private var isExit = false
    private var isEncoderStart = false

    private val frameBytes: Vector<ByteArray>?
    private val mFrameData: ByteArray
    var isFrontCamera: Boolean = false
    private val prevPresentationTimes: Long = 0
    private var mFormat: MediaFormat? = null

    private// API>=21
    val isLollipop: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP

    private// API<=19
    val isKITKAT: Boolean
        get() = Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT

    private val ptsUs: Long
        get() {
            var result = System.nanoTime() / 1000
            if (result < prevPresentationTimes) {
                result = prevPresentationTimes - result + result
            }
            return result
        }

    init {
        frameBytes = Vector()
        mFrameData = ByteArray(CameraUtils.PREVIEW_WIDTH * CameraUtils.PREVIEW_HEIGHT * 3 / 2)
    }

    private fun initMediaFormat() {
        try {
            val mCodecInfo = selectSupportCodec(MIME_TYPE)
            if (mCodecInfo == null) {
                return
            }
            mColorFormat = selectSupportColorFormat(mCodecInfo, MIME_TYPE)
            // NV21->I420
            mVideoEncodec = MediaCodec.createByCodecName(mCodecInfo.name)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        if (!isPhoneHorizontal) {
            mFormat = MediaFormat.createVideoFormat(MIME_TYPE, CameraUtils.PREVIEW_HEIGHT, CameraUtils.PREVIEW_WIDTH)
        } else {
            mFormat = MediaFormat.createVideoFormat(MIME_TYPE, CameraUtils.PREVIEW_WIDTH, CameraUtils.PREVIEW_HEIGHT)
        }
        BIT_RATE = CameraUtils.PREVIEW_WIDTH * CameraUtils.PREVIEW_HEIGHT * 3 * 8 * FRAME_RATE / 256
        mFormat!!.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        mFormat!!.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
        mFormat!!.setInteger(MediaFormat.KEY_COLOR_FORMAT, mColorFormat) // 颜色格式
        mFormat!!.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, FRAME_INTERVAL)
    }

    private fun startCodec() {
        if (mFormat == null) {
            initMediaFormat()
        }
        frameBytes!!.clear()
        isExit = false
        if (mVideoEncodec != null) {
            mVideoEncodec!!.configure(mFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mVideoEncodec!!.start()
            isEncoderStart = true
        }
    }

    private fun stopCodec() {
        if (mVideoEncodec != null) {
            mVideoEncodec!!.stop()
            mVideoEncodec!!.release()
            mVideoEncodec = null
            isEncoderStart = false
        }
    }

    fun addData(yuvData: ByteArray) {
        var data = yuvData
        if (!isPhoneHorizontal) {
            data = EncoderVideoRunnable.rotateYUV420Degree90(yuvData, PREVIEW_WIDTH, PREVIEW_HEIGHT)
        }
        frameBytes?.add(data)
    }

    override fun run() {
        if (!isEncoderStart) {
            try {
                Thread.sleep(200)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

            startCodec()
        }
        while (!isExit) {
            if (!frameBytes!!.isEmpty()) {
                val bytes = frameBytes.removeAt(0)
                try {
                    encoderBytes(bytes)
                } catch (e: IllegalStateException) {
                    e.printStackTrace()
                } catch (e: NullPointerException) {
                    e.printStackTrace()
                }

            }
        }
        stopCodec()
    }

    @SuppressLint("NewApi", "WrongConstant")
    private fun encoderBytes(rawFrame: ByteArray) {
        val inputBuffers = mVideoEncodec!!.inputBuffers
        var outputBuffers = mVideoEncodec!!.outputBuffers
        val mWidth = CameraUtils.PREVIEW_WIDTH
        val mHeight = CameraUtils.PREVIEW_HEIGHT
        NV21toI420SemiPlanar(rawFrame, mFrameData, mWidth, mHeight)
        val inputBufferIndex = mVideoEncodec!!.dequeueInputBuffer(TIMES_OUT.toLong())
        if (inputBufferIndex >= 0) {
            var inputBuffer: ByteBuffer? = null
            if (!isLollipop) {
                inputBuffer = inputBuffers[inputBufferIndex]
            } else {
                inputBuffer = mVideoEncodec!!.getInputBuffer(inputBufferIndex)
            }
            inputBuffer!!.clear()
            inputBuffer.put(mFrameData)
            mVideoEncodec!!.queueInputBuffer(inputBufferIndex, 0, mFrameData.size, ptsUs, 0)
        }

        val mBufferInfo = MediaCodec.BufferInfo()
        var outputBufferIndex = -1
        do {
            outputBufferIndex = mVideoEncodec!!.dequeueOutputBuffer(mBufferInfo, TIMES_OUT.toLong())
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                if (!isLollipop) {
                    outputBuffers = mVideoEncodec!!.outputBuffers
                }
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val mMuxerUtils = muxerRunnableRf.get()
                mMuxerUtils?.setMediaFormat()
            } else {
                var outputBuffer: ByteBuffer? = null
                if (!isLollipop) {
                    outputBuffer = outputBuffers[outputBufferIndex]
                } else {
                    outputBuffer = mVideoEncodec!!.getOutputBuffer(outputBufferIndex)
                }
                if (isKITKAT) {
                    outputBuffer!!.position(mBufferInfo.offset)
                    outputBuffer.limit(mBufferInfo.offset + mBufferInfo.size)
                }

                val mMuxerUtils = muxerRunnableRf.get()
                mMuxerUtils?.addMuxerData(
                    MediaMuxerUtils.MuxerData(
                        MediaMuxerUtils.TRACK_VIDEO, clone(outputBuffer),
                        mBufferInfo
                    )
                )

                mVideoEncodec!!.releaseOutputBuffer(outputBufferIndex, false)
            }
        } while (outputBufferIndex >= 0)
    }

    fun clone(original: ByteBuffer): ByteBuffer {
        val clone = ByteBuffer.allocate(original.capacity())
        original.rewind()//copy from the beginning
        clone.put(original)
        original.rewind()
        clone.flip()
        original.get()
        return clone
    }

    fun exit() {
        isExit = true
    }

    private fun selectSupportCodec(mimeType: String): MediaCodecInfo? {
        val numCodecs = MediaCodecList.getCodecCount()
        for (i in 0 until numCodecs) {
            val codecInfo = MediaCodecList.getCodecInfoAt(i)
            if (!codecInfo.isEncoder) {
                continue
            }
            val types = codecInfo.supportedTypes
            for (j in types.indices) {
                if (types[j].equals(mimeType, ignoreCase = true)) {
                    return codecInfo
                }
            }
        }
        return null
    }

    private fun selectSupportColorFormat(mCodecInfo: MediaCodecInfo, mimeType: String): Int {
        val capabilities = mCodecInfo.getCapabilitiesForType(mimeType)
        for (i in capabilities.colorFormats.indices) {
            val colorFormat = capabilities.colorFormats[i]
            if (isCodecRecognizedFormat(colorFormat)) {
                return colorFormat
            }
        }
        return 0
    }

    private fun isCodecRecognizedFormat(colorFormat: Int): Boolean {
        when (colorFormat) {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar, MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar -> return true
            else -> return false
        }
    }

    companion object {
        private val TAG = "EncoderVideoRunnable"
        private val MIME_TYPE = "video/avc"
        private val FRAME_RATE = 6
        private val FRAME_INTERVAL = 1
        private val TIMES_OUT = 10000
        private var BIT_RATE = CameraUtils.PREVIEW_WIDTH * CameraUtils.PREVIEW_HEIGHT * 3 * 8 * FRAME_RATE / 256

        fun NV21toI420SemiPlanar(
            nv21bytes: ByteArray, i420bytes: ByteArray,
            width: Int, height: Int
        ) {
            System.arraycopy(nv21bytes, 0, i420bytes, 0, width * height)
            var i = width * height
            while (i < nv21bytes.size) {
                i420bytes[i] = nv21bytes[i + 1]
                i420bytes[i + 1] = nv21bytes[i]
                i += 2
            }
        }

        fun rotateYUV420Degree90(data: ByteArray, imageWidth: Int, imageHeight: Int): ByteArray {
            val yuv = ByteArray(imageWidth * imageHeight * 3 / 2)
            var i = 0
            for (x in 0 until imageWidth) {
                for (y in imageHeight - 1 downTo 0) {
                    yuv[i] = data[y * imageWidth + x]
                    i++
                }
            }
            i = imageWidth * imageHeight * 3 / 2 - 1
            var x = imageWidth - 1
            while (x > 0) {
                for (y in 0 until imageHeight / 2) {
                    yuv[i] = data[imageWidth * imageHeight + y * imageWidth + x]
                    i--
                    yuv[i] = data[imageWidth * imageHeight + y * imageWidth
                            + (x - 1)]
                    i--
                }
                x = x - 2
            }
            return yuv
        }
    }
}
