package me.tatarka.support.internal.job;

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
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

import me.tatarka.support.internal.receivers.IdleReceiver;
import me.tatarka.support.job.IJobCallback;
import me.tatarka.support.job.IJobService;
import me.tatarka.support.job.JobInfo;
import me.tatarka.support.job.JobParameters;
import me.tatarka.support.internal.receivers.JobStatus;
import me.tatarka.support.internal.receivers.TimeReceiver;

/**
 * @hide
 */
public class JobSchedulerService extends Service {
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

        if (job == null) {
            // Attempting to start a non-existent job, it may have already been canceled.
            return;
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

                    if (allowReschedule) {
                        if (needsReschedule || job.getJob().isPeriodic()) {
                            rescheduleJob(job, needsReschedule);
                        }
                    }
                    stopIfFinished();
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

                    if (allowReschedule) {
                        if (reschedule || job.getJob().isPeriodic()) {
                            rescheduleJob(job, reschedule);
                        }
                    }
                    stopIfFinished();
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

    private void rescheduleJob(JobStatus job, boolean wasFailure) {
        JobStatus newJob;
        if (wasFailure) {
            newJob = rescheduleFailedJob(job);
        } else {
            newJob = reschedulePeriodicJob(job);
        }

        JobStore jobStore = JobStore.initAndGet(this);
        synchronized (jobStore) {
            jobStore.remove(job);
            jobStore.add(newJob);
        }

        TimeReceiver.setAlarmsForJob(this, newJob);
        if (job.hasIdleConstraint()) {
            IdleReceiver.setIdleForJob(this, newJob);
        }
    }

    /**
     * A job is rescheduled with exponential back-off if the client requests this from their
     * execution logic.
     * A caveat is for idle-mode jobs, for which the idle-mode constraint will usurp the
     * timeliness of the reschedule. For an idle-mode job, no deadline is given.
     * @return A newly instantiated JobStatus with the same constraints as the last job except
     * with adjusted timing constraints.
     */
    private JobStatus rescheduleFailedJob(JobStatus job) {
        if (job.hasIdleConstraint()) {
            // Don't need to modify time on idle job, it will run whenever the next idle period is.
            return job;
        }
        
        final long elapsedNowMillis = SystemClock.elapsedRealtime();
        final JobInfo jobInfo = job.getJob();

        final long initialBackoffMillis = jobInfo.getInitialBackoffMillis();
        final int backoffAttemps = job.getNumFailures() + 1;

        long delayMillis;
        switch (job.getJob().getBackoffPolicy()) {
            case JobInfo.BACKOFF_POLICY_LINEAR:
                delayMillis = initialBackoffMillis * backoffAttemps;
                break;
            default:
            case JobInfo.BACKOFF_POLICY_EXPONENTIAL:
                delayMillis = (long) Math.scalb(initialBackoffMillis, backoffAttemps - 1);
                break;
        }
        delayMillis = Math.min(delayMillis, JobInfo.MAX_BACKOFF_DELAY_MILLIS);
        return new JobStatus(job, elapsedNowMillis + delayMillis, JobStatus.NO_LATEST_RUNTIME, backoffAttemps);
    }

    /**
     * Called after a periodic has executed so we can to re-add it. We take the last execution time
     * of the job to be the time of completion (i.e. the time at which this function is called).
     * This could be inaccurate b/c the job can run for as long as
     * {@link com.android.server.job.JobServiceContext#EXECUTING_TIMESLICE_MILLIS}, but will lead
     * to underscheduling at least, rather than if we had taken the last execution time to be the
     * start of the execution.
     * @return A new job representing the execution criteria for this instantiation of the
     * recurring job.
     */
    private JobStatus reschedulePeriodicJob(JobStatus job) {
        final long elapsedNow = SystemClock.elapsedRealtime();
        // Compute how much of the period is remaining.
        long runEarly = Math.max(job.getLatestRunTimeElapsed() - elapsedNow, 0);
        long newEarliestRunTimeElapsed = elapsedNow + runEarly;
        long period = job.getJob().getIntervalMillis();
        long newLatestRuntimeElapsed = newEarliestRunTimeElapsed + period;
        return new JobStatus(job, newEarliestRunTimeElapsed,
                newLatestRuntimeElapsed, 0 /* backoffAttempt */);
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
