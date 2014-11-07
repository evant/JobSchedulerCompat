package me.tatarka.support.job;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.support.v4.net.ConnectivityManagerCompat;
import android.util.Log;
import android.util.SparseArray;

import java.util.List;

/**
 * @hide *
 */
public class JobServiceCompat extends IntentService {
    static final String TAG = "JobServiceCompat";

    private static final String EXTRA_MSG = "EXTRA_MSG";
    private static final String EXTRA_JOB = "EXTRA_JOB";
    private static final String EXTRA_JOB_ID = "EXTRA_JOB_ID";
    private static final String EXTRA_RUN_IMMEDIATELY = "EXTRA_RUN_IMMEDIATELY";
    private static final String EXTRA_START_TIME = "EXTRA_START_TIME";
    private static final String EXTRA_NUM_FAILURES = "EXTRA_NUM_FAILURES";

    private static final int MSG_SCHEDULE_JOB = 0;
    private static final int MSG_RESCHEDULE_JOB = 1;
    private static final int MSG_CANCEL_JOB = 2;
    private static final int MSG_REQUIRED_STATE_CHANGED = 3;
    private static final int MSG_CHECK_JOB_READY = 4;

    private AlarmManager am;

    public JobServiceCompat() {
        super("JobServiceCompat");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        int msg = intent.getIntExtra(EXTRA_MSG, -1);
        switch (msg) {
            case MSG_SCHEDULE_JOB: {
                JobInfo job = intent.getParcelableExtra(EXTRA_JOB);
                handleSchedule(job);
                break;
            }
            case MSG_RESCHEDULE_JOB: {
                JobInfo job = intent.getParcelableExtra(EXTRA_JOB);
                int numFailures = intent.getIntExtra(EXTRA_NUM_FAILURES, 0);
                handleReschedule(job, numFailures);
                break;
            }
            case MSG_CANCEL_JOB: {
                int jobId = intent.getIntExtra(EXTRA_JOB_ID, 0);
                handleCancelJob(jobId);
                break;
            }
            case MSG_REQUIRED_STATE_CHANGED: {
                handleRequiredStateChanged(intent);
                break;
            }
            case MSG_CHECK_JOB_READY: {
                JobInfo job = intent.getParcelableExtra(EXTRA_JOB);
                long startTime = intent.getLongExtra(EXTRA_START_TIME, 0);
                int numFailures = intent.getIntExtra(EXTRA_NUM_FAILURES, 0);
                boolean runImmediately = intent.getBooleanExtra(EXTRA_RUN_IMMEDIATELY, false);
                handleCheckJobReady(job, startTime, numFailures, runImmediately);
                break;
            }
        }
    }

    private void handleSchedule(JobInfo job) {
        JobPersister.getInstance(this).addPendingJob(job);

        long startTime = SystemClock.elapsedRealtime();

        if (job.hasEarlyConstraint()) {
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, startTime + job.getMinLatencyMillis(), toPendingIntent(job, startTime, 0, false));
        } else if (job.hasLateConstraint()) {
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, startTime + job.getMaxExecutionDelayMillis(), toPendingIntent(job, startTime, 0, true));
        } else {
            // Just create a PendingIntent to store the job.
            toPendingIntent(job, startTime, 0, false);
        }

        if (job.getNetworkType() != JobInfo.NETWORK_TYPE_NONE) {
            ReceiverUtils.enable(this, NetworkReceiver.class);
        }

        if (job.isRequireCharging()) {
            ReceiverUtils.enable(this, PowerReceiver.class);
        }
    }

    private void handleReschedule(JobInfo job, int numFailures) {
        if (job.isRequireDeviceIdle()) {
            // TODO: different reschedule policy
            throw new UnsupportedOperationException("rescheduling idle tasks is not yet implemented");
        }

        long backoffTime;
        switch (job.getBackoffPolicy()) {
            case JobInfo.BACKOFF_POLICY_LINEAR:
                backoffTime = job.getInitialBackoffMillis() * numFailures;
                break;
            case JobInfo.BACKOFF_POLICY_EXPONENTIAL:
                backoffTime = job.getInitialBackoffMillis() * (long) Math.pow(2, numFailures - 1);
                break;
            default:
                throw new IllegalArgumentException("Unknown backoff policy: " + job.getBackoffPolicy());
        }

        if (backoffTime > 5 * 60 * 60 * 1000 /* 5 hours*/) {
            // We have backed-off too long, give up.
            return;
        }

        long startTime = SystemClock.elapsedRealtime();
        am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, startTime + backoffTime, toPendingIntent(job, startTime, numFailures, true));
    }

    private void handleCancelJob(int jobId) {
        unscheduleJob(jobId);
        JobSchedulerService.stopJob(this, jobId);
    }

    private void handleCheckJobReady(final JobInfo job, long startTime, int numFailures, boolean runImmediately) {
        boolean hasRequiredNetwork = hasRequiredNetwork(job);
        boolean hasRequiredPowerState = hasRequiredPowerState(job);

        if (runImmediately || (hasRequiredNetwork && hasRequiredPowerState)) {
            unscheduleJob(job.getId());
            JobSchedulerService.startJob(this, job, startTime, numFailures);
        } else {
            if (!hasRequiredNetwork) {
                ReceiverUtils.enable(this, NetworkReceiver.class);
            }

            if (!hasRequiredPowerState) {
                ReceiverUtils.enable(this, PowerReceiver.class);
            }

            if (job.hasLateConstraint()) {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, startTime + job.getMaxExecutionDelayMillis(), toPendingIntent(job, startTime, numFailures, true));
            } else {
                // Ensure we have a pending intent for when required state changes.
                toPendingIntent(job, startTime, numFailures, false);
            }
        }
    }

    private void unscheduleJob(int jobId) {
        JobPersister.getInstance(this).removePendingJob(jobId);
        PendingIntent pendingIntent = toExistingPendingIntent(jobId);
        if (pendingIntent != null) {
            am.cancel(pendingIntent);
        }
    }

    private boolean hasRequiredNetwork(JobInfo job) {
        if (job.getNetworkType() == JobInfo.NETWORK_TYPE_NONE) {
            return true;
        }

        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        boolean connected = netInfo != null && netInfo.isConnectedOrConnecting();
        return connected
                && (job.getNetworkType() != JobInfo.NETWORK_TYPE_UNMETERED
                || !ConnectivityManagerCompat.isActiveNetworkMetered(cm));
    }

    private boolean hasRequiredPowerState(JobInfo job) {
        if (!job.isRequireCharging()) {
            return true;
        }

        Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        return plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB;
    }

    private void handleRequiredStateChanged(Intent intent) {
        List<JobInfo> pendingJobs = JobPersister.getInstance(this).getPendingJobs();
        for (JobInfo job : pendingJobs) {
            PendingIntent pendingIntent = toExistingPendingIntent(job.getId());
            if (pendingIntent != null) {
                try {
                    pendingIntent.send();
                } catch (PendingIntent.CanceledException e) {
                    // Ignore, has already been canceled.
                }
            }
        }

        // TODO: acquire own wake lock for these intents that were just sent.
        WakefulBroadcastReceiver.completeWakefulIntent(intent);
    }

    private PendingIntent toPendingIntent(JobInfo job, long startTime, int numFailures, boolean runImmediately) {
        Intent intent = new Intent(this, JobServiceCompat.class)
                .setAction(Integer.toString(job.getId()))
                .putExtra(EXTRA_MSG, MSG_CHECK_JOB_READY)
                .putExtra(EXTRA_JOB, job)
                .putExtra(EXTRA_START_TIME, startTime)
                .putExtra(EXTRA_NUM_FAILURES, numFailures)
                .putExtra(EXTRA_RUN_IMMEDIATELY, runImmediately);
        return PendingIntent.getService(this, job.getId(), intent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    private PendingIntent toExistingPendingIntent(int jobId) {
        Intent intent = new Intent(this, JobServiceCompat.class)
                .setAction(Integer.toString(jobId));
        return PendingIntent.getService(this, jobId, intent, PendingIntent.FLAG_NO_CREATE);
    }

    static void schedule(Context context, JobInfo job) {
        context.startService(
                new Intent(context, JobServiceCompat.class)
                        .putExtra(EXTRA_MSG, MSG_SCHEDULE_JOB)
                        .putExtra(EXTRA_JOB, job));
    }

    static void reschedule(Context context, JobInfo job, int numFailures) {
        context.startService(
                new Intent(context, JobServiceCompat.class)
                        .putExtra(EXTRA_MSG, MSG_RESCHEDULE_JOB)
                        .putExtra(EXTRA_JOB, job)
                        .putExtra(EXTRA_NUM_FAILURES, numFailures));
    }

    static void cancel(Context context, int jobId) {
        context.startService(
                new Intent(context, JobServiceCompat.class)
                        .putExtra(EXTRA_MSG, MSG_CANCEL_JOB)
                        .putExtra(EXTRA_JOB_ID, jobId));
    }

    static Intent requiredStateChangedIntent(Context context) {
        return new Intent(context, JobServiceCompat.class)
                .putExtra(EXTRA_MSG, MSG_REQUIRED_STATE_CHANGED);
    }
}
