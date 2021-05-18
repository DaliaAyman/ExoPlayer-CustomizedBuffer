package com.example.exoplayer_customizedbuffer.exoplayer

import android.util.Log
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy

class CustomLoadErrorHandlingPolicy :
    LoadErrorHandlingPolicy by DefaultLoadErrorHandlingPolicy() {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val exception = loadErrorInfo.exception
        Log.e("Player", "getRetryDelayMsFor $exception")
        if (exception is HttpDataSource.InvalidResponseCodeException) {
            val responseCode = exception.responseCode
            return if (responseCode in 500..599) RETRY_DELAY else C.TIME_UNSET
        }
        else if(exception is HttpDataSource.HttpDataSourceException){
            return 2000
        }
        return C.TIME_UNSET
    }

    override fun getMinimumLoadableRetryCount(dataType: Int): Int {
        return MINIMUM_RETRY_COUNT
    }

    companion object {
        private const val RETRY_DELAY = 5000L
        private const val MINIMUM_RETRY_COUNT = 3
    }
}
