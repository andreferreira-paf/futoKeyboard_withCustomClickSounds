/*
 * Copyright (C) 2012 The Android Open Source Project
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

package org.futo.inputmethod.latin;

import android.content.Context;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.TypedArray;
import android.media.AudioAttributes;
import android.media.AudioManager;
// New imports for custom sound engine
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import java.lang.Integer;
import java.util.HashMap;
import org.futo.inputmethod.latin.R;
import org.futo.inputmethod.latin.common.Constants;
import org.futo.inputmethod.latin.settings.Settings;
import org.futo.inputmethod.latin.settings.SettingsValues;

/**
 * This class gathers audio feedback and haptic feedback functions.
 *
 * It offers a consistent and simple interface that allows LatinIME to forget about the
 * complexity of settings and the like.
 */
public final class AudioAndHapticFeedbackManager {

    private AudioManager mAudioManager;
    private Vibrator mVibrator;

    private SettingsValues mSettingsValues;
    private boolean mSoundOn;
    // New variables for custom sound engine
    private SoundPool mSoundPool;
    private int mLastSelectedProfile;
    private boolean mSoundsLoaded;
    private Context mContext;
    private int mExpectedSoundCount = 0;
    private int mLoadedSoundCount = 0;
    private static final String TAG = "AudioFeedbackManager";
    private int deleteSoundId;
    private int enterSoundId;
    private int spaceSoundId;
    private int[] keypressSoundId;
    private int numberOfUniqueSounds;
    private int mNextSoundIndex = 0; // Tracks the next sound to play

    private static final AudioAndHapticFeedbackManager sInstance =
        new AudioAndHapticFeedbackManager();

    public static AudioAndHapticFeedbackManager getInstance() {
        return sInstance;
    }

    private AudioAndHapticFeedbackManager() {
        // Intentional empty constructor for singleton.
    }

    public static void init(final Context context) {
        sInstance.initInternal(context);
    }

    private void initInternal(final Context context) {
        //  Android Studio calls this line a memory leak, but it's not.
        // it would only be a memory leak if it was storing ACTIVITY context,
        // but it's storing APPLICATION context.
        mContext = context.getApplicationContext();
        mSoundsLoaded = false;
        mAudioManager = (AudioManager) context.getSystemService(
            Context.AUDIO_SERVICE
        );
        mVibrator = (Vibrator) context.getSystemService(
            Context.VIBRATOR_SERVICE
        );

        // Initialize SoundPool
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();
        mSoundPool = new SoundPool.Builder()
            .setMaxStreams(8) // Max simultaneous sounds
            .setAudioAttributes(audioAttributes)
            .build();

        mSoundPool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
            if (status == 0) {
                // success
                mLoadedSoundCount++;
                Log.d(
                    TAG,
                    "Sound loaded: " +
                        sampleId +
                        " (" +
                        mLoadedSoundCount +
                        "/" +
                        mExpectedSoundCount +
                        ")"
                );
                if (mLoadedSoundCount >= mExpectedSoundCount) {
                    mNextSoundIndex = 0;
                    mSoundsLoaded = true;
                    Log.i(TAG, "All sounds loaded successfully");
                }
            } else {
                Log.e(
                    TAG,
                    "Error loading sound " + sampleId + ", status: " + status
                );
            }
        });
    }

    private void loadSoundsForCurrentProfile() {
        mNextSoundIndex = 0;
        mSoundsLoaded = false;
        mExpectedSoundCount = 0;
        mLoadedSoundCount = 0;
        Log.d(
            TAG,
            "Loading sounds for profile: " +
                mSettingsValues.mCustomKeypressSoundsProfile
        );
        // 2. Unload existing sounds from the pool to free memory
        if (mSoundPool != null) {
            if (deleteSoundId != 0) mSoundPool.unload(deleteSoundId);
            if (enterSoundId != 0) mSoundPool.unload(enterSoundId);
            if (spaceSoundId != 0) mSoundPool.unload(spaceSoundId);

            if (keypressSoundId != null) {
                for (int soundId : keypressSoundId) {
                    if (soundId != 0) mSoundPool.unload(soundId);
                }
            }
        }
        try {
            Resources res = mContext.getResources();
            int deleteSoundResId = 0;
            int enterSoundResId = 0;
            int spaceSoundResId = 0;
            int[] keypressSoundResIds;
            TypedArray ta;
            switch (mSettingsValues.mCustomKeypressSoundsProfile) {
                case Settings.BLUE_KEYPRESS_PROFILE: // blue profile
                    Log.i(TAG, "Using 'blue' sound profile.");
                    deleteSoundResId = R.raw.blue_delete;
                    enterSoundResId = R.raw.blue_enter;
                    spaceSoundResId = R.raw.blue_space;
                    ta = res.obtainTypedArray(
                        R.array.blue_keypress_sound_res_ids
                    );
                    keypressSoundResIds = new int[ta.length()];
                    for (int i = 0; i < ta.length(); i++) {
                        keypressSoundResIds[i] = ta.getResourceId(i, 0);
                    }
                    ta.recycle();
                    mLastSelectedProfile = Settings.BLUE_KEYPRESS_PROFILE;
                    break;
                case Settings.RED_KEYPRESS_PROFILE: // red profile
                    Log.i(TAG, "Using 'red' sound profile.");
                    deleteSoundResId = R.raw.red_delete;
                    enterSoundResId = R.raw.red_enter;
                    spaceSoundResId = R.raw.red_space;
                    ta = res.obtainTypedArray(
                        R.array.red_keypress_sound_res_ids
                    );
                    keypressSoundResIds = new int[ta.length()];
                    for (int i = 0; i < ta.length(); i++) {
                        keypressSoundResIds[i] = ta.getResourceId(i, 0);
                    }
                    ta.recycle();
                    mLastSelectedProfile = Settings.RED_KEYPRESS_PROFILE;
                    break;
                default: // Fallback to old method, loading nothing
                    Log.i(TAG, "FALLBACK TO DEFAULT METHOD");
                    keypressSoundResIds = new int[] { 0 };
                    mLastSelectedProfile = Settings.DEFAULT_KEYPRESS_PROFILE;
                    break;
            }
            if (keypressSoundResIds[0] == 0) {
                Log.i(TAG, "Exiting because keypressSoundResIds = 0");
                return;
            }
            mExpectedSoundCount = keypressSoundResIds.length + 3;
            //Loading Sounds

            deleteSoundId = mSoundPool.load(mContext, deleteSoundResId, 1);
            enterSoundId = mSoundPool.load(mContext, enterSoundResId, 1);
            spaceSoundId = mSoundPool.load(mContext, spaceSoundResId, 1);
            numberOfUniqueSounds = keypressSoundResIds.length;

            keypressSoundId = new int[numberOfUniqueSounds];
            int currentIndex = 0;
            for (int i = 0; i < keypressSoundResIds.length; i++) {
                Log.i(TAG, "Loading sound: " + keypressSoundResIds[i]);
                keypressSoundId[i] = mSoundPool.load(
                    mContext,
                    keypressSoundResIds[i],
                    1
                );
            }
        } catch (Resources.NotFoundException e) {
            Log.e(
                TAG,
                "Error loading sounds: A resource was not found for profile " +
                    mSettingsValues.mCustomKeypressSoundsProfile +
                    ". Check your res/raw folder and R class. " +
                    e.getMessage()
            );
            mSoundsLoaded = false;
        } catch (Exception e) {
            Log.e(
                TAG,
                "Unexpected error loading sounds for profile " +
                    mSettingsValues.mCustomKeypressSoundsProfile +
                    ": " +
                    e.getMessage()
            );
            mSoundsLoaded = false;
        }
    }

    public void performHapticAndAudioFeedback(
        final int code,
        final View viewToPerformHapticFeedbackOn
    ) {
        performHapticFeedback(viewToPerformHapticFeedbackOn, false);
        performAudioFeedback(code);
    }

    public boolean hasVibrator() {
        return mVibrator != null && mVibrator.hasVibrator();
    }

    public void vibrate(final long milliseconds) {
        if (mVibrator == null) {
            return;
        }
        mVibrator.vibrate(milliseconds);
    }

    private boolean reevaluateIfSoundIsOn() {
        if (
            mSettingsValues == null ||
            !mSettingsValues.mSoundOn ||
            mAudioManager == null
        ) {
            return false;
        }
        return mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL;
    }

    public void performAudioFeedback(final int code) {
        if (!mSoundOn) {
            return;
        }

        if (mSoundsLoaded) {
            int soundId = 0;

            // Check for special functional keys first
            if (code == Constants.CODE_DELETE) {
                soundId = deleteSoundId;
            } else if (code == Constants.CODE_ENTER) {
                soundId = enterSoundId;
            } else if (code == Constants.CODE_SPACE) {
                soundId = spaceSoundId;
            } else if (keypressSoundId != null && keypressSoundId.length > 0) {
                // Sequential logic for all other keys
                soundId = keypressSoundId[mNextSoundIndex];

                // Advance the index and wrap around using modulo
                mNextSoundIndex =
                    (mNextSoundIndex + 1) % keypressSoundId.length;
            }

            if (soundId != 0) {
                mSoundPool.play(
                    soundId,
                    mSettingsValues.mKeypressSoundVolume,
                    mSettingsValues.mKeypressSoundVolume,
                    1,
                    0,
                    1.0f
                );
                return;
            }
        }

        // if mAudioManager is null, we can't play a sound anyway, so return
        if (mAudioManager == null) {
            return;
        }
        final int sound;
        switch (code) {
            case Constants.CODE_DELETE:
                sound = AudioManager.FX_KEYPRESS_DELETE;
                break;
            case Constants.CODE_ENTER:
                sound = AudioManager.FX_KEYPRESS_RETURN;
                break;
            case Constants.CODE_SPACE:
                sound = AudioManager.FX_KEYPRESS_SPACEBAR;
                break;
            default:
                sound = AudioManager.FX_KEYPRESS_STANDARD;
                break;
        }
        mAudioManager.playSoundEffect(
            sound,
            mSettingsValues.mKeypressSoundVolume
        );
    }

    public void performHapticFeedback(
        final View viewToPerformHapticFeedbackOn,
        final boolean repeatKey
    ) {
        if (!mSettingsValues.mVibrateOn) {
            return;
        }
        if (mSettingsValues.mKeypressVibrationDuration >= 0) {
            vibrate(
                mSettingsValues.mKeypressVibrationDuration / (repeatKey ? 2 : 1)
            );
            return;
        }
        // Go ahead with the system default
        if (viewToPerformHapticFeedbackOn != null) {
            viewToPerformHapticFeedbackOn.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP
            );
        }
    }

    public void onSettingsChanged(final SettingsValues settingsValues) {
        mSettingsValues = settingsValues;
        mSoundOn = reevaluateIfSoundIsOn();
        if (
            mSettingsValues.mCustomKeypressSoundsProfile !=
                mLastSelectedProfile ||
            !mSoundsLoaded
        ) {
            new Handler(Looper.getMainLooper()).post(
                this::loadSoundsForCurrentProfile
            );
        }
    }

    public void onRingerModeChanged() {
        mSoundOn = reevaluateIfSoundIsOn();
    }

    public void release() {
        Log.d(TAG, "Releasing SoundPool.");
        if (mSoundPool != null) {
            mSoundPool.release();
            mSoundPool = null;
        }
        mSoundsLoaded = false;
    }
}
