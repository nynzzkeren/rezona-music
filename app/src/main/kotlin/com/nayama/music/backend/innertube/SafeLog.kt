package com.nayama.music.backend.innertube

import android.util.Log

internal object SafeLog {
    fun d(tag: String, msg: String, tr: Throwable? = null) {
        try {
            if (tr != null) Log.d(tag, msg, tr) else Log.d(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
            tr?.printStackTrace()
        }
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        try {
            if (tr != null) Log.w(tag, msg, tr) else Log.w(tag, msg)
        } catch (_: Throwable) {
            System.err.println("[$tag] WARN: $msg")
            tr?.printStackTrace()
        }
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        try {
            if (tr != null) Log.e(tag, msg, tr) else Log.e(tag, msg)
        } catch (_: Throwable) {
            System.err.println("[$tag] ERROR: $msg")
            tr?.printStackTrace()
        }
    }
}
