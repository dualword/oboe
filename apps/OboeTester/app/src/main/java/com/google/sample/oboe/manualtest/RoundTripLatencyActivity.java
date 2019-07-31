/*
 * Copyright 2018 The Android Open Source Project
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

package com.google.sample.oboe.manualtest;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Activity to measure latency on a full duplex stream.
 */
public class RoundTripLatencyActivity extends AnalyzerActivity {

    private static final int STATE_GOT_DATA = 3; // Defined in LatencyAnalyzer.h

    private TextView mAnalyzerView;
    private Button mMeasureButton;
    private Button mCancelButton;
    private Button mShareButton;

    // Periodically query the status of the stream.
    protected class LatencySniffer {
        public static final int SNIFFER_UPDATE_PERIOD_MSEC = 150;
        public static final int SNIFFER_UPDATE_DELAY_MSEC = 300;

        private Handler mHandler = new Handler(Looper.getMainLooper()); // UI thread

        // Display status info for the stream.
        private Runnable runnableCode = new Runnable() {
            @Override
            public void run() {
                String message = getProgressText();
                if (getAnalyzerState() == STATE_GOT_DATA) {
                    message += "Analyzing - please wait...\n";
                }
                setAnalyzerText(message);

                if (isAnalyzerDone()) {
                    onAnalyzerDone();
                } else {
                    // Repeat this runnable code block again.
                    mHandler.postDelayed(runnableCode, SNIFFER_UPDATE_PERIOD_MSEC);
                }
            }
        };

        private void startSniffer() {
            // Start the initial runnable task by posting through the handler
            mHandler.postDelayed(runnableCode, SNIFFER_UPDATE_DELAY_MSEC);
        }

        private void stopSniffer() {
            if (mHandler != null) {
                mHandler.removeCallbacks(runnableCode);
            }
        }
    }

    private String getProgressText() {
        int progress = getAnalyzerProgress();
        int state = getAnalyzerState();
        int resetCount = getResetCount();
        return String.format("progress = %d, state = %d, #resets = %d\n",
                progress, state, resetCount);
    }

    private void onAnalyzerDone() {
        int result = getMeasuredResult();
        int latencyFrames = getMeasuredLatency();
        double confidence = getMeasuredConfidence();
        double latencyMillis = latencyFrames * 1000.0 / getSampleRate();
        String message = getProgressText();
        message += String.format("RMS: signal = %7.5f, noise = %7.5f\n",
                getSignalRMS(), getBackgroundRMS());
        message += String.format("result = %d = %s\n", result, resultCodeToString(result));
        if (result == 0) {

            // Don't report bogus latencies.
            message += String.format("latency = %6d frames = %6.2f msec\n",
                    latencyFrames, latencyMillis);
        }
        message += String.format("confidence = %6.3f", confidence);

        setAnalyzerText(message);

        mMeasureButton.setEnabled(true);

        stopAudioTest();
    }

    private LatencySniffer mLatencySniffer = new LatencySniffer();

    native int getAnalyzerProgress();
    native int getMeasuredLatency();
    native double getMeasuredConfidence();
    native double getBackgroundRMS();
    native double getSignalRMS();

    private void setAnalyzerText(String s) {
        mAnalyzerView.setText(s);
    }

    @Override
    protected void inflateActivity() {
        setContentView(R.layout.activity_rt_latency);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMeasureButton = (Button) findViewById(R.id.button_measure);
        mCancelButton = (Button) findViewById(R.id.button_cancel);
        mShareButton = (Button) findViewById(R.id.button_share);
        mShareButton.setEnabled(false);
        mAnalyzerView = (TextView) findViewById(R.id.text_analyzer_result);
        updateEnabledWidgets();

        hideSettingsViews();

        mBufferSizeView.setFaderNormalizedProgress(0.0); // for lowest latency
    }

    @Override
    protected void onStart() {
        super.onStart();
        setActivityType(ACTIVITY_RT_LATENCY);
        mShareButton.setEnabled(false);
    }

    @Override
    protected void onStop() {
        mLatencySniffer.stopSniffer();
        super.onStop();
    }

    public void onMeasure(View view) {
        openAudio();
        startAudio();
        mLatencySniffer.startSniffer();
        mMeasureButton.setEnabled(false);
        mCancelButton.setEnabled(true);
        mShareButton.setEnabled(false);
    }

    public void onCancel(View view) {
        stopAudioTest();
    }

    // Call on UI thread
    public void stopAudioTest() {
        mLatencySniffer.stopSniffer();
        mMeasureButton.setEnabled(true);
        mCancelButton.setEnabled(false);
        mShareButton.setEnabled(true);
        stopAudio();
        closeAudio();
    }

    @Override
    String getWaveTag() {
        return "rtlatency";
    }

    @Override
    boolean isOutput() {
        return false;
    }

    @Override
    public void setupEffects(int sessionId) {
    }
}
