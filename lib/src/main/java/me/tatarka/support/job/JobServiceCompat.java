package me.tatarka.support.job;

import android.app.AlarmManager;
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
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.support.v4.net.ConnectivityManagerCompat;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * @hide *
 */
public class JobServiceCompat extends Service {
    static final String TAG = "JobServiceCompat";

    private static final String EXTRA_MSG = "EXTRA_MSG";
    private static final String EXTRA_JOB = "EXTRA_JOB";
    private static final String EXTRA_JOB_ID = "EXTRA_JOB_ID";
    private static final String EXTRA_RUN_IMMEDIATELY = "EXTRA_RUN_IMMEDIATELY";
    private static final String EXTRA_NETWORK_STATE_METERED = "EXTRA_NETWORK_STATE_METERED";
    private static final String EXTRA_POWER_STATE_CONNECTED = "EXTRA_POWER_STATE_CONNECTED";

    private static final int MSG_SCHEDULE_JOB = 0;
    private static final int MSG_START_JOB = 1;
    private static final int MSG_CANCEL_JOB = 2;
    private static final int MSG_NETWORK_STATE_CHANGED = 3;
    private static final int MSG_POWER_STATE_CHANGED = 4;

    private AlarmManager am;
    // TODO: the job state needs to be persisted in case the service is destroyed and then recreated.
    private SparseArray<PendingIntent> scheduledJobs = new SparseArray<PendingIntent>();
    private SparseArray<JobServiceConnection> runningJobs = new SparseArray<JobServiceConnection>();
    private List<JobInfo> jobsWaitingOnState = new ArrayList<JobInfo>();

    @Override

    public void onCreate() {
        super.onCreate();
        am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        switch (intent.getIntExtra(EXTRA_MSG, -1)) {
            case MSG_SCHEDULE_JOB: {
                JobInfo job = intent.getParcelableExtra(EXTRA_JOB);
                handleSchedule(job, startId);
                break;
            }
            case MSG_START_JOB: {
                JobInfo job = intent.getParcelableExtra(EXTRA_JOB);
                boolean runImmediately = intent.getBooleanExtra(EXTRA_RUN_IMMEDIATELY, false);
                handleStartJob(job, runImmediately);
                break;
            }
            case MSG_CANCEL_JOB: {
                int jobId = intent.getIntExtra(EXTRA_JOB_ID, 0);
                handleCancelJob(jobId);
                break;
            }
            case MSG_NETWORK_STATE_CHANGED: {
                boolean metered = intent.getBooleanExtra(EXTRA_NETWORK_STATE_METERED, false);
                handleNetworkStateChanged(intent, metered);
                break;
            }
            case MSG_POWER_STATE_CHANGED: {
                boolean connected = intent.getBooleanExtra(EXTRA_POWER_STATE_CONNECTED, false);
                handlePowerStateChanged(intent, connected);
                break;
            }
        }

        return START_NOT_STICKY;
    }

    private void handleSchedule(JobInfo job, int startId) {
        PendingIntent pendingIntent = toPendingIntent(job, false);
        scheduledJobs.put(job.getId(), pendingIntent);

        // TODO: crazy complex logic to handle scheduling of jobs.

        long elapsedTime = SystemClock.elapsedRealtime();

        if (job.hasEarlyConstraint()) {
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, elapsedTime + job.getMinLatencyMillis(), toPendingIntent(job, false));
        }

        if (job.hasLateConstraint()) {
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, elapsedTime + job.getMaxExecutionDelayMillis(), toPendingIntent(job, true));
        }

        stopSelfResult(startId);
    }

    private void handleCancelJob(int jobId) {
        PendingIntent pendingIntent = scheduledJobs.get(jobId);
        if (pendingIntent != null) {
            scheduledJobs.remove(jobId);
            am.cancel(pendingIntent);

            JobServiceConnection runningJob = runningJobs.get(jobId);
            if (runningJob != null) {
                runningJob.stop();
            }
        }
    }

    private void handleStartJob(final JobInfo job, boolean runImmediately) {
        if (scheduledJobs.get(job.getId()) == null) {
            return; // Job already run.
        }

        Intent intent = new Intent();
        intent.setComponent(job.getService());

        int flags = BIND_AUTO_CREATE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            flags |= BIND_WAIVE_PRIORITY;
        }

        if (!runImmediately) {
            // Ensure network
            if (job.getNetworkType() != JobInfo.NETWORK_TYPE_NONE) {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                NetworkInfo netInfo = cm.getActiveNetworkInfo();
                boolean hasNecessaryNetwork;
                boolean connected = netInfo != null && netInfo.isConnectedOrConnecting();
                hasNecessaryNetwork = connected
                        && (job.getNetworkType() != JobInfo.NETWORK_TYPE_UNMETERED
                        || !ConnectivityManagerCompat.isActiveNetworkMetered(cm));

                if (!hasNecessaryNetwork) {
                    // Register listener and fire job when network comes back online.
                    jobsWaitingOnState.add(job);
                    ReceiverUtils.enable(this, NetworkReceiver.class);
                    return;
                }
            }

            // Ensure power
            if (job.isRequireCharging()) {
                Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                int plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                boolean isCharging = plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB;

                if (!isCharging) {
                    // Register listener and fire job when device is plugged in.
                    jobsWaitingOnState.add(job);
                    ReceiverUtils.enable(this, PowerReceiver.class);
                    return;
                }
            }
        }

        // Ensure other alarm is canceled
        scheduledJobs.remove(job.getId());
        am.cancel(toPendingIntent(job, true));

        JobServiceConnection connection = new JobServiceConnection(job);
        runningJobs.put(job.getId(), connection);
        jobsWaitingOnState.remove(job);
        bindService(intent, connection, flags);
    }

    private void handleNetworkStateChanged(Intent intent, boolean metered) {
        ArrayList<JobInfo> pendingJobs = new ArrayList<JobInfo>(jobsWaitingOnState);
        jobsWaitingOnState.clear();
        ListIterator<JobInfo> iter = pendingJobs.listIterator();
        while (iter.hasNext()) {
            JobInfo job = iter.next();
            if (job.getNetworkType() == (metered ? JobInfo.NETWORK_TYPE_ANY : JobInfo.NETWORK_TYPE_UNMETERED)) {
                handleStartJob(job, false);
                iter.remove();
            }
        }
        jobsWaitingOnState.addAll(pendingJobs);
        WakefulBroadcastReceiver.completeWakefulIntent(intent);
    }

    private void handlePowerStateChanged(Intent intent, boolean connected) {
        ArrayList<JobInfo> pendingJobs = new ArrayList<JobInfo>(jobsWaitingOnState);
        jobsWaitingOnState.clear();
        ListIterator<JobInfo> iter = pendingJobs.listIterator();
        while (iter.hasNext()) {
            JobInfo job = iter.next();
            if (job.isRequireCharging() && connected) {
                handleStartJob(job, false);
                iter.remove();
            }
        }
        jobsWaitingOnState.addAll(pendingJobs);
        WakefulBroadcastReceiver.completeWakefulIntent(intent);
    }

    private PendingIntent toPendingIntent(JobInfo job, boolean runImmediately) {
        Intent intent = new Intent(this, JobServiceCompat.class)
                .setAction("" + runImmediately)
                .putExtra(EXTRA_MSG, MSG_START_JOB)
                .putExtra(EXTRA_JOB, job)
                .putExtra(EXTRA_RUN_IMMEDIATELY, runImmediately);
        return PendingIntent.getService(this, job.getId(), intent, 0);
    }

    static void schedule(Context context, JobInfo job) {
        context.startService(
                new Intent(context, JobServiceCompat.class)
                        .putExtra(EXTRA_MSG, MSG_SCHEDULE_JOB)
                        .putExtra(EXTRA_JOB, job));
    }

    static void cancel(Context context, int jobId) {
        context.startService(
                new Intent(context, JobServiceCompat.class)
                        .putExtra(EXTRA_MSG, MSG_CANCEL_JOB)
                        .putExtra(EXTRA_JOB_ID, jobId));
    }

    static Intent networkStateChangedIntent(Context context, boolean metered) {
        return new Intent(context, JobServiceCompat.class)
                .putExtra(EXTRA_MSG, MSG_NETWORK_STATE_CHANGED)
                .putExtra(EXTRA_NETWORK_STATE_METERED, metered);
    }

    static Intent powerStateChangedIntent(Context context, boolean connected) {
        return new Intent(context, JobServiceCompat.class)
                .putExtra(EXTRA_MSG, MSG_POWER_STATE_CHANGED)
                .putExtra(EXTRA_POWER_STATE_CONNECTED, connected);
    }

    private class JobServiceConnection implements ServiceConnection {
        JobInfo job;
        JobParameters jobParams;
        IJobService jobService;

        JobServiceConnection(JobInfo job) {
            this.job = job;
            this.jobParams = new JobParameters(new IJobCallback.Stub() {
                @Override
                public void jobFinished(int jobId, boolean needsReschedule) throws RemoteException {
                    finishJob(jobId, JobServiceConnection.this);

                    if (needsReschedule) {
                        // TODO
                    }
                }

                @Override
                public void acknowledgeStartMessage(int jobId, boolean workOngoing) throws RemoteException {
                    if (!workOngoing) {
                        finishJob(jobId, JobServiceConnection.this);
                    }
                }

                @Override
                public void acknowledgeStopMessage(int jobId, boolean reschedule) throws RemoteException {
                    finishJob(jobId, JobServiceConnection.this);

                    if (reschedule) {
                        // TODO
                    }
                }
            }, job.getId(), job.getExtras(), false);
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            jobService = (IJobService) service;

            try {
                jobService.startJob(jobParams);
            } catch (Exception e) {
                Log.e(TAG, "Error while starting job: " + job.getId());
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            runningJobs.remove(job.getId());
            jobService = null;
            jobParams = null;
        }

        void stop() {
            if (jobService != null) {
                try {
                    jobService.stopJob(jobParams);
                } catch (Exception e) {
                    Log.e(TAG, "Error while stopping job: " + job.getId());
                    throw new RuntimeException(e);
                }
            }
        }

        private void finishJob(int jobId, JobServiceConnection connection) {
            if (runningJobs.get(jobId) != null) {
                unbindService(connection);
            }
            runningJobs.remove(jobId);

            if (runningJobs.size() == 0) {
                stopSelf();
            }
        }
    }
}
