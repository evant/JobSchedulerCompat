package me.tatarka.support.job;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;

/** @hide **/
public class JobServiceCompat extends Service {
    static final String TAG = "JobServiceCompat";

    private static final String EXTRA_MSG = "EXTRA_MSG";
    private static final String EXTRA_JOB = "EXTRA_JOB";
    private static final String EXTRA_JOB_ID = "EXTRA_JOB_ID";

    private static final int MSG_SCHEDULE_JOB = 0;
    private static final int MSG_START_JOB = 1;
    private static final int MSG_CANCEL_JOB = 2;

    private AlarmManager am;
    private SparseArray<PendingIntent> scheduledJobs = new SparseArray<PendingIntent>();
    private SparseArray<JobServiceConnection> runningJobs = new SparseArray<JobServiceConnection>();

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
                handleStartJob(job);
                break;
            } case MSG_CANCEL_JOB:
                int jobId = intent.getIntExtra(EXTRA_JOB_ID, 0);
                handleCancelJob(jobId);
                break;
        }

        return START_NOT_STICKY;
    }

    private void handleSchedule(JobInfo job, int startId) {
        // TODO: crazy complex logic to handle scheduling of jobs.
        PendingIntent pendingIntent = toPendingIntent(job);
        scheduledJobs.put(job.getId(), pendingIntent);

        if (job.isPeriodic()) {
            am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, 0, job.getIntervalMillis(), pendingIntent);
        } else {
            throw new UnsupportedOperationException("Not yet implemented!");
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

    private void handleStartJob(final JobInfo job) {
        Intent intent = new Intent();
        intent.setComponent(job.getService());

        int flags = BIND_AUTO_CREATE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            flags |= BIND_WAIVE_PRIORITY;
        }

        JobServiceConnection connection = new JobServiceConnection(job);
        runningJobs.put(job.getId(), connection);
        bindService(intent, connection, flags);
    }

    private PendingIntent toPendingIntent(JobInfo job) {
        Intent intent = new Intent(this, JobServiceCompat.class)
                .putExtra(EXTRA_MSG, MSG_START_JOB)
                .putExtra(EXTRA_JOB, job);
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
            unbindService(connection);
            runningJobs.remove(jobId);

            if (runningJobs.size() == 0) {
                stopSelf();
            }
        }
    }
}
