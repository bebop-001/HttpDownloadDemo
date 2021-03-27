/*
 *  Copyright (C) 2021 kana-tutor.com
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.kana_tutor.utils

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.kana_tutor.httpdownloaddemo.HOME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.withContext
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

private const val TAG = "FileUtils"
const val BUFFER_SIZE = 0x1000

private fun Application.createDirsAsNeeded(fileName:String) : Pair<Boolean, String?> {
    val (_, dirPart, _ /*fileNamePart*/) = "^(.*/)*(.*)".toRegex()
        .find(fileName)!!.groupValues
    if (!File(filesDir, dirPart).exists()) {
        val dirs = dirPart.split("/+".toRegex()).toMutableList()
        var d = ""
        while (dirs.isNotEmpty()) {
            d += dirs.removeAt(0) + "/"
            val f = File(filesDir, d)
            if (!f.exists()) {
                try {f.mkdir()}
                catch (e:Exception) {
                    return false to
                            "Failed to create directory:$d\n" +
                            e.toString()
                }
            }
        }
    }
    return true to ""
}

private var _copyUrlProgress = MutableLiveData(0)
val copyUrlProgress : LiveData<Int>
    get() = _copyUrlProgress

suspend fun Application.copyUrlToLocalFile(
    sourceUrl: String,
    destFileName: String,
    calcMD5: Boolean = true,
    force:Boolean = true,
) : Pair<Boolean, String?> {
    if (File(filesDir, destFileName).exists() && !force) {
        return Pair(false,
            """copyFileFromAssets:destFileName file $destFileName exists.
            |Please set \"force\" to overwrite.
        """.trimMargin())
    }
    val dirsResult = createDirsAsNeeded(destFileName)
    if (!dirsResult.first)
        return dirsResult
    try {
        val digest =
            if(calcMD5) MessageDigest.getInstance("MD5")
            else null
        val url = URL(sourceUrl)
        val connection: HttpURLConnection =
            url.openConnection() as HttpURLConnection
        connection.connectTimeout = 3000 //<- 3 Seconds Timeout
        connection.connect()
        val downloadBytes = connection.contentLength
        Log.d(TAG, "download response code: ${connection.responseMessage}")
        Log.d("TAG", "copyUrlToLocalFile:source:$url, dest:$destFileName, bytes:")
        val source: InputStream = url.openStream()
        val dest: OutputStream = File(filesDir, destFileName).outputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead:Int
        var destSize = 0
        val startTime = System.currentTimeMillis()
        var currentProgress = -1
        do {
            bytesRead = source.read(buffer)
            if (bytesRead < 0) break
            dest.write(buffer, 0, bytesRead)
            digest?.update(buffer, 0, bytesRead)
            destSize += bytesRead
            val progress = ((destSize / downloadBytes.toFloat()) * 100).toInt()
            if (progress != _copyUrlProgress.value) {
                withContext(Dispatchers.Main) {
                    _copyUrlProgress.value = progress
                }
            }
        } while (bytesRead > 0)
        dest.flush()
        dest.close()
        source.close()
        // if caller wants md5 sum, he gets that.  Otherwise
        // return info about the download.
        val resultString =
            if (calcMD5) digest!!.digest()
                .joinToString("") { "%02x".format(it) }
            else "$url -> $destFileName, $dest size Bytes, %d seconds"
                .format((
                        (System.currentTimeMillis() - startTime)/1000).toInt()
                )
        return Pair(true, resultString)
    }
    catch (e: Exception){
        return Pair(false, "exception during copy: $e")
    }
}

fun Application.copyFromAssetsToLocalFile(
    assetsFile:String, destFileName:String, force:Boolean = false
) : Pair<Boolean, String?> {
    if (File(filesDir, destFileName).exists() && !force) {
        return Pair(false,
            """copyFileFromAssets:destination file $destFileName exists.
            |Please set \"force\" to overwrite.
        """.trimMargin())
    }
    val dirsResult = createDirsAsNeeded(destFileName)
    if (!dirsResult.first)
        return dirsResult
    val source: InputStream = assets.open(assetsFile)
    val dest: OutputStream = FileOutputStream(File(filesDir, destFileName))
    val buffer = ByteArray(BUFFER_SIZE)
    var destSize = 0L
    var bytesRead:Int
    do {
        bytesRead = source.read(buffer)
        if (bytesRead < 0) break
        dest.write(buffer, 0, bytesRead)
        destSize += bytesRead
    } while (bytesRead > 0)
    dest.flush()
    dest.close()
    source.close()
    return true to "wrote $destSize bytes."
}

fun unzipFile(
    zippedFile:String,
    unzipDirname: String
): Pair<Boolean, String?> {
    var zippedBytes: Long = -1
    var unZippedBytes: Long = 0
    val buffer = ByteArray(0x400)
    try {
        val f = File(zippedFile)
        if (!f.exists()) throw RuntimeException(
            "Zip in file " + zippedFile
                    + " doesn't exist."
        )
        val unzipDir = File(HOME, unzipDirname)
        if (!unzipDir.exists()) {
            if (!unzipDir.mkdir()) {
                return false to "Failed to create unzipDir:$unzipDir"
            }
        }
        else if (!unzipDir.isDirectory) {
            return false to """unzip to directory:$unzipDir: FAILED
                $unzipDir exists and is not a directory.""".trimIndent()
        }
        zippedBytes = f.length()
        val fileIn = FileInputStream(zippedFile)
        val zipIn = ZipInputStream(BufferedInputStream(fileIn))
        var ze: ZipEntry
        while (zipIn.nextEntry.also { ze = it } != null) {
            val zippedFileName = ze.name
            if (ze.isDirectory) {
                val newDir = File(unzipDirname + zippedFileName)
                if (!newDir.mkdirs())
                    return(false to "mkdirs $newDir FAILED")
                continue
            }
            val zipOut = FileOutputStream(unzipDirname + zippedFileName)
            var count: Int
            while (zipIn.read(buffer).also { count = it } > 0) {
                zipOut.write(buffer, 0, count)
                unZippedBytes += count.toLong()
            }
            zipOut.close()
            zipIn.closeEntry()
        }
        zipIn.close()
    }
    catch (e: Exception) {
        return false to """
                Error unzipping $zippedFile.
                error = "${e.message}"
                """.trimIndent()
    }
    return true to """Successfully unziped $zippedFile to directory
        unzipped length:$zippedBytes, bytes unzipped:$unZippedBytes
        """.trimIndent()
}

fun Application.gunzip (
    zippedFile: String,
    unZippedFile: String
): Pair<Boolean, String?> {
    var zippedBytes: Long
    var unZippedBytes: Long = 0

    val buffer = ByteArray(0x400)
    try {
        val f = File(zippedFile)
        if (!f.exists())
            return false to "Zip in file $zippedFile doesn't exist."
        zippedBytes = f.length()
        val gzipIn = GZIPInputStream(FileInputStream(zippedFile))
        val gzipOut = FileOutputStream(unZippedFile)
        var count: Int
        while (gzipIn.read(buffer).also { count = it } > 0) {
            gzipOut.write(buffer, 0, count)
            unZippedBytes += count.toLong()
        }
        gzipOut.close()
    }
    catch (e: Exception) {
        return false to """
                Error unzipping $zippedFile.
                error = "${e.message}"
                """.trimIndent()
    }
    return true to """file $zippedFile unzipped to ${unZippedFile}:
        $zippedBytes bytes tp $unZippedBytes""".trimIndent()
}

