/*
 * Copyright (C) 2009 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.wallpaper.nexus;

import com.android.wallpaper.R;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceGroup;
import android.service.wallpaper.WallpaperSettingsActivity;
import android.util.Log;

public class NexusSettings extends WallpaperSettingsActivity 
	implements SharedPreferences.OnSharedPreferenceChangeListener {

	public static final String COLORSCHEME_PREF = "nexus_colorscheme";
	private static final String COLOR0_PREF = "color0";
	private static final String COLOR1_PREF = "color1";
	private static final String COLOR2_PREF = "color2";
	private static final String COLOR3_PREF = "color3";

	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		getPreferenceManager().setSharedPreferencesName(
				NexusWallpaper.SHARED_PREFS_NAME);
		addPreferencesFromResource(R.xml.nexus_prefs);
		final PreferenceGroup parentPreference = getPreferenceScreen();
		parentPreference.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	}

	protected void onResume() {
		super.onResume();
	}

	protected void onDestroy() {
		super.onDestroy();
	}

	public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
		if (COLORSCHEME_PREF.equals(key)) {
			final Resources res = this.getResources();
			final String[] colorscheme = res.getStringArray(res.getIdentifier("nexus_colorscheme_" + 
				preferences.getString(key, "mau5"), "array", "com.android.wallpaper"));

			SharedPreferences.Editor editor = preferences.edit();
			editor.putString(COLOR0_PREF, colorscheme[0]);
			editor.putString(COLOR1_PREF, colorscheme[1]);
			editor.putString(COLOR2_PREF, colorscheme[2]);
			editor.putString(COLOR3_PREF, colorscheme[3]);
			editor.commit();

			Log.d("Nexus LWP", "colorScheme="+preferences.getString(key, "none"));

			Log.d("Nexus LWP", "color0="+colorscheme[0]);
			Log.d("Nexus LWP", "color1="+colorscheme[1]);
			Log.d("Nexus LWP", "color2="+colorscheme[2]);
			Log.d("Nexus LWP", "color3="+colorscheme[3]);
		}
	}

}
