/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.contacts;

import com.android.contacts.T9Search.ContactItem;
import com.android.contacts.T9Search.T9Adapter;
import com.android.contacts.T9Search.T9SearchResult;
import com.android.internal.telephony.ITelephony;
import com.android.phone.CallLogAsync;
import com.android.phone.HapticFeedback;

import java.util.ArrayList;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.os.Vibrator;
import android.provider.ContactsContract.Intents.Insert;
import android.provider.ContactsContract.Contacts;
import android.provider.Contacts.Phones;
import android.provider.Contacts.PhonesColumns;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.DialerKeyListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.ViewSwitcher;

import com.android.internal.telephony.ITelephony;

//Wysie
import android.content.ComponentName;
import android.content.res.ColorStateList;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.provider.CallLog.Calls;
import android.widget.ImageButton;

/**
 * Dialer activity that displays the typical twelve key interface.
 */
@SuppressWarnings("deprecation")
public class TwelveKeyDialer extends Activity implements View.OnClickListener,
        View.OnLongClickListener, View.OnKeyListener,
        View.OnTouchListener,
        AdapterView.OnItemLongClickListener,
        AdapterView.OnItemClickListener, TextWatcher {
    private static final String EMPTY_NUMBER = "";
    private static final String TAG = "TwelveKeyDialer";

    /** The length of DTMF tones in milliseconds */
    private static final int TONE_LENGTH_MS = 150;

    /** The DTMF tone volume relative to other sounds in the stream */
    private static final int TONE_RELATIVE_VOLUME = 80;

    /** Stream type used to play the DTMF tones off call, and mapped to the volume control keys */
    private static final int DIAL_TONE_STREAM_TYPE = AudioManager.STREAM_MUSIC;

    /** Play the vibrate pattern only once. */
    private static final int VIBRATE_NO_REPEAT = -1;

    private EditText mDigits;
    private View mDelete;
    private MenuItem mAddToContactMenuItem;
    private ToneGenerator mToneGenerator;
    private Object mToneGeneratorLock = new Object();
    private Drawable mDigitsBackground;
    private Drawable mDigitsEmptyBackground;
    private View mDialpad;
    private View mVoicemailDialAndDeleteRow;
    private ImageButton mVoicemailButton; //YC: Change from View to ImageButton
    private View mDialButton;
    private ListView mDialpadChooser;
    private DialpadChooserAdapter mDialpadChooserAdapter;
    //Member variables for dialpad options
    private MenuItem m2SecPauseMenuItem;
    private MenuItem mWaitMenuItem;
    private static final int MENU_ADD_CONTACTS = 1;
    private static final int MENU_2S_PAUSE = 2;
    private static final int MENU_WAIT = 3;

    // Last number dialed, retrieved asynchronously from the call DB
    // in onCreate. This number is displayed when the user hits the
    // send key and cleared in onPause.
    CallLogAsync mCallLog = new CallLogAsync();
    private String mLastNumberDialed = EMPTY_NUMBER;

    // determines if we want to playback local DTMF tones.
    private boolean mDTMFToneEnabled;

    // Vibration (haptic feedback) for dialer key presses.
    private Vibrator mVibrator;
    private boolean mVibrateOn;
    private long[] mVibratePattern;
    
    //Wysie
    private MenuItem mSmsMenuItem, mPreferences;
    private SharedPreferences ePrefs;
    private boolean prefVibrateOn, retrieveLastDialled, returnToDialer;
    
    // stores the return value of the last call to hasVoicemail()
    private boolean mHasVoicemail;

    private static final int MENU_SMS = 4;
    private static final int MENU_PREFERENCES = 5;

    /** Identifier for the "Add Call" intent extra. */
    static final String ADD_CALL_MODE_KEY = "add_call_mode";
    private static T9Search sT9Search; // Static to avoid reloading when class is destroyed and recreated
    private ToggleButton mT9Toggle;
    private ListView mT9List;
    private ListView mT9ListTop;
    private T9Adapter mT9Adapter;
    private T9Adapter mT9AdapterTop;
    private ViewSwitcher mT9Flipper;
    private LinearLayout mT9Top;

    /**
     * Identifier for intent extra for sending an empty Flash message for
     * CDMA networks. This message is used by the network to simulate a
     * press/depress of the "hookswitch" of a landline phone. Aka "empty flash".
     *
     * TODO: Using an intent extra to tell the phone to send this flash is a
     * temporary measure. To be replaced with an ITelephony call in the future.
     * TODO: Keep in sync with the string defined in OutgoingCallBroadcaster.java
     * in Phone app until this is replaced with the ITelephony API.
     */
    static final String EXTRA_SEND_EMPTY_FLASH
            = "com.android.phone.extra.SEND_EMPTY_FLASH";

    /** Indicates if we are opening this dialer to add a call from the InCallScreen. */
    private boolean mIsAddCallMode;

    PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
            /**
             * Listen for phone state changes so that we can take down the
             * "dialpad chooser" if the phone becomes idle while the
             * chooser UI is visible.
             */
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                // Log.i(TAG, "PhoneStateListener.onCallStateChanged: "
                //       + state + ", '" + incomingNumber + "'");
                if ((state == TelephonyManager.CALL_STATE_IDLE) && dialpadChooserVisible()) {
                    // Log.i(TAG, "Call ended with dialpad chooser visible!  Taking it down...");
                    // Note there's a race condition in the UI here: the
                    // dialpad chooser could conceivably disappear (on its
                    // own) at the exact moment the user was trying to select
                    // one of the choices, which would be confusing.  (But at
                    // least that's better than leaving the dialpad chooser
                    // onscreen, but useless...)
                    showDialpadChooser(false);
                }
            }
        };

    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // Do nothing
    }

    public void onTextChanged(CharSequence input, int start, int before, int changeCount) {
        // Do nothing
        // DTMF Tones do not need to be played here any longer -
        // the DTMF dialer handles that functionality now.
    }

    public void afterTextChanged(Editable input) {
        if (SpecialCharSequenceMgr.handleChars(this, input.toString(), mDigits)) {
            // A special sequence was entered, clear the digits
            mDigits.getText().clear();
        }

        if (!isDigitsEmpty()) {
            mDigits.setBackgroundDrawable(mDigitsBackground);
        } else {
            mDigits.setCursorVisible(false);
            mDigits.setBackgroundDrawable(mDigitsEmptyBackground);
        }

        updateDialAndDeleteButtonEnabledState();
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        //Wysie
        ePrefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        Resources r = getResources();
        // Do not show title in the case the device is in carmode.
        if ((r.getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK) ==
                Configuration.UI_MODE_TYPE_CAR) {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
        }
        // Set the content view
        setContentView(getContentViewResource());

        // Load up the resources for the text field.
        mDigitsBackground = r.getDrawable(R.drawable.btn_dial_textfield_active);
        mDigitsEmptyBackground = r.getDrawable(R.drawable.btn_dial_textfield);

        mDigits = (EditText) findViewById(R.id.digits);
        mDigits.setKeyListener(DialerKeyListener.getInstance());
        mDigits.setOnClickListener(this);
        mDigits.setOnTouchListener(this);
        mDigits.setOnKeyListener(this);
        mT9List = (ListView) findViewById(R.id.t9list);
        if (mT9List != null) {
            mT9List.setOnItemClickListener(this);
            mT9List.setOnItemLongClickListener(this);
        }
        mT9ListTop = (ListView) findViewById(R.id.t9listtop);
        if (mT9ListTop != null) {
            mT9ListTop.setOnItemClickListener(this);
            mT9ListTop.setOnItemLongClickListener(this);
            mT9ListTop.setTag(new ContactItem());
        }
        mT9Toggle = (ToggleButton) findViewById(R.id.t9toggle);
        if (mT9Toggle != null) {
            mT9Toggle.setOnClickListener(this);
        }
        mT9Flipper = (ViewSwitcher) findViewById(R.id.t9flipper);
        mT9Top = (LinearLayout) findViewById(R.id.t9topbar);
        maybeAddNumberFormatting();

        setupKeypad(true);

        mVoicemailDialAndDeleteRow = findViewById(R.id.voicemailAndDialAndDelete);

        //initVoicemailButton();

        // Check whether we should show the onscreen "Dial" button.
        mDialButton = mVoicemailDialAndDeleteRow.findViewById(R.id.dialButton);

        if (r.getBoolean(R.bool.config_show_onscreen_dial_button)) {
            mDialButton.setOnClickListener(this);
            mDialButton.setOnTouchListener(this);
        } else {
            mDialButton.setVisibility(View.GONE); // It's VISIBLE by default
            mDialButton = null;
        }

        View view = mVoicemailDialAndDeleteRow.findViewById(R.id.deleteButton);
        view.setOnClickListener(this);
        view.setOnTouchListener(this);
        view.setOnLongClickListener(this);
        mDelete = view;

        mDialpad = findViewById(R.id.dialpad);  // This is null in landscape mode.

        mDigits.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        // Set up the "dialpad chooser" UI; see showDialpadChooser().
        mDialpadChooser = (ListView) findViewById(R.id.dialpadChooser);
        mDialpadChooser.setOnItemClickListener(this);
        
        //Wysi: Should remove this since it's also in onResume. To be tested.
        //updateDialer();

        if (!resolveIntent() && icicle != null) {
            super.onRestoreInstanceState(icicle);
        }

    }

    @Override
    protected void onRestoreInstanceState(Bundle icicle) {
        // Do nothing, state is restored in onCreate() if needed
    }

    protected void maybeAddNumberFormatting() {
        mDigits.addTextChangedListener(new PhoneNumberFormattingTextWatcher());
    }

    /**
     * Overridden by subclasses to control the resource used by the content view.
     */
    protected int getContentViewResource() {
        return R.layout.twelve_key_dialer;
    }

    private boolean resolveIntent() {
        boolean ignoreState = false;

        // Find the proper intent
        final Intent intent;
        if (isChild()) {
            intent = getParent().getIntent();
            ignoreState = intent.getBooleanExtra(DialtactsActivity.EXTRA_IGNORE_STATE, false);
        } else {
            intent = getIntent();
        }
        // Log.i(TAG, "==> resolveIntent(): intent: " + intent);

        // by default we are not adding a call.
        mIsAddCallMode = false;

        // By default we don't show the "dialpad chooser" UI.
        boolean needToShowDialpadChooser = false;

        // Resolve the intent
        final String action = intent.getAction();
        if (Intent.ACTION_DIAL.equals(action) || Intent.ACTION_VIEW.equals(action)) {
            // see if we are "adding a call" from the InCallScreen; false by default.
            mIsAddCallMode = intent.getBooleanExtra(ADD_CALL_MODE_KEY, false);

            Uri uri = intent.getData();
            if (uri != null) {
                if ("tel".equals(uri.getScheme())) {
                    // Put the requested number into the input area
                    String data = uri.getSchemeSpecificPart();
                    setFormattedDigits(data);
                } else {
                    String type = intent.getType();
                    if (Contacts.CONTENT_ITEM_TYPE.equals(type)
                            || Phones.CONTENT_ITEM_TYPE.equals(type)) {
                        // Query the phone number
                        Cursor c = getContentResolver().query(intent.getData(),
                                new String[] {PhonesColumns.NUMBER}, null, null, null);
                        if (c != null) {
                            if (c.moveToFirst()) {
                                // Put the number into the input area
                                setFormattedDigits(c.getString(0));
                            }
                            c.close();
                        }
                    }
                }
            } else {
                // ACTION_DIAL or ACTION_VIEW with no data.
                // This behaves basically like ACTION_MAIN: If there's
                // already an active call, bring up an intermediate UI to
                // make the user confirm what they really want to do.
                // Be sure *not* to show the dialpad chooser if this is an
                // explicit "Add call" action, though.
                if (!mIsAddCallMode && phoneIsInUse()) {
                    needToShowDialpadChooser = true;
                }
            }
        } else if (Intent.ACTION_MAIN.equals(action)) {
            // The MAIN action means we're bringing up a blank dialer
            // (e.g. by selecting the Home shortcut, or tabbing over from
            // Contacts or Call log.)
            //
            // At this point, IF there's already an active call, there's a
            // good chance that the user got here accidentally (but really
            // wanted the in-call dialpad instead).  So we bring up an
            // intermediate UI to make the user confirm what they really
            // want to do.
            if (phoneIsInUse()) {
                // Log.i(TAG, "resolveIntent(): phone is in use; showing dialpad chooser!");
                needToShowDialpadChooser = true;
            }
        }

        // Bring up the "dialpad chooser" IFF we need to make the user
        // confirm which dialpad they really want.
        showDialpadChooser(needToShowDialpadChooser);

        return ignoreState;
    }

    protected void setFormattedDigits(String data) {
        // strip the non-dialable numbers out of the data string.
        String dialString = PhoneNumberUtils.extractNetworkPortion(data);
        dialString = PhoneNumberUtils.formatNumber(dialString);
        if (!TextUtils.isEmpty(dialString)) {
            Editable digits = mDigits.getText();
            digits.replace(0, digits.length(), dialString);
            // for some reason this isn't getting called in the digits.replace call above..
            // but in any case, this will make sure the background drawable looks right
            afterTextChanged(digits);
        }
    }

    @Override
    protected void onNewIntent(Intent newIntent) {
        setIntent(newIntent);
        resolveIntent();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // This can't be done in onCreate(), since the auto-restoring of the digits
        // will play DTMF tones for all the old digits if it is when onRestoreSavedInstanceState()
        // is called. This method will be called every time the activity is created, and
        // will always happen after onRestoreSavedInstanceState().
        mDigits.addTextChangedListener(this);
    }

    private void setupKeypad(boolean setupAllDigits) {
        // Setup the listeners for the buttons
        
        //YC: Changed type from View to ImageButton
        ImageButton digitOne = (ImageButton)findViewById(R.id.one);

        // Check for the presence of the keypad
        if (digitOne == null)
            return;

        digitOne.setOnClickListener(this);
        digitOne.setOnTouchListener(this);
        digitOne.setOnLongClickListener(this);

        mHasVoicemail = hasVoicemail();

        //YC: Set image accordingly        
        if (mHasVoicemail) {
        	digitOne.setImageResource(R.drawable.dial_num_1_with_vm);
        }
        else {
        	digitOne.setImageResource(R.drawable.dial_num_1_no_vm);
        }

        if (setupAllDigits) {
            findViewById(R.id.two).setOnClickListener(this);
            findViewById(R.id.three).setOnClickListener(this);
            findViewById(R.id.four).setOnClickListener(this);
            findViewById(R.id.five).setOnClickListener(this);
            findViewById(R.id.six).setOnClickListener(this);
            findViewById(R.id.seven).setOnClickListener(this);
            findViewById(R.id.eight).setOnClickListener(this);
            findViewById(R.id.nine).setOnClickListener(this);
            findViewById(R.id.star).setOnClickListener(this);

            findViewById(R.id.two).setOnTouchListener(this);
            findViewById(R.id.three).setOnTouchListener(this);
            findViewById(R.id.four).setOnTouchListener(this);
            findViewById(R.id.five).setOnTouchListener(this);
            findViewById(R.id.six).setOnTouchListener(this);
            findViewById(R.id.seven).setOnTouchListener(this);
            findViewById(R.id.eight).setOnTouchListener(this);
            findViewById(R.id.nine).setOnTouchListener(this);
            findViewById(R.id.star).setOnTouchListener(this);

            View view = findViewById(R.id.zero);
            view.setOnClickListener(this);
            view.setOnTouchListener(this);
            view.setOnLongClickListener(this);

            findViewById(R.id.pound).setOnClickListener(this);
            findViewById(R.id.pound).setOnTouchListener(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sT9Search == null && isT9On()) {
            Thread loadContacts = new Thread(new Runnable() {
                public void run () {
                    sT9Search = new T9Search(getBaseContext());
                }
            });
            loadContacts.start();
        }
        hideT9();
        // Query the last dialed number. Do it first because hitting
        // the DB is 'slow'. This call is asynchronous.
        queryLastOutgoingCall();

        // retrieve the DTMF tone play back setting.
        mDTMFToneEnabled = Settings.System.getInt(getContentResolver(),
                Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1;

        // if the mToneGenerator creation fails, just continue without it.  It is
        // a local audio signal, and is not as important as the dtmf tone itself.
        synchronized(mToneGeneratorLock) {
            if (mToneGenerator == null) {
                try {
                    // we want the user to be able to control the volume of the dial tones
                    // outside of a call, so we use the stream type that is also mapped to the
                    // volume control keys for this activity
                    mToneGenerator = new ToneGenerator(DIAL_TONE_STREAM_TYPE, TONE_RELATIVE_VOLUME);
                    setVolumeControlStream(DIAL_TONE_STREAM_TYPE);
                } catch (RuntimeException e) {
                    Log.w(TAG, "Exception caught while creating local tone generator: " + e);
                    mToneGenerator = null;
                }
            }
        }

        Activity parent = getParent();
        // See if we were invoked with a DIAL intent. If we were, fill in the appropriate
        // digits in the dialer field.
        if (parent != null && parent instanceof DialtactsActivity) {
            Uri dialUri = ((DialtactsActivity) parent).getAndClearDialUri();
            if (dialUri != null) {
                resolveIntent();
            }
        }

        // While we're in the foreground, listen for phone state changes,
        // purely so that we can take down the "dialpad chooser" if the
        // phone becomes idle while the chooser UI is visible.
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        telephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

        // Potentially show hint text in the mDigits field when the user
        // hasn't typed any digits yet.  (If there's already an active call,
        // this hint text will remind the user that he's about to add a new
        // call.)
        //
        // TODO: consider adding better UI for the case where *both* lines
        // are currently in use.  (Right now we let the user try to add
        // another call, but that call is guaranteed to fail.  Perhaps the
        // entire dialer UI should be disabled instead.)
        if (phoneIsInUse()) {
            mDigits.setHint(R.string.dialerDialpadHintText);
        } else {
            // Common case; no hint necessary.
            mDigits.setHint(null);

            // Also, a sanity-check: the "dialpad chooser" UI should NEVER
            // be visible if the phone is idle!
            showDialpadChooser(false);
        }
        
        prefVibrateOn = ePrefs.getBoolean("dial_enable_haptic", true);
        mVibratePattern = stringToLongArray(Settings.System.getString(getContentResolver(), Settings.System.HAPTIC_TAP_ARRAY));
        retrieveLastDialled = ePrefs.getBoolean("dial_retrieve_last", false);
        returnToDialer = ePrefs.getBoolean("dial_return", false);

        updateDialAndDeleteButtonEnabledState();
        updateDialer();
    }

    /**
     * Hides the topresult layout
     * Needed to reclaim the space when T9 is off.
     */
    private void hideT9 () {
        if (!isT9On()) {
            toggleT9();
            mT9Top.setVisibility(View.GONE);
        } else {
            mT9Top.setVisibility(View.VISIBLE);
        }
        return;
    }

    /**
     * Toggles between expanded list and dialpad
     */
    private void toggleT9() {
        if (mT9Flipper.getCurrentView() == mT9List) {
            mT9Toggle.setChecked(false);
            animateT9();
        }
    }

    /**
     * Initiates a search for the dialed digits
     * Toggles view visibility based on results
     */
    private void searchContacts() {
        if (!isT9On())
            return;
        final int length = mDigits.length();
        if (length > 0) {
            if (sT9Search != null) {
                T9SearchResult result = sT9Search.search(mDigits.getText().toString());
                if (mT9AdapterTop == null) {
                    mT9AdapterTop = sT9Search.new T9Adapter(this, 0, new ArrayList<ContactItem>(),getLayoutInflater());
                    mT9AdapterTop.setNotifyOnChange(true);
                } else {
                    mT9AdapterTop.clear();
                }
                if (result != null) {
                    if (mT9Adapter == null) {
                        mT9Adapter = sT9Search.new T9Adapter(this, 0, result.getResults(),getLayoutInflater());
                        mT9Adapter.setNotifyOnChange(true);
                    } else {
                        mT9Adapter.clear();
                        for (ContactItem item : result.getResults()) {
                            mT9Adapter.add(item);
                        }
                    }
                    if (mT9List.getAdapter() == null) {
                        mT9List.setAdapter(mT9Adapter);
                    }

                    mT9AdapterTop.add(result.getTopContact());
                    if (result.getNumResults()>  1) {
                        mT9Toggle.setVisibility(View.VISIBLE);
                    } else {
                        mT9Toggle.setVisibility(View.GONE);
                        toggleT9();
                    }
                    mT9Toggle.setTag(null);
                } else {
                    ContactItem contact = (ContactItem) mT9ListTop.getTag();
                    contact.number = mDigits.getText().toString();
                    mT9AdapterTop.add(contact);
                    mT9Toggle.setTag(new Boolean(true));
                    mT9Toggle.setVisibility(View.GONE);
                    toggleT9();
                }
                mT9ListTop.setVisibility(View.VISIBLE);
                if (mT9ListTop.getAdapter() == null) {
                    mT9ListTop.setAdapter(mT9AdapterTop);
                }
            }
        } else {
            mT9ListTop.setVisibility(View.INVISIBLE);
            mT9Toggle.setVisibility(View.INVISIBLE);
            toggleT9();
        }
    }

    /**
     * Returns preference value for T9Dialer
     */
    private boolean isT9On() {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean("t9_state", true);
    }

    /**
     * Returns preference for whether to dial
     * upon clicking contact in listview/topbar
     */
    private boolean dialOnTap() {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean("t9_dial_onclick", false);
    }

    /**
     * Animates the dialpad/listview
     */
    private void animateT9() {
        TranslateAnimation slidedown1 = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 1.0f);
        TranslateAnimation slidedown2 = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, -1.0f, Animation.RELATIVE_TO_PARENT, 0.0f);
        TranslateAnimation slideup1 = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, -1.0f);
        TranslateAnimation slideup2 = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 1.0f, Animation.RELATIVE_TO_PARENT, 0.0f);
        slidedown2.setDuration(500);
        slidedown2.setInterpolator(new DecelerateInterpolator());
        slidedown1.setDuration(500);
        slidedown1.setInterpolator(new DecelerateInterpolator());
        slideup1.setDuration(500);
        slideup1.setInterpolator(new DecelerateInterpolator());
        slideup2.setDuration(500);
        slideup2.setInterpolator(new DecelerateInterpolator());
        if (mT9Toggle.isChecked()) {
            mT9Flipper.setOutAnimation(slidedown1);
            mT9Flipper.setInAnimation(slidedown2);
        } else {
            mT9Flipper.setOutAnimation(slideup1);
            mT9Flipper.setInAnimation(slideup2);
        }
        mT9Flipper.showNext();
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            // Hide soft keyboard, if visible (it's fugly over button dialer).
            // The only known case where this will be true is when launching the dialer with
            // ACTION_DIAL via a soft keyboard.  we dismiss it here because we don't
            // have a window token yet in onCreate / onNewIntent
            InputMethodManager inputMethodManager = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(mDigits.getWindowToken(), 0);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Stop listening for phone state changes.
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        telephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);

        synchronized(mToneGeneratorLock) {
            if (mToneGenerator != null) {
                mToneGenerator.release();
                mToneGenerator = null;
            }
        }
        // TODO: I wonder if we should not check if the AsyncTask that
        // lookup the last dialed number has completed.
        mLastNumberDialed = EMPTY_NUMBER;  // Since we are going to query again, free stale number.
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        m2SecPauseMenuItem = menu.add(0, MENU_2S_PAUSE, 0, R.string.add_2sec_pause)
                .setIcon(R.drawable.ic_menu_2sec_pause);
        mWaitMenuItem = menu.add(0, MENU_WAIT, 0, R.string.add_wait)
                .setIcon(R.drawable.ic_menu_wait);
        mAddToContactMenuItem = menu.add(0, MENU_ADD_CONTACTS, 0, R.string.recentCalls_addToContact)
                .setIcon(android.R.drawable.ic_menu_add);
        mSmsMenuItem = menu.add(0, MENU_SMS, 0, R.string.dialer_menu_sms)
                .setIcon(R.drawable.ic_menu_smsmms);
        mPreferences = menu.add(0, MENU_PREFERENCES, 0, R.string.menu_preferences)
                .setIcon(android.R.drawable.ic_menu_preferences);
        //Wysie_Soh: Preferences intent
        mPreferences.setIntent(new Intent(this, ContactsPreferences.class));
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // We never show a menu if the "choose dialpad" UI is up.
        if (dialpadChooserVisible()) {
            return false;
        }

        CharSequence digits = mDigits.getText();
        if ((isDigitsEmpty() || !TextUtils.isGraphic(digits)) && !ePrefs.getBoolean("dial_disable_num_check", false)) {
            mAddToContactMenuItem.setVisible(false);
            m2SecPauseMenuItem.setVisible(false);
            mWaitMenuItem.setVisible(false);
            mSmsMenuItem.setVisible(false);
        } else {

            // Put the current digits string into an intent
            /*
            Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            intent.putExtra(Insert.PHONE, digits);
            intent.setType(People.CONTENT_ITEM_TYPE);
            mAddToContactMenuItem.setIntent(intent);
            */
            mAddToContactMenuItem.setVisible(true);

            // Check out whether to show Pause & Wait option menu items
            int selectionStart;
            int selectionEnd;
            String strDigits = digits.toString();

            selectionStart = mDigits.getSelectionStart();
            selectionEnd = mDigits.getSelectionEnd();

            if (selectionStart != -1) {
                if (selectionStart > selectionEnd) {
                    // swap it as we want start to be less then end
                    int tmp = selectionStart;
                    selectionStart = selectionEnd;
                    selectionEnd = tmp;
                }

                if (selectionStart != 0) {
                    // Pause can be visible if cursor is not in the begining
                    m2SecPauseMenuItem.setVisible(true);

                    // For Wait to be visible set of condition to meet
                    mWaitMenuItem.setVisible(showWait(selectionStart,
                                                      selectionEnd, strDigits));
                } else {
                    // cursor in the beginning both pause and wait to be invisible
                    m2SecPauseMenuItem.setVisible(false);
                    mWaitMenuItem.setVisible(false);
                }
            } else {
                // cursor is not selected so assume new digit is added to the end
                int strLength = strDigits.length();
                mWaitMenuItem.setVisible(showWait(strLength,
                                                      strLength, strDigits));
            }
            
            if (ePrefs.getString("vm_button", "0").equals("0")) {
                mAddToContactMenuItem.setVisible(false);
            }
            else {
                mAddToContactMenuItem.setVisible(true);
            }
            
            if (ePrefs.getString("vm_button", "0").equals("1")) {
        	    mSmsMenuItem.setVisible(false);
            }
            else {
                mSmsMenuItem.setVisible(true);
			}
		
        }
        
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL: {
                long callPressDiff = SystemClock.uptimeMillis() - event.getDownTime();
                if (callPressDiff >= ViewConfiguration.getLongPressTimeout()) {
                    // Launch voice dialer
                    Intent intent = new Intent(Intent.ACTION_VOICE_COMMAND);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                    }
                }
                return true;
            }
            case KeyEvent.KEYCODE_1: {
                long timeDiff = SystemClock.uptimeMillis() - event.getDownTime();
                if (timeDiff >= ViewConfiguration.getLongPressTimeout()) {
                    // Long press detected, call voice mail
                    callVoicemail();
                }
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL: {
                // TODO: In dialButtonPressed we do some of these
                // tests again. We should try to consolidate them in
                // one place.
                if (!phoneIsCdma() && mIsAddCallMode && isDigitsEmpty()) {
                    // For CDMA phones, we always call
                    // dialButtonPressed() because we may need to send
                    // an empty flash command to the network.
                    // Otherwise, if we are adding a call from the
                    // InCallScreen and the phone number entered is
                    // empty, we just close the dialer to expose the
                    // InCallScreen under it.
                    finish();
                }

                // If we're CDMA, regardless of where we are adding a call from (either
                // InCallScreen or Dialtacts), the user may need to send an empty
                // flash command to the network. So let's call dialButtonPressed() regardless
                // and dialButtonPressed will handle this functionality for us.
                // otherwise, we place the call.
                dialButtonPressed();
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    private void keyPressed(int keyCode) {
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
        mDigits.onKeyDown(keyCode, event);
        searchContacts();
    }

    public boolean onKey(View view, int keyCode, KeyEvent event) {
        switch (view.getId()) {
            case R.id.digits:
                if ((event.getAction() == KeyEvent.ACTION_UP) &&
                    (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    dialButtonPressed();
                    return true;
                }
                searchContacts();
                break;
        }
        return false;
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.one: {
                playTone(ToneGenerator.TONE_DTMF_1);
                keyPressed(KeyEvent.KEYCODE_1);
                break;
            }
            case R.id.two: {
                playTone(ToneGenerator.TONE_DTMF_2);
                keyPressed(KeyEvent.KEYCODE_2);
                break;
            }
            case R.id.three: {
                playTone(ToneGenerator.TONE_DTMF_3);
                keyPressed(KeyEvent.KEYCODE_3);
                break;
            }
            case R.id.four: {
                playTone(ToneGenerator.TONE_DTMF_4);
                keyPressed(KeyEvent.KEYCODE_4);
                break;
            }
            case R.id.five: {
                playTone(ToneGenerator.TONE_DTMF_5);
                keyPressed(KeyEvent.KEYCODE_5);
                break;
            }
            case R.id.six: {
                playTone(ToneGenerator.TONE_DTMF_6);
                keyPressed(KeyEvent.KEYCODE_6);
                break;
            }
            case R.id.seven: {
                playTone(ToneGenerator.TONE_DTMF_7);
                keyPressed(KeyEvent.KEYCODE_7);
                break;
            }
            case R.id.eight: {
                playTone(ToneGenerator.TONE_DTMF_8);
                keyPressed(KeyEvent.KEYCODE_8);
                break;
            }
            case R.id.nine: {
                playTone(ToneGenerator.TONE_DTMF_9);
                keyPressed(KeyEvent.KEYCODE_9);
                break;
            }
            case R.id.zero: {
                playTone(ToneGenerator.TONE_DTMF_0);
                keyPressed(KeyEvent.KEYCODE_0);
                break;
            }
            case R.id.pound: {
                playTone(ToneGenerator.TONE_DTMF_P);
                keyPressed(KeyEvent.KEYCODE_POUND);
                break;
            }
            case R.id.star: {
                playTone(ToneGenerator.TONE_DTMF_S);
                keyPressed(KeyEvent.KEYCODE_STAR);
                break;
            }
            case R.id.deleteButton: {
                keyPressed(KeyEvent.KEYCODE_DEL);
                break;
            }
            case R.id.dialButton: {
                // Call dialButtonPressed() regardless if there is something
                // entered or not.
                // dialButtonPressed() will handle all combinations
                dialButtonPressed();
                break;
            }
            /*
            case R.id.voicemailButton: {
                callVoicemail();
                vibrate();
                return;
            }
            */
            case R.id.digits: {
                if (!isDigitsEmpty()) {
                    mDigits.setCursorVisible(true);
                }
                break;
            }
            case R.id.voicemailButton: {
            	if (ePrefs.getString("vm_button", "0").equals("0")) {
            		addToContacts();
            	}
            	else if (ePrefs.getString("vm_button", "0").equals("1")) {
            		smsToNumber();
            	}
            	else if (ePrefs.getString("vm_button", "0").equals("2")) {
            		callVoicemail();
            	}
            	break;
            }
            case R.id.t9toggle: {
                animateT9();
                return;
            }
        }
        
        //Wysie: Set the "voicemail"/add button to be enabled/disabled according to if any number is displayed   
        checkForNumber();
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (view == mDigits) {
            // hack to prevent soft keyboard from appearing,
            // while still allowing touch interaction, like
            // cursor positioning and selection
            int inType = mDigits.getInputType();                    // back up the input type
            mDigits.setInputType(android.text.InputType.TYPE_NULL); // disable soft input
            mDigits.onTouchEvent(event);                            // call native handler
            mDigits.setInputType(inType);                           // restore input type
            return true;                                            // consume touch event
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            vibrate();
        }
        // always return false, so onClick() is still launched afterwards
        return false;
    }

    public boolean onLongClick(View view) {
        final Editable digits = mDigits.getText();
        int id = view.getId();
        switch (id) {
            case R.id.deleteButton: {
                digits.clear();
                searchContacts();
                //Wysie: Invoke checkForNumber() to disable button
                checkForNumber();
                // TODO: The framework forgets to clear the pressed
                // status of disabled button. Until this is fixed,
                // clear manually the pressed status. b/2133127
                mDelete.setPressed(false);
                return true;
            }
            case R.id.one: {
                if (isDigitsEmpty()) {
                    //callVoicemail();
                    //Wysie
                    if (hasVoicemail()) {
                        if (ePrefs.getBoolean("vm_use_1_for_regular_vm", false)) {
                            Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED, Uri.fromParts("voicemail", "", null));
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                        else {
                        	callVoicemail();
                        }
                    	return true;
                    }
                    return true;
                }
                return false;
            }
            case R.id.zero: {
                keyPressed(KeyEvent.KEYCODE_PLUS);
                return true;
            }
        }
        return false;
    }
    /*
    void callVoicemail() {
        StickyTabs.saveTab(this, getIntent());
        Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                Uri.fromParts("voicemail", EMPTY_NUMBER, null));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        mDigits.getText().clear();
        finish();
    }
    */
    
    //Wysie
    void callVoicemail() {
        Intent intent;
        String vmHandler = ePrefs.getString("vm_handler", "0");
        if (vmHandler.equals("0")) {
            intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                        Uri.fromParts("voicemail", "", null));
        } else {
            String[] cmp = vmHandler.split("/");
            intent = new Intent(Intent.ACTION_MAIN);
            Log.d(TAG, vmHandler);
            ComponentName component = new ComponentName(cmp[0], cmp[0] + cmp[1]);
            intent.setComponent(component);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        mDigits.getText().clear();
        if (!returnToDialer) {
            finish();
        }
    }

    void dialButtonPressed() {
        final String number = mDigits.getText().toString();
        boolean sendEmptyFlash = false;
        Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED);

        if (isDigitsEmpty()) { // There is no number entered.
            if (phoneIsCdma() && phoneIsOffhook()) {
                // On CDMA phones, if we're already on a call, pressing
                // the Dial button without entering any digits means "send
                // an empty flash."
                intent.setData(Uri.fromParts("tel", EMPTY_NUMBER, null));
                intent.putExtra(EXTRA_SEND_EMPTY_FLASH, true);
                sendEmptyFlash = true;
            } else if (retrieveLastDialled && !TextUtils.isEmpty(mLastNumberDialed)) {
                // Otherwise, pressing the Dial button without entering
                // any digits means "recall the last number dialed".
                // Only if set in options.
                mDigits.setText(mLastNumberDialed);
                mDigits.setSelection(mDigits.length());
                searchContacts();
                return;
            } else {
                // Rare case: there's no "last number dialed".  There's
                // nothing useful for the Dial button to do in this case.
                playTone(ToneGenerator.TONE_PROP_NACK);
                return;
            }
        } else {  // There is a number.
            intent.setData(Uri.fromParts("tel", number, null));
        }

        StickyTabs.saveTab(this, getIntent());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        mDigits.getText().clear();

        // Don't finish TwelveKeyDialer yet if we're sending a blank flash for CDMA. CDMA
        // networks use Flash messages when special processing needs to be done, mainly for
        // 3-way or call waiting scenarios. Presumably, here we're in a special 3-way scenario
        // where the network needs a blank flash before being able to add the new participant.
        // (This is not the case with all 3-way calls, just certain CDMA infrastructures.)
        if (!sendEmptyFlash && !returnToDialer) {
            finish();
        }
    }


    /**
     * Plays the specified tone for TONE_LENGTH_MS milliseconds.
     *
     * The tone is played locally, using the audio stream for phone calls.
     * Tones are played only if the "Audible touch tones" user preference
     * is checked, and are NOT played if the device is in silent mode.
     *
     * @param tone a tone code from {@link ToneGenerator}
     */
    void playTone(int tone) {
        // if local tone playback is disabled, just return.
        if (!mDTMFToneEnabled) {
            return;
        }

        // Also do nothing if the phone is in silent mode.
        // We need to re-check the ringer mode for *every* playTone()
        // call, rather than keeping a local flag that's updated in
        // onResume(), since it's possible to toggle silent mode without
        // leaving the current activity (via the ENDCALL-longpress menu.)
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int ringerMode = audioManager.getRingerMode();
        if ((ringerMode == AudioManager.RINGER_MODE_SILENT)
            || (ringerMode == AudioManager.RINGER_MODE_VIBRATE)) {
            return;
        }

        synchronized(mToneGeneratorLock) {
            if (mToneGenerator == null) {
                Log.w(TAG, "playTone: mToneGenerator == null, tone: "+tone);
                return;
            }

            // Start the new tone (will stop any playing tone)
            mToneGenerator.startTone(tone, TONE_LENGTH_MS);
        }
    }

    /**
     * Brings up the "dialpad chooser" UI in place of the usual Dialer
     * elements (the textfield/button and the dialpad underneath).
     *
     * We show this UI if the user brings up the Dialer while a call is
     * already in progress, since there's a good chance we got here
     * accidentally (and the user really wanted the in-call dialpad instead).
     * So in this situation we display an intermediate UI that lets the user
     * explicitly choose between the in-call dialpad ("Use touch tone
     * keypad") and the regular Dialer ("Add call").  (Or, the option "Return
     * to call in progress" just goes back to the in-call UI with no dialpad
     * at all.)
     *
     * @param enabled If true, show the "dialpad chooser" instead
     *                of the regular Dialer UI
     */
    private void showDialpadChooser(boolean enabled) {
        if (enabled) {
            // Log.i(TAG, "Showing dialpad chooser!");
            mDigits.setVisibility(View.GONE);
            if (mDialpad != null) mDialpad.setVisibility(View.GONE);
            mVoicemailDialAndDeleteRow.setVisibility(View.GONE);
            mDialpadChooser.setVisibility(View.VISIBLE);

            // Instantiate the DialpadChooserAdapter and hook it up to the
            // ListView.  We do this only once.
            if (mDialpadChooserAdapter == null) {
                mDialpadChooserAdapter = new DialpadChooserAdapter(this);
                mDialpadChooser.setAdapter(mDialpadChooserAdapter);
            }
        } else {
            if (isT9On()) {
                if (mT9Flipper.getCurrentView() != mT9List) {
                    mT9Toggle.setChecked(false);
                    searchContacts();
                } else {
                    return;
                }
            }
            // Log.i(TAG, "Displaying normal Dialer UI.");
            mDigits.setVisibility(View.VISIBLE);
            if (mDialpad != null) mDialpad.setVisibility(View.VISIBLE);
            mVoicemailDialAndDeleteRow.setVisibility(View.VISIBLE);
            mDialpadChooser.setVisibility(View.GONE);
        }
    }

    /**
     * @return true if we're currently showing the "dialpad chooser" UI.
     */
    private boolean dialpadChooserVisible() {
        return mDialpadChooser.getVisibility() == View.VISIBLE;
    }

    /**
     * Simple list adapter, binding to an icon + text label
     * for each item in the "dialpad chooser" list.
     */
    private static class DialpadChooserAdapter extends BaseAdapter {
        private LayoutInflater mInflater;

        // Simple struct for a single "choice" item.
        static class ChoiceItem {
            String text;
            Bitmap icon;
            int id;

            public ChoiceItem(String s, Bitmap b, int i) {
                text = s;
                icon = b;
                id = i;
            }
        }

        // IDs for the possible "choices":
        static final int DIALPAD_CHOICE_USE_DTMF_DIALPAD = 101;
        static final int DIALPAD_CHOICE_RETURN_TO_CALL = 102;
        static final int DIALPAD_CHOICE_ADD_NEW_CALL = 103;

        private static final int NUM_ITEMS = 3;
        private ChoiceItem mChoiceItems[] = new ChoiceItem[NUM_ITEMS];

        public DialpadChooserAdapter(Context context) {
            // Cache the LayoutInflate to avoid asking for a new one each time.
            mInflater = LayoutInflater.from(context);

            // Initialize the possible choices.
            // TODO: could this be specified entirely in XML?

            // - "Use touch tone keypad"
            mChoiceItems[0] = new ChoiceItem(
                    context.getString(R.string.dialer_useDtmfDialpad),
                    BitmapFactory.decodeResource(context.getResources(),
                                                 R.drawable.ic_dialer_fork_tt_keypad),
                    DIALPAD_CHOICE_USE_DTMF_DIALPAD);

            // - "Return to call in progress"
            mChoiceItems[1] = new ChoiceItem(
                    context.getString(R.string.dialer_returnToInCallScreen),
                    BitmapFactory.decodeResource(context.getResources(),
                                                 R.drawable.ic_dialer_fork_current_call),
                    DIALPAD_CHOICE_RETURN_TO_CALL);

            // - "Add call"
            mChoiceItems[2] = new ChoiceItem(
                    context.getString(R.string.dialer_addAnotherCall),
                    BitmapFactory.decodeResource(context.getResources(),
                                                 R.drawable.ic_dialer_fork_add_call),
                    DIALPAD_CHOICE_ADD_NEW_CALL);
        }

        public int getCount() {
            return NUM_ITEMS;
        }

        /**
         * Return the ChoiceItem for a given position.
         */
        public Object getItem(int position) {
            return mChoiceItems[position];
        }

        /**
         * Return a unique ID for each possible choice.
         */
        public long getItemId(int position) {
            return position;
        }

        /**
         * Make a view for each row.
         */
        public View getView(int position, View convertView, ViewGroup parent) {
            // When convertView is non-null, we can reuse it (there's no need
            // to reinflate it.)
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.dialpad_chooser_list_item, null);
            }

            TextView text = (TextView) convertView.findViewById(R.id.text);
            text.setText(mChoiceItems[position].text);

            ImageView icon = (ImageView) convertView.findViewById(R.id.icon);
            icon.setImageBitmap(mChoiceItems[position].icon);

            return convertView;
        }
    }

    /**
     * Handle clicks from the dialpad chooser.
     */
    public void onItemClick(AdapterView parent, View v, int position, long id) {
        if (parent == mT9List || parent == mT9ListTop) {
            if (parent == mT9List) {
                mDigits.setText(mT9Adapter.getItem(position).number);
            } else {
                if (mT9Toggle.getTag() == null) {
                    mDigits.setText(mT9AdapterTop.getItem(position).number);
                } else {
                    addToContacts();
                    return;
                }
            }
            mDigits.setSelection(mDigits.length());
            if (dialOnTap()) {
                dialButtonPressed();
            }
            return;
        }
        DialpadChooserAdapter.ChoiceItem item =
                (DialpadChooserAdapter.ChoiceItem) parent.getItemAtPosition(position);
        int itemId = item.id;
        switch (itemId) {
            case DialpadChooserAdapter.DIALPAD_CHOICE_USE_DTMF_DIALPAD:
                // Log.i(TAG, "DIALPAD_CHOICE_USE_DTMF_DIALPAD");
                // Fire off an intent to go back to the in-call UI
                // with the dialpad visible.
                returnToInCallScreen(true);
                break;

            case DialpadChooserAdapter.DIALPAD_CHOICE_RETURN_TO_CALL:
                // Log.i(TAG, "DIALPAD_CHOICE_RETURN_TO_CALL");
                // Fire off an intent to go back to the in-call UI
                // (with the dialpad hidden).
                returnToInCallScreen(false);
                break;

            case DialpadChooserAdapter.DIALPAD_CHOICE_ADD_NEW_CALL:
                // Log.i(TAG, "DIALPAD_CHOICE_ADD_NEW_CALL");
                // Ok, guess the user really did want to be here (in the
                // regular Dialer) after all.  Bring back the normal Dialer UI.
                showDialpadChooser(false);
                break;

            default:
                Log.w(TAG, "onItemClick: unexpected itemId: " + itemId);
                break;
        }
    }

    /**
     * Handle long clicks from mT9List and mT9ListTop
     */
    @Override
    public boolean onItemLongClick(AdapterView parent, View v, int position, long id) {
        long contactId;
        if (parent == mT9List) {
            contactId = mT9Adapter.getItem(position).id;
        } else if (mT9Toggle.getTag() == null) {
            contactId = mT9AdapterTop.getItem(position).id;
        } else {
            return false;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri uri = Uri.withAppendedPath(Contacts.CONTENT_URI, String.valueOf(contactId));
        intent.setData(uri);
        startActivity(intent);
        return true;
    }

    /**
     * Returns to the in-call UI (where there's presumably a call in
     * progress) in response to the user selecting "use touch tone keypad"
     * or "return to call" from the dialpad chooser.
     */
    private void returnToInCallScreen(boolean showDialpad) {
        try {
            ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
            if (phone != null) phone.showCallScreenWithDialpad(showDialpad);
        } catch (RemoteException e) {
            Log.w(TAG, "phone.showCallScreenWithDialpad() failed", e);
        }

        // Finally, finish() ourselves so that we don't stay on the
        // activity stack.
        // Note that we do this whether or not the showCallScreenWithDialpad()
        // call above had any effect or not!  (That call is a no-op if the
        // phone is idle, which can happen if the current call ends while
        // the dialpad chooser is up.  In this case we can't show the
        // InCallScreen, and there's no point staying here in the Dialer,
        // so we just take the user back where he came from...)
        finish();
    }

    /**
     * @return true if the phone is "in use", meaning that at least one line
     *              is active (ie. off hook or ringing or dialing).
     */
    private boolean phoneIsInUse() {
        boolean phoneInUse = false;
        try {
            ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
            if (phone != null) phoneInUse = !phone.isIdle();
        } catch (RemoteException e) {
            Log.w(TAG, "phone.isIdle() failed", e);
        }
        return phoneInUse;
    }

    /**
     * @return true if the phone is a CDMA phone type
     */
    private boolean phoneIsCdma() {
        boolean isCdma = false;
        try {
            ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
            if (phone != null) {
                isCdma = (phone.getActivePhoneType() == TelephonyManager.PHONE_TYPE_CDMA);
            }
        } catch (RemoteException e) {
            Log.w(TAG, "phone.getActivePhoneType() failed", e);
        }
        return isCdma;
    }

    /**
     * @return true if the phone state is OFFHOOK
     */
    private boolean phoneIsOffhook() {
        boolean phoneOffhook = false;
        try {
            ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
            if (phone != null) phoneOffhook = phone.isOffhook();
        } catch (RemoteException e) {
            Log.w(TAG, "phone.isOffhook() failed", e);
        }
        return phoneOffhook;
    }

    /**
     * Triggers haptic feedback (if enabled) for dialer key presses.
     */
    private synchronized void vibrate() {
        if (!prefVibrateOn) {
            return;
        }
        if (mVibrator == null) {
            mVibrator = new Vibrator();
        }
        if (mVibratePattern.length == 1) {
        	mVibrator.vibrate(mVibratePattern[0]);
        } else {
            mVibrator.vibrate(mVibratePattern, VIBRATE_NO_REPEAT);
        }
    }

    /**
     * Returns true whenever any one of the options from the menu is selected.
     * Code changes to support dialpad options
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_2S_PAUSE:
                updateDialString(",");
                return true;
            case MENU_WAIT:
                updateDialString(";");
                return true;
            case MENU_ADD_CONTACTS:
                addToContacts();
                return true;
            case MENU_SMS:
                smsToNumber();
                return true;
        }
        return false;
    }

    /**
     * Updates the dial string (mDigits) after inserting a Pause character (,)
     * or Wait character (;).
     */
    private void updateDialString(String newDigits) {
        int selectionStart;
        int selectionEnd;

        // SpannableStringBuilder editable_text = new SpannableStringBuilder(mDigits.getText());
        int anchor = mDigits.getSelectionStart();
        int point = mDigits.getSelectionEnd();

        selectionStart = Math.min(anchor, point);
        selectionEnd = Math.max(anchor, point);

        Editable digits = mDigits.getText();
        if (selectionStart != -1 ) {
            if (selectionStart == selectionEnd) {
                // then there is no selection. So insert the pause at this
                // position and update the mDigits.
                digits.replace(selectionStart, selectionStart, newDigits);
            } else {
                digits.replace(selectionStart, selectionEnd, newDigits);
                // Unselect: back to a regular cursor, just pass the character inserted.
                mDigits.setSelection(selectionStart + 1);
            }
        } else {
            int len = mDigits.length();
            digits.replace(len, len, newDigits);
        }
    }

    /**
     * Update the enabledness of the "Dial" and "Backspace" buttons if applicable.
     */
    private void updateDialAndDeleteButtonEnabledState() {
        final boolean digitsNotEmpty = !isDigitsEmpty();

        if (mDialButton != null) {
            // On CDMA phones, if we're already on a call, we *always*
            // enable the Dial button (since you can press it without
            // entering any digits to send an empty flash.)
            if (phoneIsCdma() && phoneIsOffhook()) {
                mDialButton.setEnabled(true);
            } else {
                // Common case: GSM, or CDMA but not on a call.
                // Enable the Dial button if some digits have
                // been entered, or if there is a last dialed number
                // that could be redialed.
                mDialButton.setEnabled(digitsNotEmpty ||
                                       !TextUtils.isEmpty(mLastNumberDialed));
            }
        }
        mDelete.setEnabled(digitsNotEmpty);
    }


    /**
     * Check if voicemail is enabled/accessible.
     */
    //Wysie: Some changes here to allow left action button to be set to other images
    private void initVoicemailButton() {
    	mVoicemailButton = (ImageButton)mVoicemailDialAndDeleteRow.findViewById(R.id.voicemailButton);
    	mVoicemailButton.setOnClickListener(this);
    	mVoicemailButton.setOnTouchListener(this);
    	
    	if (ePrefs.getString("vm_button", "0").equals("0")) {
    		mVoicemailButton.setImageResource(R.drawable.sym_action_add);
    		//mVoicemailButton.setImageResource(R.drawable.sym_action_sms);
    	}
    	else if (ePrefs.getString("vm_button", "0").equals("1")) { //Wysie_Soh: startsWith changed to equals
            mVoicemailButton.setImageResource(R.drawable.sym_action_sms);
    	}
    	else if (ePrefs.getString("vm_button", "0").equals("2")) {
    		mVoicemailButton.setImageResource(R.drawable.ic_dial_action_voice_mail);
    	}
    }

    /**
     * This function return true if Wait menu item can be shown
     * otherwise returns false. Assumes the passed string is non-empty
     * and the 0th index check is not required.
     */
    private boolean showWait(int start, int end, String digits) {
        if (start == end) {
            // visible false in this case
            if (start > digits.length()) return false;

            // preceding char is ';', so visible should be false
            if (digits.charAt(start-1) == ';') return false;

            // next char is ';', so visible should be false
            if ((digits.length() > start) && (digits.charAt(start) == ';')) return false;
        } else {
            // visible false in this case
            if (start > digits.length() || end > digits.length()) return false;

            // In this case we need to just check for ';' preceding to start
            // or next to end
            if (digits.charAt(start-1) == ';') return false;
        }
        return true;
    }

    /**
     * @return true if the widget with the phone number digits is empty.
     */
    private boolean isDigitsEmpty() {
        return mDigits.length() == 0;
    }

    /**
     * Starts the asyn query to get the last dialed/outgoing
     * number. When the background query finishes, mLastNumberDialed
     * is set to the last dialed number or an empty string if none
     * exists yet.
     */
    private void queryLastOutgoingCall() {
        mLastNumberDialed = EMPTY_NUMBER;
        CallLogAsync.GetLastOutgoingCallArgs lastCallArgs =
                new CallLogAsync.GetLastOutgoingCallArgs(
                    this,
                    new CallLogAsync.OnLastOutgoingCallComplete() {
                        public void lastOutgoingCall(String number) {
                            // TODO: Filter out emergency numbers if
                            // the carrier does not want redial for
                            // these.
                            mLastNumberDialed = number;
                            updateDialAndDeleteButtonEnabledState();
                        }
                    });
        mCallLog.getLastOutgoingCall(lastCallArgs);
    }

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData,
            boolean globalSearch) {
        if (globalSearch) {
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
        } else {
            ContactsSearchManager.startSearch(this, initialQuery);
        }
    }
    
    //Wysie
    private void smsToNumber() {
    	Intent sendIntent = new Intent(Intent.ACTION_VIEW);
    	sendIntent.putExtra("sms_body", "");
    	sendIntent.putExtra("address", mDigits.getText().toString());    	
    	sendIntent.setType("vnd.android-dir/mms-sms");
    	startActivity(sendIntent); 
    }
    
    //Wysie    
    private void addToContacts() {
    	// Put the current digits string into an intent
    	Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
    	intent.putExtra(Insert.PHONE, mDigits.getText().toString());
    	intent.setType(Contacts.CONTENT_ITEM_TYPE);
    	startActivity(intent);
    }
    
    //Wysie
    private void updateDialer() {
    	initVoicemailButton();
    	checkForNumber();
    	setDigitsColor();

        // The voicemail number might have been set after the app was started
        if (mHasVoicemail != hasVoicemail()) {
            setupKeypad(false);
        }
    }    
    
    //Wysie: Method to check if there's any number entered
    private void checkForNumber() {        
        CharSequence digits = mDigits.getText();
        if ((digits == null || !TextUtils.isGraphic(digits)) && !ePrefs.getBoolean("dial_disable_num_check", false)) {
            if (ePrefs.getString("vm_button", "0").equals("0") || ePrefs.getString("vm_button", "0").equals("1")) {
                mVoicemailButton.setEnabled(false);
            }
        } else {
    		    mVoicemailButton.setEnabled(true);
        }
        
        //Wysie: Voicemail button will be enabled/disabled regardless if there are digits or not
        //It's based on whether the user has a voicemail number configured
        if (ePrefs.getString("vm_button", "0").equals("2")) {
            if (ePrefs.getString("title_vm_handler", "0").equals("0")) {
                if (hasVoicemail()) {
                    mVoicemailButton.setEnabled(true);
                } else {
                    mVoicemailButton.setEnabled(false);
                }
            }
            else {
                mVoicemailButton.setEnabled(true);
            }
        }
    }
    
    //Wysie: Method to set digits colour
    private void setDigitsColor() {
        int colorPressed = -16777216;
        int colorFocused = -1;
        int colorUnselected = -1;
        
        colorPressed = ePrefs.getInt("pressed_digits_color", colorPressed);
        colorFocused = ePrefs.getInt("focused_digits_color", colorFocused);
        colorUnselected = ePrefs.getInt("unselected_digits_color", colorUnselected);
    
        mDigits.setTextColor(new ColorStateList(
                     new int[][] {
                             new int[] { android.R.attr.state_pressed },
                             new int[] { android.R.attr.state_focused },
                             new int[0]},
                     
                             new int[] { colorPressed, colorFocused, colorUnselected }
                     ));
        mDigits.setCursorVisible(false);
    }
    
    //Wysie: Check for voicemail number    
    private boolean hasVoicemail() {
    	boolean hasVoicemail = false;    		
    	
    	try {
    		String num = TelephonyManager.getDefault().getVoiceMailNumber();
    		
    		//Wysie_Soh: Bugfix for 1.52. Important. Caused FCs on many people cause num.equals was called first
    		//resulting in a nullpointerexception. added the exception handler as well, but it's not actually needed
    		//since we now check for num == null first :).
    		if (!(num == null || num.equals(""))) 
    			hasVoicemail = true;
    	} catch (SecurityException se) {
    		// Possibly no READ_PHONE_STATE privilege.
    	} catch (NullPointerException e) {
    		//
    	}
    	
    	return hasVoicemail;
    }
    
    private String getLastDialedNumber() {
        final String[] PROJECTION = new String[] {
            Calls.NUMBER
        };
        Cursor c = getContentResolver().query(Calls.CONTENT_URI, PROJECTION, null, null, Calls.DEFAULT_SORT_ORDER);
        String num = "";
        if (c != null) {
            if (c.moveToFirst()) {
                num = c.getString(0);
            }
            c.close();
        }
        
        return num;
    }
    
    private long[] stringToLongArray(String inpString) {
        if (inpString == null) {
            long[] returnLong = new long[1];
            returnLong[0] = 0;
            return returnLong;
        }
        String[] splitStr = inpString.split(",");
        int los = splitStr.length;
        long[] returnLong = new long[los];
        int i;
        for (i=0; i < los; i++ ) {
            returnLong[i] = Long.parseLong(splitStr[i].trim());
        }
        return returnLong;
    }
    
}
