@file:Suppress("PrivatePropertyName", "PrivatePropertyName")

package com.kana_tutor.httpdownloaddemo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Observer
import com.kana_tutor.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private lateinit var downloadUrl:EditText
    private lateinit var md5Checksum:EditText
    private lateinit var destinationFilename:AutoCompleteTextView
    private lateinit var startDownload:Button
    private lateinit var downloadProgress:ProgressBar
    private lateinit var overwriteCurrentFile: CheckedTextView

    private fun hideKeyboard() {
        val im = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val currentWindow: View? = currentFocus
        if (currentWindow != null) {
            im.hideSoftInputFromWindow(
                currentWindow.windowToken, 0
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        downloadUrl = findViewById(R.id.download_url_ET)
        md5Checksum = findViewById(R.id.md5_ET)
        destinationFilename = findViewById(R.id.destination_AC)
        startDownload = findViewById(R.id.start_download_BTN)
        downloadProgress = findViewById(R.id.download_PB)
        overwriteCurrentFile = findViewById(R.id.overwrite_file_CK)

        fun enableDownloadButton() {
            startDownload.isEnabled =
                downloadUrl.text.isNotEmpty() && destinationFilename.text.isNotEmpty()
        }
        overwriteCurrentFile.setOnClickListener{
            val cb = it as CheckedTextView
            cb.isChecked = !cb.isChecked
        }

        enableDownloadButton()
        downloadUrl.doAfterTextChanged {
            enableDownloadButton()
        }
        destinationFilename.doAfterTextChanged {
            enableDownloadButton()
        }
        startDownload.setOnClickListener {
            hideKeyboard()
            GlobalScope.launch {
                val sourceUrl = downloadUrl.text.toString()
                val destFile = destinationFilename.text.toString()
                Log.d(TAG, "onResume")
                NetworkMonitor.getInstance(application).start()
                GlobalScope.launch {
                    withContext(Dispatchers.Main){startDownload.isEnabled = false}
                    var copyFileResult = application.isUrlReachable(sourceUrl)
                    Log.d(TAG, "isServerReachable:$copyFileResult")
                    if (copyFileResult.first) {
                        copyFileResult = application.copyUrlToLocalFile(
                            sourceUrl, destFile,
                            calcMD5 = md5Checksum.text.isNotEmpty(),
                            force = overwriteCurrentFile.isChecked,
                        )
                    }
                    // show a result dialog.
                    withContext(Dispatchers.Main) {
                        val dialog = AlertDialog.Builder(this@MainActivity)
                            .setMessage(
                            "Download complete:\n${
                                if (copyFileResult.first) {
                                    val md5Sum = copyFileResult.second!!
                                    if (md5Checksum.text.isNotEmpty())
                                        if (md5Checksum.text.toString() == md5Sum)
                                            "    Download success.  Checksum passed."
                                        else "    Download success.  Checksum FAILED."
                                    else "    Download success.  Checksum not tested."
                                }
                                else "Failed:${copyFileResult.second}"
                            }")
                            .setCancelable(true)
                            .setPositiveButton(android.R.string.ok) {dialog, _ ->
                                dialog.cancel()
                            }
                            .create()
                        dialog.show()
                    }
                    Log.d(TAG, "$copyFileResult")
                    withContext(Dispatchers.Main){startDownload.isEnabled = true}
                }
                Log.d(TAG, "outside coroutine")
            }
        }
    }
    override fun onResume() {
        super.onResume()
        copyUrlProgress.observe(
            this,
            Observer { progress ->
                downloadProgress.progress = progress
            }
        )
        startDownload.isEnabled = downloadUrl.text.isNotEmpty()
    }
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        try { NetworkMonitor.getInstance(application).stop() }
        catch (e:Exception) {
            Log.e(TAG, "onPause:Exception:${e.message}")
        }
    }
}