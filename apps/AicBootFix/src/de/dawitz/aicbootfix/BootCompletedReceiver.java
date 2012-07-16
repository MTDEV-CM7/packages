/*
 * Created by Sven Dawitz; Copyright (C) 2011 CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.dawitz.aicbootfix;

import java.io.IOException;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRecorder;

/**
 * 
 * @author sven dawitz
 * The aic3254 chip for HTC Desire HD and HTC Inspire (ace) devices does not
 * initialize in the correct way since we currently cannot use its native
 * libaudio.so. tests have shown, starting recording once fixes the initialzation
 * issue. Thats whats this bootfix about - on system boot, just start recording
 * for a moment, then exit again.
 *
 * May seem stupid, but serves its purpose: aic3254 chip is working as intended
 *
 */

public class BootCompletedReceiver extends BroadcastReceiver {
    // only called on boot_completed
    @Override
    public void onReceive(Context context, Intent intent) {
        
        MediaRecorder mRecorder;

        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mRecorder.setOutputFile("/dev/null");

        try {
            mRecorder.prepare();
        } catch(IOException exception) {
            mRecorder.reset();
            mRecorder.release();
            mRecorder = null;
        }

        try {
            mRecorder.start();
        } catch (RuntimeException exception) {
            mRecorder.reset();
            mRecorder.release();
            mRecorder = null;
            return;
        }

        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;
    }
}
