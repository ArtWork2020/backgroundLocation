package com.google.android.gms.location.sample.location

import android.util.Log

object SampleLogUtil {
    // **********************************************************************
    // 定数
    // **********************************************************************
    private const val TAG = "After"

    // **********************************************************************
    // メンバ
    // **********************************************************************
    private var mIsShowLog = false

    // **********************************************************************
    // パブリックメソッド
    // **********************************************************************
    fun setShowLog(isShowLog: Boolean) {
        mIsShowLog = isShowLog
    }

    fun logDebug() {
        outputLog(Log.DEBUG, null, null)
    }

    fun logDebug(message: String?) {
        outputLog(Log.DEBUG, message, null)
    }

    fun logDebug(message: String?, throwable: Throwable?) {
        outputLog(Log.DEBUG, message, throwable)
    }

    // **********************************************************************
    // プライベートメソッド
    // **********************************************************************
    private fun outputLog(type: Int, message: String?, throwable: Throwable?) {
        var message = message
        if (!mIsShowLog) {
            // ログ出力フラグが立っていない場合は何もしません。
            return
        }

        // ログのメッセージ部分にスタックトレース情報を付加します。
        message = if (message == null) {
            stackTraceInfo
        } else {
            stackTraceInfo + message
        }
        when (type) {
            Log.DEBUG -> if (throwable == null) {
                Log.d(TAG, message)
            } else {
                Log.d(TAG, message, throwable)
            }
        }
    }// 現在のスタックトレースを取得。
    // 0:VM 1:スレッド 2:getStackTraceInfo() 3:outputLog() 4:logDebug()等 5:呼び出し元
    /**
     * スタックトレースから呼び出し元の基本情報を取得。
     *
     * @return <<className></className>#methodName:lineNumber>>
     */
    private val stackTraceInfo: String
        private get() {
            // 現在のスタックトレースを取得。
            // 0:VM 1:スレッド 2:getStackTraceInfo() 3:outputLog() 4:logDebug()等 5:呼び出し元
            val element = Thread.currentThread().stackTrace[5]
            val fullName = element.className
            val className = fullName.substring(fullName.lastIndexOf(".") + 1)
            val methodName = element.methodName
            val lineNumber = element.lineNumber
            return "$className#$methodName:$lineNumber "
        }
}