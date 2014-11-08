package me.tatarka.support.job;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;

/** @hide */
public class JobSchedulerService extends Service {
    static final String TAG = "JobServiceSchedulerService";

    private static final String EXTRA_MSG = "EXTRA_MSG";
    private static final String EXTRA_JOB = "EXTRA_JOB";
    private static final String EXTRA_JOB_ID = "EXTRA_JOB_ID";
    private static final String EXTRA_NUM_FAILURES = "EXTRA_NUM_FAILURES";
    private static final String EXTRA_RUN_IMMEDIATELY = "EXTRA_RUN_IMMEDIATELY";
    private static final String EXTRA_NETWORK_TYPE = "EXTRA_NETWORK_TYPE";
    private static final String EXTRA_IS_CHARGING = "EXTRA_IS_CHARGING";

    private static final int MSG_START_JOB = 0;
    private static final int MSG_STOP_JOB = 1;
    private static final int MSG_STOP_ALL = 2;
    private static final int MSG_RECHECK_CONSTRAINTS = 3;

    private SparseArray<JobServiceConnection> runningJobs = new SparseArray<JobServiceConnection>();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int what = intent.getIntExtra(EXTRA_MSG, -1);
        Message message = Message.obtain(handler, what, intent);
        handler.handleMessage(message);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void handleStartJob(JobInfo job, int numFailures, boolean runImmediately) {
        Intent intent = new Intent();
        intent.setComponent(job.getService());

        int flags = BIND_AUTO_CREATE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            flags |= BIND_WAIVE_PRIORITY;
        }

        JobServiceConnection connection = new JobServiceConnection(job, numFailures, runImmediately);
        runningJobs.put(job.getId(), connection);
        bindService(intent, connection, flags);
    }

    private void handleStopJob(int jobId) {
        JobServiceConnection connection = runningJobs.get(jobId);
        if (connection != null) {
            runningJobs.remove(jobId);
            connection.stop(false);
        }
    }

    private void handleStopAll() {
        int size = runningJobs.size();
        for(int i = 0; i < size; i++) {
            int jobId = runningJobs.keyAt(i);
            JobServiceConnection connection = runningJobs.get(jobId);
            connection.stop(false);
        }
        runningJobs.clear();
    }

    private void handleRecheckConstraints(int networkType, boolean powerConnected) {
        int size = runningJobs.size();
        for(int i = 0; i < size; i++) {
            int jobId = runningJobs.keyAt(i);
            JobServiceConnection connection = runningJobs.get(jobId);
            if (!jobMeetsConstraints(connection.job, networkType, powerConnected)) {
                connection.stop(true);
            }
        }
    }

    private boolean jobMeetsConstraints(JobInfo job, int networkType, boolean isCharging) {
        return JobInfoUtil.hasRequiredNetwork(job, networkType) && JobInfoUtil.hasRequiredPowerState(job, isCharging);
    }

    private class JobServiceConnection implements ServiceConnection {
        JobInfo job;
        JobParameters jobParams;
        IJobService jobService;
        boolean allowReschedule = true;

        JobServiceConnection(final JobInfo job, final int numFailures, final boolean runImmediately) {
            this.job = job;
            this.jobParams = new JobParameters(new IJobCallback.Stub() {
                @Override
                public void jobFinished(int jobId, boolean needsReschedule) throws RemoteException {
                    finishJob(jobId, JobServiceConnection.this);

                    if (needsReschedule && allowReschedule) {
                        JobServiceCompat.reschedule(JobSchedulerService.this, job, numFailures + 1);
                    } else {
                        stopIfFinished();
                    }
                }

                @Override
                public void acknowledgeStartMessage(int jobId, boolean workOngoing) throws RemoteException {
                    if (!workOngoing) {
                        finishJob(jobId, JobServiceConnection.this);
                        stopIfFinished();
                    }
                }

                @Override
                public void acknowledgeStopMessage(int jobId, boolean reschedule) throws RemoteException {
                    finishJob(jobId, JobServiceConnection.this);

                    if (reschedule && allowReschedule) {
                        JobServiceCompat.reschedule(JobSchedulerService.this, job, numFailures + 1);
                    } else {
                        stopIfFinished();
                    }
                }
            }, job.getId(), job.getExtras(), runImmediately);
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
            stopIfFinished();
        }

        void stop(boolean allowReschedule) {
            if (jobService != null) {
                this.allowReschedule = allowReschedule;
                try {
                    jobService.stopJob(jobParams);
                } catch (Exception e) {
                    Log.e(TAG, "Error while stopping job: " + job.getId());
                    throw new RuntimeException(e);
                }
            }
        }
    }

    static void startJob(Context context, JobInfo job, int numFailures, boolean runImmediately) {
        context.startService(new Intent(context, JobSchedulerService.class)
                .putExtra(EXTRA_MSG, MSG_START_JOB)
                .putExtra(EXTRA_JOB, job)
                .putExtra(EXTRA_RUN_IMMEDIATELY, runImmediately)
                .putExtra(EXTRA_NUM_FAILURES, numFailures));
    }

    static void stopJob(Context context, int jobId) {
        context.startService(new Intent(context, JobSchedulerService.class)
                .putExtra(EXTRA_MSG, MSG_STOP_JOB)
                .putExtra(EXTRA_JOB_ID, jobId));
    }

    static void stopAll(Context context) {
        context.startService(new Intent(context, JobSchedulerService.class)
                .putExtra(EXTRA_MSG, MSG_STOP_ALL));
    }

    static void recheckConstraints(Context context, int networkType, boolean isCharging) {
        context.startService(new Intent(context, JobSchedulerService.class)
                .putExtra(EXTRA_MSG, MSG_RECHECK_CONSTRAINTS)
                .putExtra(EXTRA_NETWORK_TYPE, networkType)
                .putExtra(EXTRA_IS_CHARGING, isCharging));
    }

    private void finishJob(int jobId, JobServiceConnection connection) {
        if (runningJobs.get(jobId) != null) {
            unbindService(connection);
        }
        runningJobs.remove(jobId);
    }

    private void stopIfFinished() {
        if (runningJobs.size() == 0) {
            stopSelf();
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            Intent intent = (Intent) msg.obj;
            switch (msg.what) {
                case MSG_START_JOB:
                    JobInfo job = intent.getParcelableExtra(EXTRA_JOB);
                    int numFailures = intent.getIntExtra(EXTRA_NUM_FAILURES, 0);
                    boolean runImmediately = intent.getBooleanExtra(EXTRA_RUN_IMMEDIATELY, false);
                    handleStartJob(job, numFailures, runImmediately);
                    break;
                case MSG_STOP_JOB:
                    int jobId = intent.getIntExtra(EXTRA_JOB_ID, 0);
                    handleStopJob(jobId);
                    break;
                case MSG_STOP_ALL:
                    handleStopAll();
                    break;
                case MSG_RECHECK_CONSTRAINTS:
                    int networkType = intent.getIntExtra(EXTRA_NETWORK_TYPE, JobInfo.NETWORK_TYPE_NONE);
                    boolean powerConnected = intent.getBooleanExtra(EXTRA_IS_CHARGING, false);
                    handleRecheckConstraints(networkType, powerConnected);
                    break;
            }
        }
    };
}
