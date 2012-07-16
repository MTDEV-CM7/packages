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

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;

public class GanOptimizer extends Service {

    static final String TAG = "GanOptimizer";
    
    static final int NOTIFICATION_ID = 1;
    
    WifiManager.WifiLock lock;
    
    @Override
    public void onCreate() {
        Log.v(TAG, "Setting wifi high performance mode");
        
        final WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
        lock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "GanOptimizer");
        lock.setReferenceCounted(false);
        lock.acquire();
        
        // Keep the app from dying
        Notification n = new Notification();
        n.defaults = 0;
        n.flags = Notification.FLAG_FOREGROUND_SERVICE
            | Notification.FLAG_NO_CLEAR
            | Notification.FLAG_ONGOING_EVENT;
        n.when = System.currentTimeMillis();
        n.setLatestEventInfo(
                this,
                getText(R.string.notificationTitle),
                getText(R.string.notificationText),
                null
        );
        startForeground(NOTIFICATION_ID, n);
    }
    
    @Override
    public void onDestroy() {
        Log.v(TAG, "Releasing wifi high performance mode");
        
        lock.release();
        lock = null;
        stopForeground(true);
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
