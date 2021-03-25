@file:Suppress("LocalVariableName", "unused")
// This was for the class not being used by Lint.  Actually,
// it is being used.

package com.kana_tutor.utils

import android.app.Activity
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import java.io.File

// will need import android:windowSoftInputMode="stateAlwaysHidden|adjustResize
// added to activity in AndroidManifest.xml
fun Activity.getFilePickView(resId:Int):AutoCompleteTextView {
    @Suppress("LocalVariableName")
    val TAG = "getFilePickView"
    fun hideKeyboard() {
        val im = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        im.hideSoftInputFromWindow(this.currentFocus!!.windowToken, 0)
        Log.d(TAG, "hideKeyboard")
    }
    // custom comparator for files.  Directories first then non-directories.
    val fileComparator = Comparator{ left:String, right:String ->
        if (left.endsWith("/"))
            if (right.endsWith("/"))
                left.compareTo(right)
            else -1
        else if (right.endsWith("/")) 1
        else left.compareTo(right)
    }
    val rootDir = File(filesDir, "../")
    fun findFiles() : Array<String> {
        val files = mutableListOf<String>()
        fun find(dir:String) {
            val d = File(rootDir, dir)
            val l = d.list()
            val dirs = mutableListOf<String>()
            l.map {
                val x = if (dir.isNotEmpty()) "$dir/$it" else it
                if(File(rootDir, x).isDirectory) {
                    dirs.add(x)
                    files.add("$x/")
                }
                else files.add(x)
            }
            dirs.map{find(it)}
        }
        find("")
        return files.sortedWith(fileComparator).toTypedArray()
    }
    val autoComplete = findViewById<AutoCompleteTextView>(resId)
    val ad = ArrayAdapter(this,
        android.R.layout.simple_dropdown_item_1line,
        findFiles()
    )
    autoComplete.setAdapter(ad)
    autoComplete.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
        val af = v as AutoCompleteTextView
        if (hasFocus) af.showDropDown()
        Log.d("onFocusChange", "hasFocus = $hasFocus")
    }
    (autoComplete as EditText).doAfterTextChanged { text ->
        Log.d(TAG, "doAfterTextChanged:$text")
        if (text!!.isEmpty() && autoComplete.hasFocus()) autoComplete.showDropDown()
    }
    (autoComplete as EditText).setOnClickListener {
        val et = it!! as EditText
        Log.d(TAG, "onClickListener:${et.text}")
        autoComplete.showDropDown()
    }
    autoComplete.setOnItemClickListener { parent, view, position, id ->
        hideKeyboard()
    }
    return autoComplete
}