package com.roger.vlog_master.utils

object TimeUtils {
    fun secToTime(time: Long): String {
        var timeStr: String?
        var hour: Long
        var minute: Long
        var second: Long
        if (time <= 0)
            return "00:00"
        else {
            minute = time / 60
            if (minute < 60) {
                second = time % 60
                timeStr = unitFormat(minute) + ":" + unitFormat(second)
            } else {
                hour = minute / 60
                if (hour > 99)
                    return "99:59:59"
                minute %= 60
                second = time - hour * 3600 - minute * 60
                timeStr = unitFormat(hour) + ":" + unitFormat(minute) + ":" + unitFormat(second)
            }
        }
        return timeStr
    }

    private fun unitFormat(i: Long): String {
        return if (i in 0..9)
            "0" + i.toString()
        else
            "" + i
    }
}