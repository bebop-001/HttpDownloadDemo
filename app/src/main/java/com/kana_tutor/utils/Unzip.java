/*
 *
 *    Copyright 2017 Steven Smith, sjs@kana-tutor.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing,
 *    software distributed under the License is distributed on an
 *    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *    either express or implied. See the License for the specific
 *    language governing permissions and limitations under the License.
 */

package com.kana_tutor.async_download_zip_eg;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class AsyncUnzip extends AsyncTask<String, String, Boolean> {
    public static final String TAG = "AsyncUnzip";
    private AsyncStatusInterface statusListener;
    private Context callerContext;
    private String fileBaseName         // used as title for the progress bar
            , errorString = "";
    private ProgressDialog mProgressDialog;
    public AsyncUnzip(Context c, String fileBaseName) {
        callerContext = c;
        this.fileBaseName = fileBaseName;
        this.statusListener = (AsyncStatusInterface)c;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        mProgressDialog = new ProgressDialog(callerContext);
        mProgressDialog.setMessage("Unzip " + fileBaseName);
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();
    }

    private String zippedFile, unZippedFile;
    private long zippedBytes = -1, unZippedBytes = 0;

    @Override
    protected Boolean doInBackground(String... params) {
        zippedFile = params[0]; unZippedFile = params[1];
        String   unzipDirname;
        boolean success = true;
        byte[] buffer = new byte[0x400];
        try  {
            File f = new File(zippedFile);
            if ( ! f.exists())
                throw new RuntimeException("Zip in file " + zippedFile
                    + " doesn't exist.");
            unzipDirname = zippedFile.substring(0, unZippedFile.lastIndexOf("/") + 1);
            zippedBytes = f.length();
            FileInputStream fileIn = new FileInputStream(zippedFile);
            BufferedInputStream bis = new BufferedInputStream(fileIn);
            ZipInputStream zipIn = new ZipInputStream(bis);
            ZipEntry ze;
            while((ze = zipIn.getNextEntry()) != null) {

                String zippedFileName = ze.getName();
                if (ze.isDirectory()) {
                    File newDir = new File(unzipDirname + zippedFileName);
                    if ( ! newDir.mkdirs())
                        throw new Exception("mkdirs " + newDir.toString() + " FAILED");
                    continue;
                }
                FileOutputStream zipOut = new FileOutputStream(unzipDirname + zippedFileName);
                int count;
                while ((count = zipIn.read(buffer)) > 0) {
                    zipOut.write(buffer, 0, count);
                    unZippedBytes += count;
                }
                zipOut.close();
                zipIn.closeEntry();
            }
            zipIn.close();
        }
        catch(Exception e) {
            errorString = "Error unzipping " + zippedFile
                    + ".\nerror = \"" + e.getMessage() + "\"";
            success = false;
        }
        return success;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        mProgressDialog.dismiss();
        mProgressDialog = null;
        // notify the caller of task completion.
        statusListener.asyncTaskCompleted (
            TAG, zippedFile, zippedBytes, unZippedFile, unZippedBytes
            , errorString, result);
        callerContext = null;
    }
}
