package me.tatarka.support.server.job;

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

import me.tatarka.support.job.IJobCallback;
import me.tatarka.support.job.IJobService;
import me.tatarka.support.job.JobParameters;
import me.tatarka.support.server.job.controllers.JobStatus;

/**
 * @hide
 */
public class JobSchedulerService extends Service implements StateChangedListener {
    static final String TAG = "JobServiceSchedulerService";

    private static final String EXTRA_MSG = "EXTRA_MSG";
    private static final String EXTRA_JOB_ID = "EXTRA_JOB_ID";

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

    private void handleStartJob(int jobId) {
        if (runningJobs.get(jobId) != null) {
            // Job already running!
            return;
        }

        JobStore jobStore = JobStore.initAndGet(this);
        JobStatus job;
        synchronized (jobStore) {
            job = jobStore.getJobByJobId(jobId);
        }

        Intent intent = new Intent();
        intent.setComponent(job.getJob().getService());

        int flags = BIND_AUTO_CREATE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            flags |= BIND_WAIVE_PRIORITY;
        }

        JobServiceConnection connection = new JobServiceConnection(job);
        runningJobs.put(job.getJobId(), connection);
        bindService(intent, connection, flags);
    }

    private void handleStopJob(int jobId) {
        JobServiceConnection connection = runningJobs.get(jobId);
        if (connection != null) {
            connection.stop(false);
        }
    }

    private void handleStopAll() {
        int size = runningJobs.size();
        for (int i = 0; i < size; i++) {
            int jobId = runningJobs.keyAt(i);
            JobServiceConnection connection = runningJobs.get(jobId);
            connection.stop(false);
        }
    }

    private void handleRecheckConstraints() {
        int size = runningJobs.size();
        for (int i = 0; i < size; i++) {
            int jobId = runningJobs.keyAt(i);
            JobServiceConnection connection = runningJobs.get(jobId);
            if (!connection.job.isConstraintsSatisfied()) {
                connection.stop(true);
            }
        }
    }

    @Override
    public void onControllerStateChanged() {

    }

    @Override
    public void onRunJobNow(JobStatus jobStatus) {

    }

    private class JobServiceConnection implements ServiceConnection {
        JobStatus job;
        JobParameters jobParams;
        IJobService jobService;
        boolean allowReschedule = true;

        JobServiceConnection(final JobStatus job) {
            this.job = job;
            this.jobParams = new JobParameters(new IJobCallback.Stub() {
                @Override
                public void jobFinished(int jobId, boolean needsReschedule) throws RemoteException {
                    finishJob(jobId, JobServiceConnection.this);

                    if (needsReschedule && allowReschedule) {
                        JobServiceCompat.reschedule(JobSchedulerService.this, job.getJobId());
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
                        JobServiceCompat.reschedule(JobSchedulerService.this, job.getJobId());
                    } else {
                        stopIfFinished();
                    }
                }
            }, job.getJobId(), job.getExtras(), !job.isConstraintsSatisfied());
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            jobService = (IJobService) service;

            try {
                jobService.startJob(jobParams);
            } catch (Exception e) {
                Log.e(TAG, "Error while starting job: " + job.getJob());
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            runningJobs.remove(job.getJobId());
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
                    Log.e(TAG, "Error while stopping job: " + job.getJobId());
                    throw new RuntimeException(e);
                }
            }
        }
    }

    static void startJob(Context context, int jobId) {
        context.startService(new Intent(context, JobSchedulerService.class)
                .putExtra(EXTRA_MSG, MSG_START_JOB)
                .putExtra(EXTRA_JOB_ID, jobId));
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

    static void recheckConstraints(Context context) {
        context.startService(new Intent(context, JobSchedulerService.class)
                .putExtra(EXTRA_MSG, MSG_RECHECK_CONSTRAINTS));
    }

    private void finishJob(int jobId, JobServiceConnection connection) {
        if (runningJobs.get(jobId) != null) {
            unbindService(connection);
        }
        runningJobs.remove(jobId);
        JobStore jobStore = JobStore.initAndGet(this);
        synchronized (jobStore) {
            jobStore.remove(connection.job);
        }
    }

    private void stopIfFinished() {
        if (runningJobs.size() == 0) {
            JobServiceCompat.jobsFinished(this);
            stopSelf();
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            Intent intent = (Intent) msg.obj;
            switch (msg.what) {
                case MSG_START_JOB: {
                    int jobId = intent.getIntExtra(EXTRA_JOB_ID, 0);
                    handleStartJob(jobId);
                    break;
                }
                case MSG_STOP_JOB: {
                    int jobId = intent.getIntExtra(EXTRA_JOB_ID, 0);
                    handleStopJob(jobId);
                    break;
                }
                case MSG_STOP_ALL: {
                    handleStopAll();
                    break;
                }
                case MSG_RECHECK_CONSTRAINTS: {
                    handleRecheckConstraints();
                    break;
                }
            }
        }
    };
}
