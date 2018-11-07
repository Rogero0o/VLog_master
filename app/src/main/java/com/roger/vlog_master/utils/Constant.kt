package com.roger.vlog_master.utils

import android.os.Environment
import java.io.File

const val LOG_TAG = "VLog_MASTER"
const val REQUEST_CAMERA_PERMISSION_CODE = 100
const val MIN_FILE_SIZE = 100
const val HANDLER_SHOOT_WHAT = 1
const val HANDLER_PREVIEW_WHAT = 2
const val HANDLER_SHOOT_DELAY = 1000L
var FILE_FOLDER = Environment.getExternalStorageDirectory()
    .absolutePath + File.separator + "VLog"