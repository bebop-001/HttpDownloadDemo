package com.kana_tutor.httpdownloaddemo
import android.app.Application
import com.kana_tutor.utils.NetworkMonitor
import java.io.File

lateinit var HOME : File
class HttpDownloadDemo : Application(){
    override fun onCreate() {
        super.onCreate()
        //Start network callback
        NetworkMonitor.getInstance(this).start()
        HOME = File(filesDir, "../")
    }

    override fun onTerminate(){
        super.onTerminate()
        //Stop network callback
        NetworkMonitor.getInstance(this).stop()
    }


}