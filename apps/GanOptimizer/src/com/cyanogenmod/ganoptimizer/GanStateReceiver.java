/*
 * Copyright (C) 2011 The CyanogenMod Project
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

package com.cyanogenmod.ganoptimizer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class GanStateReceiver extends BroadcastReceiver {
    final String TAG = "GanOptimizer";
    
    final String KINETO_INTENT = "com.android.kineto.GanState";
    
    final String KINETO_QOS = "QoS";
    
    final String KINETO_GANSTATE = "GanState";
    final String KINETO_GANSTATE_DEREGISTERED = "DEREGISTERED";
    final String KINETO_GANSTATE_REGISTERED = "REGISTERED";
    final String KINETO_GANSTATE_GAN_CS_CALL = "GAN_CS_CALL";
    final String KINETO_GANSTATE_GAN_VOICE_CALL = "GAN_VOICE_CALL";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        final String ganState = intent.getStringExtra(KINETO_GANSTATE);;
        final boolean callInProgress = (!ganState.equals(KINETO_GANSTATE_REGISTERED)
                && !ganState.equals(KINETO_GANSTATE_DEREGISTERED));
        
        if (callInProgress && !ganState.endsWith("_CALL")) {
            Log.w(TAG, "Assuming unknown GanState " + ganState + " is a call");
        }
        
        Log.v(TAG, "Wifi call is " + (callInProgress ? "in progress" : "not in progress") + " (state " + ganState + ")");
        if (callInProgress) {
            context.startService(new Intent(context, GanOptimizer.class));
        } else {
            context.stopService(new Intent(context, GanOptimizer.class));
        }
    }

}
