/*
 *  Copyright 2021 Steven Smith kana-tutor.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *
 *  You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.
 *
 *  See the License for the specific language governing permissions
 *  and limitations under the License.
 */
@file:Suppress("BlockingMethodInNonBlockingContext")

package com.kana_tutor.utils

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkInfo
import android.net.NetworkRequest
import android.os.Build
import android.util.Log

import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "NetworkMonitor"

// from https://github.com/bebop-001/CheckNetworkConnectivity
class NetworkMonitor private constructor(private val application: Application) {
    private var networkCallback:ConnectivityManager.NetworkCallback? = null
    init {
        if (Build.VERSION.SDK_INT >= 21) {
            // install callback for newer versions of android.
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    networkIsConnected = true
                    Log.d(TAG, "networkCallback:onAvailable")
                }
                override fun onLost(network: Network) {
                    networkIsConnected = false
                    Log.d(TAG, "networkCallback:onLost")
                }
            }
        }
        else isConnected()
    }
    companion object {
        private var theInstance:NetworkMonitor? = null
        fun getInstance(application: Application) : NetworkMonitor {
            if (theInstance == null) {
                synchronized(NetworkMonitor::class) {
                    theInstance = NetworkMonitor(application)
                }
            }
            return theInstance!!
        }
    }
    private var networkIsConnected = false
    fun start() {
        if (Build.VERSION.SDK_INT >= 21) {
            val cm: ConnectivityManager =
                application.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as ConnectivityManager
            val builder: NetworkRequest.Builder = NetworkRequest.Builder()
            if (Build.VERSION.SDK_INT >= 24) cm.registerDefaultNetworkCallback(
                networkCallback!!
            )
            else cm.registerNetworkCallback(
                builder.build(),
                networkCallback!!
            )
        }
        Log.d(TAG, "start")
    }
    fun stop() {
        if (Build.VERSION.SDK_INT >= 21) {
            val cm: ConnectivityManager =
                application.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as ConnectivityManager
            cm.unregisterNetworkCallback(ConnectivityManager.NetworkCallback())
            Log.d(TAG, "Stop")
        }
    }
    // For older versions of Android, just call the network manager
    // to get status.  For newer versions, the OS callbacks set the
    // status and we just return it.
    @Suppress("DEPRECATION")
    fun isConnected(): Boolean {
        if (Build.VERSION.SDK_INT < 21) {
            val connectivityManager =
                application.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as ConnectivityManager
            val networkInfo: NetworkInfo? = connectivityManager.activeNetworkInfo
            networkIsConnected = networkInfo?.isConnected ?: false
        }
        Log.d(TAG, "isConnected:$networkIsConnected")
        return networkIsConnected
    }
}

// sort-a-kinda like ping.
fun Application.isUrlReachable(urlIn: String): Pair<Boolean, String?> {
    var rv : Pair<Boolean, String?>
    if (NetworkMonitor.getInstance(this).isConnected()) {
        try {
            val url = URL(urlIn)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 3000 //<- 3Seconds Timeout
            connection.connect()
            rv = Pair(
                connection.responseCode == 200,
                connection.responseMessage
            )
        }
        catch (e: Exception) {
            rv = false to "$urlIn:Connect failed:$e"
        }
    }
    else rv = false to "no network connection"
    return rv
}
