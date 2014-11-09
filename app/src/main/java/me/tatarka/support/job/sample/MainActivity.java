/*
 * Copyright 2013 The Android Open Source Project
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

package me.tatarka.support.job.sample;

import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import me.tatarka.support.job.JobInfo;
import me.tatarka.support.job.JobParameters;
import me.tatarka.support.job.JobScheduler;
import me.tatarka.support.job.sample.service.TestJobService;

public class MainActivity extends ActionBarActivity {

    private static final String TAG = "MainActivity";

    public static final int MSG_UNCOLOUR_START = 0;
    public static final int MSG_UNCOLOUR_STOP = 1;
    public static final int MSG_SERVICE_OBJ = 2;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sample_main);
        Resources res = getResources();
        defaultColor = res.getColor(R.color.none_received);
        startJobColor = res.getColor(R.color.start_received);
        stopJobColor = res.getColor(R.color.stop_received);

        // Set up UI.
        mShowStartView = (TextView) findViewById(R.id.onstart_textview);
        mShowStopView = (TextView) findViewById(R.id.onstop_textview);
        mParamsTextView = (TextView) findViewById(R.id.task_params);
        mDelayEditText = (EditText) findViewById(R.id.delay_time);
        mDeadlineEditText = (EditText) findViewById(R.id.deadline_time);
        mPeriodEditText = (EditText) findViewById(R.id.period_time);
        mNoConnectivityRadioButton = (RadioButton) findViewById(R.id.checkbox_none);
        mWiFiConnectivityRadioButton = (RadioButton) findViewById(R.id.checkbox_unmetered);
        mAnyConnectivityRadioButton = (RadioButton) findViewById(R.id.checkbox_any);
        mRequiresChargingCheckBox = (CheckBox) findViewById(R.id.checkbox_charging);
        mRequiresIdleCheckbox = (CheckBox) findViewById(R.id.checkbox_idle);
        mPersistedCheckbox = (CheckBox) findViewById(R.id.checkbox_persisted);
        mBackoffDelayEditText = (EditText) findViewById(R.id.backoff_delay_time);
        mBackoffLinearRadioButton = (RadioButton) findViewById(R.id.checkbox_linear);
        mBackoffExponentialRadioButton = (RadioButton) findViewById(R.id.checkbox_exponential);
        mServiceComponent = new ComponentName(this, TestJobService.class);
        // Start service and provide it a way to communicate with us.
        Intent startServiceIntent = new Intent(this, TestJobService.class);
        startServiceIntent.putExtra("messenger", new Messenger(mHandler));
        startService(startServiceIntent);
    }

    // UI fields.
    int defaultColor;
    int startJobColor;
    int stopJobColor;

    private TextView mShowStartView;
    private TextView mShowStopView;
    private TextView mParamsTextView;
    private EditText mDelayEditText;
    private EditText mDeadlineEditText;
    private EditText mPeriodEditText;
    private EditText mBackoffDelayEditText;
    private RadioButton mNoConnectivityRadioButton;
    private RadioButton mWiFiConnectivityRadioButton;
    private RadioButton mAnyConnectivityRadioButton;
    private CheckBox mRequiresChargingCheckBox;
    private CheckBox mRequiresIdleCheckbox;
    private CheckBox mPersistedCheckbox;
    private RadioButton mBackoffLinearRadioButton;
    private RadioButton mBackoffExponentialRadioButton;

    ComponentName mServiceComponent;
    /**
     * Service object to interact scheduled jobs.
     */
    TestJobService mTestService;

    private static int kJobId = 0;

    Handler mHandler = new Handler(/* default looper */) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UNCOLOUR_START:
                    mShowStartView.setBackgroundColor(defaultColor);
                    break;
                case MSG_UNCOLOUR_STOP:
                    mShowStopView.setBackgroundColor(defaultColor);
                    break;
                case MSG_SERVICE_OBJ:
                    mTestService = (TestJobService) msg.obj;
                    mTestService.setUiCallback(MainActivity.this);
            }
        }
    };

    private boolean ensureTestService() {
        if (mTestService == null) {
            Toast.makeText(MainActivity.this, "Service null, never got callback?",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    /**
     * UI onclick listener to schedule a job. What this job is is defined in
     * TestJobService#scheduleJob().
     */
    public void scheduleJob(View v) {
        if (!ensureTestService()) {
            return;
        }

        JobInfo.Builder builder = new JobInfo.Builder(kJobId++, mServiceComponent);

        String delay = mDelayEditText.getText().toString();
        if (delay != null && !TextUtils.isEmpty(delay)) {
            builder.setMinimumLatency(Long.valueOf(delay) * 1000);
        }
        String deadline = mDeadlineEditText.getText().toString();
        if (deadline != null && !TextUtils.isEmpty(deadline)) {
            builder.setOverrideDeadline(Long.valueOf(deadline) * 1000);
        }
        String period = mPeriodEditText.getText().toString();
        if (period != null && !TextUtils.isEmpty(period)) {
            builder.setPeriodic(Long.valueOf(period) * 1000);
        }
        boolean requiresUnmetered = mWiFiConnectivityRadioButton.isChecked();
        boolean requiresAnyConnectivity = mAnyConnectivityRadioButton.isChecked();
        if (requiresUnmetered) {
            builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED);
        } else if (requiresAnyConnectivity) {
            builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        }
        builder.setRequiresDeviceIdle(mRequiresIdleCheckbox.isChecked());
        builder.setRequiresCharging(mRequiresChargingCheckBox.isChecked());
        builder.setPersisted(mPersistedCheckbox.isChecked());

        String backoffTime = mBackoffDelayEditText.getText().toString();
        if (backoffTime != null && !TextUtils.isEmpty(backoffTime)) {
            int backoffPolicy = mBackoffLinearRadioButton.isChecked() ? JobInfo.BACKOFF_POLICY_LINEAR : JobInfo.BACKOFF_POLICY_EXPONENTIAL;
            builder.setBackoffCriteria(Long.valueOf(backoffTime) * 1000, backoffPolicy);
        }

        try {
            mTestService.scheduleJob(builder.build());
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        } catch (IllegalStateException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public void cancelAllJobs(View v) {
        JobScheduler tm = JobScheduler.getInstance(this);
        List<JobInfo> pendingJobs = tm.getAllPendingJobs();
        for (JobInfo job : pendingJobs) {
            Log.d(TAG, "canceled job: " + job.getId());
        }
        tm.cancelAll();
    }

    /**
     * UI onclick listener to call jobFinished() in our service.
     */
    public void finishJob(View v) {
        if (!ensureTestService()) {
            return;
        }
        mTestService.callJobFinished();
        mParamsTextView.setText("");
    }

    public void failJob(View v) {
        if (!ensureTestService()) {
            return;
        }
        mTestService.callJobFailed();
        mParamsTextView.setText("");
    }

    /**
     * Receives callback from the service when a job has landed on the app. Colours the UI and post
     * a message to uncolour it after a second.
     */
    public void onReceivedStartJob(JobParameters params) {
        mShowStartView.setBackgroundColor(startJobColor);
        Message m = Message.obtain(mHandler, MSG_UNCOLOUR_START);
        mHandler.sendMessageDelayed(m, 1000L); // uncolour in 1 second.
        mParamsTextView.setText("Executing: " + params.getJobId() + " " + params.getExtras());
    }

    /**
     * Receives callback from the service when a job that previously landed on the app must stop
     * executing. Colours the UI and post a message to uncolour it after a second.
     */
    public void onReceivedStopJob() {
        mShowStopView.setBackgroundColor(stopJobColor);
        Message m = Message.obtain(mHandler, MSG_UNCOLOUR_STOP);
        mHandler.sendMessageDelayed(m, 2000L); // uncolour in 1 second.
        mParamsTextView.setText("");
    }
}
