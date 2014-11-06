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
public class JobServiceCompat extends Service {
    static final String TAG = "JobServiceCompat";

    private static final String EXTRA_MSG = "EXTRA_MSG";
    private static final String EXTRA_JOB = "EXTRA_JOB";
    private static final String EXTRA_JOB_ID = "EXTRA_JOB_ID";
    private static final String EXTRA_RUN_IMMEDIATELY = "EXTRA_RUN_IMMEDIATELY";
    private static final String EXTRA_START_TIME = "EXTRA_START_TIME";
    private static final String EXTRA_NETWORK_STATE_METERED = "EXTRA_NETWORK_STATE_METERED";
    private static final String EXTRA_POWER_STATE_CONNECTED = "EXTRA_POWER_STATE_CONNECTED";
    private static final String EXTRA_NUM_FAILURES = "EXTRA_NUM_FAILURES";

    private static final int MSG_SCHEDULE_JOB = 0;
    private static final int MSG_START_JOB = 1;
    private static final int MSG_CANCEL_JOB = 2;
    private static final int MSG_REQUIRED_STATE_CHANGED = 3;

    private JobHandler handler;
    private AlarmManager am;

    private SparseArray<JobServiceConnection> runningJobs = new SparseArray<JobServiceConnection>();

    private final Object handlerLock = new Object();

    private void ensureHandler() {
        synchronized (handlerLock) {
            if (handler == null) {
                handler = new JobHandler(getMainLooper());
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureHandler();
        int what = intent.getIntExtra(EXTRA_MSG, -1);
        Message message = Message.obtain(handler, what, intent);
        handler.handleMessage(message);

        return START_NOT_STICKY;
    }

    private class JobHandler extends Handler {
        public JobHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Intent intent = (Intent) msg.obj;
            switch (msg.what) {
                case MSG_SCHEDULE_JOB: {
                    JobInfo job = intent.getParcelableExtra(EXTRA_JOB);
                    handleSchedule(job);
                    break;
                }
                case MSG_START_JOB: {
                    JobInfo job = intent.getParcelableExtra(EXTRA_JOB);
                    long startTime = intent.getLongExtra(EXTRA_START_TIME, 0);
                    int numFailures = intent.getIntExtra(EXTRA_NUM_FAILURES, 0);
                    boolean runImmediately = intent.getBooleanExtra(EXTRA_RUN_IMMEDIATELY, false);
                    handleStartJob(job, startTime, numFailures, runImmediately);
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

        stopIfFinished();
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

        stopIfFinished();
    }

    private void handleCancelJob(int jobId) {
        JobPersister.getInstance(this).removePendingJob(jobId);

        PendingIntent pendingIntent = toExistingPendingIntent(jobId);

        if (pendingIntent != null) {
            am.cancel(pendingIntent);
        }

        JobServiceConnection runningJob = runningJobs.get(jobId);
        if (runningJob != null) {
            runningJob.stop();
        }

        stopIfFinished();
    }

    private void handleStartJob(final JobInfo job, long startTime, int numFailures, boolean runImmediately) {
        boolean hasRequiredNetwork = hasRequiredNetwork(job);
        boolean hasRequiredPowerState = hasRequiredPowerState(job);
        
        if (runImmediately || (hasRequiredNetwork && hasRequiredPowerState)) {
            JobPersister.getInstance(this).removePendingJob(job.getId());

            Intent intent = new Intent();
            intent.setComponent(job.getService());

            int flags = BIND_AUTO_CREATE;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                flags |= BIND_WAIVE_PRIORITY;
            }

            JobServiceConnection connection = new JobServiceConnection(job, numFailures);
            runningJobs.put(job.getId(), connection);
            bindService(intent, connection, flags);
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

            stopIfFinished();
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
                .putExtra(EXTRA_MSG, MSG_START_JOB)
                .putExtra(EXTRA_JOB, job)
                .putExtra(EXTRA_START_TIME, startTime)
                .putExtra(EXTRA_NUM_FAILURES, numFailures)
                .putExtra(EXTRA_RUN_IMMEDIATELY, runImmediately);
        return PendingIntent.getService(this, job.getId(), intent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
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

    private class JobServiceConnection implements ServiceConnection {
        JobInfo job;
        JobParameters jobParams;
        IJobService jobService;

        JobServiceConnection(final JobInfo job, final int numFailures) {
            this.job = job;
            this.jobParams = new JobParameters(new IJobCallback.Stub() {
                @Override
                public void jobFinished(int jobId, boolean needsReschedule) throws RemoteException {
                    finishJob(jobId, JobServiceConnection.this);

                    if (needsReschedule) {
                        handleReschedule(job, numFailures + 1);
                    } else if (runningJobs.size() == 0) {
                        stopSelf();
                    }
                }

                @Override
                public void acknowledgeStartMessage(int jobId, boolean workOngoing) throws RemoteException {
                    if (!workOngoing) {
                        finishJob(jobId, JobServiceConnection.this);
                        if (runningJobs.size() == 0) {
                            stopSelf();
                        }
                    }
                }

                @Override
                public void acknowledgeStopMessage(int jobId, boolean reschedule) throws RemoteException {
                    finishJob(jobId, JobServiceConnection.this);

                    if (reschedule) {
                        handleReschedule(job, numFailures + 1);
                    } else if (runningJobs.size() == 0) {
                        stopSelf();
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
            stopIfFinished();
        }
    }

    private void stopIfFinished() {
        if (runningJobs.size() == 0) {
            stopSelf();
        }
    }
}
