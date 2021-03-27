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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.GZIPInputStream;

public class AsyncGunzip extends AsyncTask<String, String, Boolean> {
    public static final String TAG = "AsyncGunzip";
    private AsyncStatusInterface statusListener;
    private Context callerContext;
    private String fileBaseName         // used as title for the progress bar
        , errorString = "";
    private ProgressDialog mProgressDialog;
    public AsyncGunzip(Context c, String fileBaseName) {
        callerContext = c;
        this.fileBaseName = fileBaseName;
        this.statusListener = (AsyncStatusInterface)c;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        mProgressDialog = new ProgressDialog(callerContext);
        mProgressDialog.setMessage("Gunzipping " + fileBaseName);
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();
    }
    private String zippedFile, unZippedFile;
    private long zippedBytes = -1, unZippedBytes = 0;

    @Override
    protected Boolean doInBackground(String... params) {
        zippedFile = params[0]; unZippedFile = params[1];
        boolean success = true;
        byte[] buffer = new byte[0x400];
        try  {
            File f = new File(zippedFile);
            if ( ! f.exists())
                throw new RuntimeException("Zip in file " + zippedFile
                    + " doesn't exist.");
            zippedBytes = f.length();
            GZIPInputStream gzipIn =
                new GZIPInputStream(new FileInputStream(zippedFile));

            FileOutputStream gzipOut =
                new FileOutputStream(unZippedFile);
            int count;
            while ((count = gzipIn.read(buffer)) > 0) {
                gzipOut.write(buffer, 0, count);
                unZippedBytes += count;
            }
            gzipOut.close();
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
        statusListener.asyncTaskCompleted (
            TAG, zippedFile, zippedBytes, unZippedFile, unZippedBytes
            , errorString, result);
        callerContext = null;
    }
}
