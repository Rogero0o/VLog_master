package com.roger.vlog_master.utils

import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object FileUtils{
    fun getFile(): File {
        val fileName = "VLog_"+getNowDateTime() + ".mp4"
        val file = File(FILE_FOLDER, fileName)

        if (!file.parentFile.exists()) {
            file.parentFile.mkdirs()
        }

        if (!file.exists()) {
            file.createNewFile()
        }

        return file
    }

    private fun getNowDateTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss",Locale.getDefault())
        return sdf.format(Date())
    }
}