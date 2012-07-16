/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.phone;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.ThrottleManager;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.util.Log;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;

/**
 * List of Phone-specific settings screens.
 */
public class Settings extends PreferenceActivity implements DialogInterface.OnClickListener,
        DialogInterface.OnDismissListener, Preference.OnPreferenceChangeListener{

    // debug data
    private static final String LOG_TAG = "NetworkSettings";
    private static final boolean DBG = true;
    public static final int REQUEST_CODE_EXIT_ECM = 17;

    //String keys for preference lookup
    private static final String BUTTON_DATA_ENABLED_KEY = "button_data_enabled_key";
    private static final String BUTTON_DATA_USAGE_KEY = "button_data_usage_key";
    private static final String BUTTON_PREFERED_NETWORK_MODE = "preferred_network_mode_key";
    private static final String BUTTON_PREFERED_UMTS_NETWORK_MODE = "preferred_umts_network_mode_key";
    private static final String BUTTON_PREFERED_CDMA_NETWORK_MODE = "preferred_cdma_network_mode_key";
    private static final String BUTTON_ROAMING_KEY = "button_roaming_key";
    private static final String BUTTON_MVNO_ROAMING_KEY = "button_mvno_roaming_key";
    private static final String BUTTON_CDMA_ROAMING_KEY = "cdma_roaming_mode_key";
    private static final String BUTTON_GSM_UMTS_OPTIONS = "gsm_umts_options_key";
    private static final String BUTTON_CDMA_OPTIONS = "cdma_options_key";

    static final int preferredNetworkMode = Phone.PREFERRED_NT_MODE;

    //UI objects
    private ListPreference mButtonPreferredNetworkMode;
    private ListPreference mButtonPreferredUmtsNetworkMode;
    private ListPreference mButtonPreferredCdmaNetworkMode;
    private CheckBoxPreference mButtonDataRoam;
    private CheckBoxPreference mButtonDataEnabled;
    private CheckBoxPreference mButtonMvnoDataRoam;

    private Preference mButtonDataUsage;
    private DataUsageListener mDataUsageListener;
    private static final String iface = "rmnet0"; //TODO: this will go away

    private Phone mPhone;
    private MyHandler mHandler;
    private boolean mOkClicked;

    //GsmUmts options and Cdma options
    GsmUmtsOptions mGsmUmtsOptions;
    CdmaOptions mCdmaOptions;

    private Preference mClickedPreference;


    //This is a method implemented for DialogInterface.OnClickListener.
    //  Used to dismiss the dialogs when they come up.
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            mPhone.setDataRoamingEnabled(true);
            mOkClicked = true;
        } else {
            // Reset the toggle
            mButtonDataRoam.setChecked(false);
        }
    }

    public void onDismiss(DialogInterface dialog) {
        // Assuming that onClick gets called first
        if (!mOkClicked) {
            mButtonDataRoam.setChecked(false);
        }
    }

    /**
     * Invoked on each preference click in this hierarchy, overrides
     * PreferenceActivity's implementation.  Used to make sure we track the
     * preference click events.
     */
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        /** TODO: Refactor and get rid of the if's using subclasses */
        if (mGsmUmtsOptions != null &&
                mGsmUmtsOptions.preferenceTreeClick(preference) == true) {
            return true;
        } else if (mCdmaOptions != null &&
                   mCdmaOptions.preferenceTreeClick(preference) == true) {
            if (Boolean.parseBoolean(
                    SystemProperties.get(TelephonyProperties.PROPERTY_INECM_MODE))) {

                mClickedPreference = preference;

                // In ECM mode launch ECM app dialog
                startActivityForResult(
                    new Intent(TelephonyIntents.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS, null),
                    REQUEST_CODE_EXIT_ECM);
            }
            return true;

        } else if (preference == mButtonPreferredNetworkMode ||
                       preference == mButtonPreferredUmtsNetworkMode ||
                       preference == mButtonPreferredCdmaNetworkMode) {
            //displays the value taken from the Settings.System
            int phoneType = mPhone.getPhoneType();
            int settingsNetworkMode = android.provider.Settings.Secure.getInt(mPhone.getContext().
                    getContentResolver(), android.provider.Settings.Secure.PREFERRED_NETWORK_MODE,
                    preferredNetworkMode);
            mButtonPreferredUmtsNetworkMode.setValue(Integer.toString(settingsNetworkMode));
            mButtonPreferredCdmaNetworkMode.setValue(Integer.toString(settingsNetworkMode));
            mButtonPreferredNetworkMode.setValue(Integer.toString(settingsNetworkMode));
            return true;

        } else if (preference == mButtonDataRoam) {
            if (DBG) log("onPreferenceTreeClick: preference == mButtonDataRoam.");

            //normally called on the toggle click
            if (mButtonDataRoam.isChecked()) {
                // First confirm with a warning dialog about charges
                mOkClicked = false;
                new AlertDialog.Builder(this).setMessage(
                        getResources().getString(R.string.roaming_warning))
                        .setTitle(android.R.string.dialog_alert_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, this)
                        .setNegativeButton(android.R.string.no, this)
                        .show()
                        .setOnDismissListener(this);
            } else {
                mPhone.setDataRoamingEnabled(false);
            }
            return true;

        } else if (preference == mButtonMvnoDataRoam) {
            if (mButtonMvnoDataRoam.isChecked()) {
                new AlertDialog.Builder(this).setMessage(
                    getResources().getString(R.string.mvno_roaming_warning))
                    .setTitle(android.R.string.dialog_alert_title)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            android.provider.Settings.System.putInt(getContentResolver(),
                                    android.provider.Settings.Secure.MVNO_ROAMING, 1);
                        }
                     })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            android.provider.Settings.System.putInt(getContentResolver(),
                                    android.provider.Settings.Secure.MVNO_ROAMING, 0);
                            mButtonMvnoDataRoam.setChecked(false);
                        }
                     })
                    .show();
            }
            android.provider.Settings.System.putInt(getContentResolver(),
                        android.provider.Settings.Secure.MVNO_ROAMING, 0);
            return true;

        } else if (preference == mButtonDataEnabled) {
            if (DBG) log("onPreferenceTreeClick: preference == mButtonDataEnabled.");
            ConnectivityManager cm =
                    (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

            cm.setMobileDataEnabled(mButtonDataEnabled.isChecked());
            //{PIAF
            Intent intent = new Intent(PhoneToggler.MOBILE_DATA_CHANGED);
            intent.putExtra(PhoneToggler.NETWORK_MODE, mButtonDataEnabled.isChecked());
            mPhone.getContext().sendBroadcast(intent);
            //PIAF}
            return true;

        } else {
            // if the button is anything but the simple toggle preference,
            // we'll need to disable all preferences to reject all click
            // events until the sub-activity's UI comes up.
            preferenceScreen.setEnabled(false);
            // Let the intents be launched by the Preference manager
            return false;
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.network_setting);

        mPhone = PhoneFactory.getDefaultPhone();
        mHandler = new MyHandler();

        //get UI object references
        PreferenceScreen prefSet = getPreferenceScreen();

        mButtonDataEnabled = (CheckBoxPreference) prefSet.findPreference(BUTTON_DATA_ENABLED_KEY);
        mButtonDataRoam = (CheckBoxPreference) prefSet.findPreference(BUTTON_ROAMING_KEY);
        mButtonMvnoDataRoam = (CheckBoxPreference) prefSet.findPreference(BUTTON_MVNO_ROAMING_KEY);
        mButtonMvnoDataRoam.setChecked(android.provider.Settings.System.getInt(getContentResolver(), 
                android.provider.Settings.Secure.MVNO_ROAMING, 0) == 1);

        mButtonPreferredCdmaNetworkMode = (ListPreference) prefSet.findPreference(
                BUTTON_PREFERED_CDMA_NETWORK_MODE);
        mButtonPreferredUmtsNetworkMode = (ListPreference) prefSet.findPreference(
                BUTTON_PREFERED_UMTS_NETWORK_MODE);
        mButtonPreferredNetworkMode = (ListPreference) prefSet.findPreference(
                BUTTON_PREFERED_NETWORK_MODE);

        mButtonDataUsage = prefSet.findPreference(BUTTON_DATA_USAGE_KEY);

        if (getResources().getBoolean(R.bool.world_phone) == true) {

            prefSet.removePreference(mButtonPreferredUmtsNetworkMode);
            prefSet.removePreference(mButtonPreferredCdmaNetworkMode);
            // set the listener for the mButtonPreferredNetworkMode list preference so we can issue
            // change Preferred Network Mode.
            mButtonPreferredNetworkMode.setOnPreferenceChangeListener(this);

            //Get the networkMode from Settings.System and displays it
            int settingsNetworkMode = android.provider.Settings.Secure.getInt(
                    mPhone.getContext().getContentResolver(),
                    android.provider.Settings.Secure.PREFERRED_NETWORK_MODE,
                    preferredNetworkMode);

            mButtonPreferredNetworkMode.setValue(Integer.toString(settingsNetworkMode));
            mCdmaOptions = new CdmaOptions(this, prefSet);
            mGsmUmtsOptions = new GsmUmtsOptions(this, prefSet);

            if (getResources().getBoolean(R.bool.config_preferred_mode_disable) == true) {
                prefSet.removePreference(mButtonPreferredNetworkMode);
            }

        } else {

            //Get the networkMode from Settings.System and displays it
            int phoneType = mPhone.getPhoneType();
            prefSet.removePreference(mButtonPreferredNetworkMode);
            int settingsNetworkMode = android.provider.Settings.Secure.getInt(
                    mPhone.getContext().getContentResolver(),
                    android.provider.Settings.Secure.PREFERRED_NETWORK_MODE,
                    preferredNetworkMode);

            if (phoneType == Phone.PHONE_TYPE_CDMA) {
                mCdmaOptions = new CdmaOptions(this, prefSet);
                prefSet.removePreference(mButtonPreferredUmtsNetworkMode);
                mButtonPreferredCdmaNetworkMode.setOnPreferenceChangeListener(this);
                mButtonPreferredCdmaNetworkMode.setValue(Integer.toString(settingsNetworkMode));
            } else if (phoneType == Phone.PHONE_TYPE_GSM) {
                mGsmUmtsOptions = new GsmUmtsOptions(this, prefSet);
                prefSet.removePreference(mButtonPreferredCdmaNetworkMode);
                mButtonPreferredUmtsNetworkMode.setOnPreferenceChangeListener(this);
                mButtonPreferredUmtsNetworkMode.setValue(Integer.toString(settingsNetworkMode));
            } else {
                throw new IllegalStateException("Unexpected phone type: " + phoneType);
            }

            if (getResources().getBoolean(R.bool.config_preferred_mode_disable) == true) {
                prefSet.removePreference(mButtonPreferredCdmaNetworkMode);
                prefSet.removePreference(mButtonPreferredUmtsNetworkMode);
            }

        }
        ThrottleManager tm = (ThrottleManager) getSystemService(Context.THROTTLE_SERVICE);
        mDataUsageListener = new DataUsageListener(this, mButtonDataUsage, prefSet);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // upon resumption from the sub-activity, make sure we re-enable the
        // preferences.
        getPreferenceScreen().setEnabled(true);

        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        mButtonDataEnabled.setChecked(cm.getMobileDataEnabled());

        // Set UI state in onResume because a user could go home, launch some
        // app to change this setting's backend, and re-launch this settings app
        // and the UI state would be inconsistent with actual state
        mButtonDataRoam.setChecked(mPhone.getDataRoamingEnabled());

        if (getPreferenceScreen().findPreference(BUTTON_PREFERED_NETWORK_MODE) != null)  {
            mPhone.getPreferredNetworkType(mHandler.obtainMessage(
                    MyHandler.MESSAGE_GET_PREFERRED_NETWORK_TYPE));
        }
        mDataUsageListener.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mDataUsageListener.pause();
    }

    /**
     * Implemented to support onPreferenceChangeListener to look for preference
     * changes specifically on CLIR.
     *
     * @param preference is the preference to be changed, should be mButtonCLIR.
     * @param objValue should be the value of the selection, NOT its localized
     * display value.
     */
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (preference == mButtonPreferredNetworkMode ||
                preference == mButtonPreferredUmtsNetworkMode ||
                preference == mButtonPreferredCdmaNetworkMode) {

            //NOTE onPreferenceChange seems to be called even if there is no change
            //Check if the button value is changed from the System.Setting
            mButtonPreferredUmtsNetworkMode.setValue((String) objValue);
            mButtonPreferredCdmaNetworkMode.setValue((String) objValue);
            mButtonPreferredNetworkMode.setValue((String) objValue);
            int buttonNetworkMode;
            buttonNetworkMode = Integer.valueOf((String) objValue).intValue();
            int settingsNetworkMode = android.provider.Settings.Secure.getInt(
                    mPhone.getContext().getContentResolver(),
                    android.provider.Settings.Secure.PREFERRED_NETWORK_MODE, preferredNetworkMode);
            if (buttonNetworkMode != settingsNetworkMode) {
                int modemNetworkMode;
                switch(buttonNetworkMode) {
                    case Phone.NT_MODE_GLOBAL:
                        modemNetworkMode = Phone.NT_MODE_GLOBAL;
                        break;
                    case Phone.NT_MODE_EVDO_NO_CDMA:
                        modemNetworkMode = Phone.NT_MODE_EVDO_NO_CDMA;
                        break;
                    case Phone.NT_MODE_CDMA_NO_EVDO:
                        modemNetworkMode = Phone.NT_MODE_CDMA_NO_EVDO;
                        break;
                    case Phone.NT_MODE_CDMA:
                        modemNetworkMode = Phone.NT_MODE_CDMA;
                        break;
                    case Phone.NT_MODE_GSM_UMTS:
                        modemNetworkMode = Phone.NT_MODE_GSM_UMTS;
                        break;
                    case Phone.NT_MODE_WCDMA_ONLY:
                        modemNetworkMode = Phone.NT_MODE_WCDMA_ONLY;
                        break;
                    case Phone.NT_MODE_GSM_ONLY:
                        modemNetworkMode = Phone.NT_MODE_GSM_ONLY;
                        break;
                    case Phone.NT_MODE_WCDMA_PREF:
                        modemNetworkMode = Phone.NT_MODE_WCDMA_PREF;
                        break;
                    default:
                        modemNetworkMode = Phone.PREFERRED_NT_MODE;
                }
                UpdatePreferredNetworkModeSummary(buttonNetworkMode);

                android.provider.Settings.Secure.putInt(mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Secure.PREFERRED_NETWORK_MODE,
                        buttonNetworkMode );
                //Set the modem network mode
                mPhone.setPreferredNetworkType(modemNetworkMode, mHandler
                        .obtainMessage(MyHandler.MESSAGE_SET_PREFERRED_NETWORK_TYPE));

                //{PIAF
                Intent intent = new Intent(PhoneToggler.NETWORK_MODE_CHANGED);
                intent.putExtra(PhoneToggler.NETWORK_MODE, buttonNetworkMode);
                mPhone.getContext().sendBroadcast(intent, PhoneToggler.CHANGE_NETWORK_MODE_PERM);
                //PIAF}
            }
        }

        // always let the preference setting proceed.
        return true;
    }

    private class MyHandler extends Handler {

        private static final int MESSAGE_GET_PREFERRED_NETWORK_TYPE = 0;
        private static final int MESSAGE_SET_PREFERRED_NETWORK_TYPE = 1;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_GET_PREFERRED_NETWORK_TYPE:
                    handleGetPreferredNetworkTypeResponse(msg);
                    break;

                case MESSAGE_SET_PREFERRED_NETWORK_TYPE:
                    handleSetPreferredNetworkTypeResponse(msg);
                    break;
            }
        }

        private void handleGetPreferredNetworkTypeResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            if (ar.exception == null) {
                int modemNetworkMode = ((int[])ar.result)[0];

                if (DBG) {
                    log ("handleGetPreferredNetworkTypeResponse: modemNetworkMode = " +
                            modemNetworkMode);
                }

                int settingsNetworkMode = android.provider.Settings.Secure.getInt(
                        mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Secure.PREFERRED_NETWORK_MODE,
                        preferredNetworkMode);

                if (DBG) {
                    log("handleGetPreferredNetworkTypeReponse: settingsNetworkMode = " +
                            settingsNetworkMode);
                }

                //check that modemNetworkMode is from an accepted value
                if (modemNetworkMode == Phone.NT_MODE_WCDMA_PREF ||
                        modemNetworkMode == Phone.NT_MODE_GSM_ONLY ||
                        modemNetworkMode == Phone.NT_MODE_WCDMA_ONLY ||
                        modemNetworkMode == Phone.NT_MODE_GSM_UMTS ||
                        modemNetworkMode == Phone.NT_MODE_CDMA ||
                        modemNetworkMode == Phone.NT_MODE_CDMA_NO_EVDO ||
                        modemNetworkMode == Phone.NT_MODE_EVDO_NO_CDMA ||
                        //A modem might report world phone sometimes
                        //but it's not true. Double check here
                        (getResources().getBoolean(R.bool.world_phone) == true &&
                            modemNetworkMode == Phone.NT_MODE_GLOBAL) ) {
                    if (DBG) {
                        log("handleGetPreferredNetworkTypeResponse: if 1: modemNetworkMode = " +
                                modemNetworkMode);
                    }

                    //check changes in modemNetworkMode and updates settingsNetworkMode
                    if (modemNetworkMode != settingsNetworkMode) {
                        if (DBG) {
                            log("handleGetPreferredNetworkTypeResponse: if 2: " +
                                    "modemNetworkMode != settingsNetworkMode");
                        }

                        settingsNetworkMode = modemNetworkMode;

                        if (DBG) { log("handleGetPreferredNetworkTypeResponse: if 2: " +
                                "settingsNetworkMode = " + settingsNetworkMode);
                        }

                        //changes the Settings.System accordingly to modemNetworkMode
                        android.provider.Settings.Secure.putInt(
                                mPhone.getContext().getContentResolver(),
                                android.provider.Settings.Secure.PREFERRED_NETWORK_MODE,
                                settingsNetworkMode );
                    }

                    UpdatePreferredNetworkModeSummary(modemNetworkMode);
                    // changes the mButtonPreferredNetworkMode accordingly to modemNetworkMode
                    mButtonPreferredCdmaNetworkMode.setValue(Integer.toString(modemNetworkMode));
                    mButtonPreferredUmtsNetworkMode.setValue(Integer.toString(modemNetworkMode));
                    mButtonPreferredNetworkMode.setValue(Integer.toString(modemNetworkMode));
                    //{PIAF
                    Intent intent = new Intent(PhoneToggler.NETWORK_MODE_CHANGED);
                    intent.putExtra(PhoneToggler.NETWORK_MODE, modemNetworkMode);
                    mPhone.getContext().sendBroadcast(intent, PhoneToggler.CHANGE_NETWORK_MODE_PERM);
                    //PIAF}
                } else {
                    if (DBG) log("handleGetPreferredNetworkTypeResponse: else: reset to default");
                    resetNetworkModeToDefault();
                }
            }
        }

        private void handleSetPreferredNetworkTypeResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            int phoneType = mPhone.getPhoneType();
            int networkMode;
            if (ar.exception == null) {
                if (phoneType == Phone.PHONE_TYPE_GSM) {
                    networkMode = Integer.valueOf(
                        mButtonPreferredUmtsNetworkMode.getValue()).intValue();
                } else if (phoneType == Phone.PHONE_TYPE_CDMA) {
                    networkMode = Integer.valueOf(
                        mButtonPreferredCdmaNetworkMode.getValue()).intValue();
                } else {
                    networkMode = Integer.valueOf(
                        mButtonPreferredNetworkMode.getValue()).intValue();
                }
                android.provider.Settings.Secure.putInt(mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Secure.PREFERRED_NETWORK_MODE,
                        networkMode );
                //{PIAF
                Intent intent = new Intent(PhoneToggler.NETWORK_MODE_CHANGED);
                intent.putExtra(PhoneToggler.NETWORK_MODE, networkMode);
                mPhone.getContext().sendBroadcast(intent, PhoneToggler.CHANGE_NETWORK_MODE_PERM);
                //PIAF}
            } else {
                mPhone.getPreferredNetworkType(obtainMessage(MESSAGE_GET_PREFERRED_NETWORK_TYPE));
            }
        }

        private void resetNetworkModeToDefault() {
            //set the mButtonPreferredNetworkMode
            mButtonPreferredCdmaNetworkMode.setValue(Integer.toString(preferredNetworkMode));
            mButtonPreferredUmtsNetworkMode.setValue(Integer.toString(preferredNetworkMode));
            mButtonPreferredNetworkMode.setValue(Integer.toString(preferredNetworkMode));
            //set the Settings.System
            android.provider.Settings.Secure.putInt(mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Secure.PREFERRED_NETWORK_MODE,
                        preferredNetworkMode );
            //Set the Modem
            mPhone.setPreferredNetworkType(preferredNetworkMode,
                    this.obtainMessage(MyHandler.MESSAGE_SET_PREFERRED_NETWORK_TYPE));
        }
    }

    private void UpdatePreferredNetworkModeSummary(int NetworkMode) {
        int phoneType = mPhone.getPhoneType();
        final String prefMode = getResources().getString(R.string.preferred_network_mode_dialogtitle);

        // TODO T: Make all of these strings come from res/values/strings.xml.
        if (phoneType == Phone.PHONE_TYPE_CDMA) {
            switch(NetworkMode) {
            case Phone.NT_MODE_CDMA_NO_EVDO:
                mButtonPreferredCdmaNetworkMode.setSummary(prefMode + ": CDMA only");
                break;
            case Phone.NT_MODE_EVDO_NO_CDMA:
                mButtonPreferredCdmaNetworkMode.setSummary(prefMode + ": EvDo only");
                break;
            case Phone.NT_MODE_CDMA:
            default:
                mButtonPreferredCdmaNetworkMode.setSummary(prefMode + ": CDMA / EvDo");
            }
        } else if (phoneType == Phone.PHONE_TYPE_GSM) {
            switch(NetworkMode) {
            case Phone.NT_MODE_WCDMA_PREF:
                mButtonPreferredUmtsNetworkMode.setSummary(prefMode + ": WCDMA pref");
                break;
            case Phone.NT_MODE_GSM_ONLY:
                mButtonPreferredUmtsNetworkMode.setSummary(prefMode + ": GSM only");
                break;
            case Phone.NT_MODE_WCDMA_ONLY:
                mButtonPreferredUmtsNetworkMode.setSummary(prefMode + ": WCDMA only");
                break;
            case Phone.NT_MODE_GSM_UMTS:
            default:
                mButtonPreferredUmtsNetworkMode.setSummary(prefMode + ": GSM/WCDMA");
            }
        } else {
            switch(NetworkMode) {
            case Phone.NT_MODE_WCDMA_PREF:
                mButtonPreferredNetworkMode.setSummary(prefMode + ": WCDMA pref");
                break;
            case Phone.NT_MODE_GSM_ONLY:
                mButtonPreferredNetworkMode.setSummary(prefMode + ": GSM only");
                break;
            case Phone.NT_MODE_WCDMA_ONLY:
                mButtonPreferredNetworkMode.setSummary(prefMode + ": WCDMA only");
                break;
            case Phone.NT_MODE_GSM_UMTS:
                mButtonPreferredNetworkMode.setSummary(prefMode + ": GSM/WCDMA");
                break;
            case Phone.NT_MODE_CDMA:
                mButtonPreferredNetworkMode.setSummary(prefMode + ": CDMA / EvDo");
                break;
            case Phone.NT_MODE_CDMA_NO_EVDO:
                mButtonPreferredNetworkMode.setSummary(prefMode + ": CDMA only");
                break;
            case Phone.NT_MODE_EVDO_NO_CDMA:
                mButtonPreferredNetworkMode.setSummary(prefMode + ": EvDo only");
                break;
            case Phone.NT_MODE_GLOBAL:
            default:
                mButtonPreferredNetworkMode.setSummary(prefMode + ": Global");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
        case REQUEST_CODE_EXIT_ECM:
            Boolean isChoiceYes =
                data.getBooleanExtra(EmergencyCallbackModeExitDialog.EXTRA_EXIT_ECM_RESULT, false);
            if (isChoiceYes) {
                // If the phone exits from ECM mode, show the CDMA Options
                mCdmaOptions.showDialog(mClickedPreference);
            } else {
                // do nothing
            }
            break;

        default:
            break;
        }
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
