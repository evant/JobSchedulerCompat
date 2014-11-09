package me.tatarka.support.internal.job;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.support.v4.content.WakefulBroadcastReceiver;

import java.util.ArrayList;
import java.util.List;

import me.tatarka.support.internal.util.ArraySet;
import me.tatarka.support.job.JobInfo;
import me.tatarka.support.internal.receivers.BootReceiver;
import me.tatarka.support.internal.receivers.ControllerPrefs;
import me.tatarka.support.internal.receivers.IdleReceiver;
import me.tatarka.support.internal.receivers.JobStatus;
import me.tatarka.support.internal.receivers.NetworkReceiver;
import me.tatarka.support.internal.receivers.PowerReceiver;
import me.tatarka.support.internal.receivers.ReceiverUtils;
import me.tatarka.support.internal.receivers.TimeReceiver;

/**
 * @hide *
 */
public class JobServiceCompat extends IntentService {
    static final String TAG = "JobServiceCompat";

    private static final String EXTRA_MSG = "EXTRA_MSG";
    private static final String EXTRA_JOB = "EXTRA_JOB";
    private static final String EXTRA_JOB_ID = "EXTRA_JOB_ID";
    private static final String EXTRA_RELEASE_WAKE_LOCK = "EXTRA_RELEASE_WAKE_LOCK";

    private static final int MSG_SCHEDULE_JOB = 0;
    private static final int MSG_CANCEL_JOB = 1;
    private static final int MSG_CANCEL_ALL = 2;
    private static final int MSG_RUN_JOBS = 3;
    private static final int MSG_JOBS_FINISHED = 4;
    private static final int MSG_BOOT = 5;

    private static PowerManager.WakeLock WAKE_LOCK;

    public JobServiceCompat() {
        super("JobServiceCompat");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);

        if (WAKE_LOCK == null) {
            WAKE_LOCK = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JobServiceCompat");
        }
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
            case MSG_CANCEL_JOB: {
                int jobId = intent.getIntExtra(EXTRA_JOB_ID, 0);
                handleCancelJob(jobId);
                break;
            }
            case MSG_CANCEL_ALL: {
                handleCancelAll();
                break;
            }
            case MSG_RUN_JOBS: {
                handleRunJobs();
                boolean releaseWakeLock = intent.getBooleanExtra(EXTRA_RELEASE_WAKE_LOCK, false);
                if (releaseWakeLock) {
                    WakefulBroadcastReceiver.completeWakefulIntent(intent);
                }
                break;
            }
            case MSG_BOOT: {
                handleBoot();
            }
            case MSG_JOBS_FINISHED: {
                handleJobsFinished();
            }
        }
    }

    private void handleSchedule(JobInfo job) {
        JobStatus jobStatus = new JobStatus(job);
        JobStore jobStore = JobStore.initAndGet(this);
        synchronized (jobStore) {
            JobStatus oldJobStats = jobStore.getJobByJobId(job.getId());
            jobStore.remove(oldJobStats);
            jobStore.add(jobStatus);
        }
        scheduleJob(jobStatus);
    }

    private void scheduleJob(JobStatus job) {
        if (job.hasConnectivityConstraint() || job.hasUnmeteredConstraint()) {
            NetworkReceiver.setNetworkForJob(this, job);
            ReceiverUtils.enable(this, NetworkReceiver.class);
        }

        if (job.hasChargingConstraint()) {
            PowerReceiver.setPowerForJob(this, job);
            ReceiverUtils.enable(this, PowerReceiver.class);
        }

        if (job.hasIdleConstraint()) {
            ReceiverUtils.enable(this, IdleReceiver.class);
        }

        if (job.isPersisted()) {
            ReceiverUtils.enable(this, BootReceiver.class);
        }

        TimeReceiver.setAlarmsForJob(this, job);
    }

    private void handleCancelJob(int jobId) {
        unscheduleJob(jobId);
        JobStore jobStore = JobStore.initAndGet(this);
        synchronized (jobStore) {
            JobStatus job = jobStore.getJobByJobId(jobId);
            jobStore.remove(job);
        }
        JobSchedulerService.stopJob(this, jobId);
    }

    private void handleCancelAll() {
        JobStore jobStore = JobStore.initAndGet(this);
        synchronized (jobStore) {
            ArraySet<JobStatus> jobStatuses = jobStore.getJobs();
            for (int i = 0; i < jobStatuses.size(); i++) {
                JobStatus jobStatus = jobStatuses.valueAt(i);
                unscheduleJob(jobStatus.getJobId());
            }
            jobStore.clear();
        }
        JobSchedulerService.stopAll(this);
    }

    private void handleRunJobs() {
        JobSchedulerService.recheckConstraints(this);

        JobStore jobStore = JobStore.initAndGet(this);
        List<Integer> jobsToRun = new ArrayList<Integer>();
        synchronized (jobStore) {
            ArraySet<JobStatus> jobs = jobStore.getJobs();
            for (int i = 0; i < jobs.size(); i++) {
                JobStatus job = jobs.valueAt(i);
                if (job.isReady()) {
                    jobsToRun.add(job.getJobId());
                }
            }
        }

        if (!jobsToRun.isEmpty()) {
            WAKE_LOCK.acquire();
            for (int jobId : jobsToRun) {
                JobSchedulerService.startJob(this, jobId);
            }
        }
    }

    private void handleBoot() {
        ControllerPrefs.getInstance(this).clear();

        JobStore jobStore = JobStore.initAndGet(this);
        synchronized (jobStore) {
            ArraySet<JobStatus> jobStatuses = jobStore.getJobs();
            for (int i = 0; i < jobStatuses.size(); i++) {
                scheduleJob(jobStatuses.valueAt(i));
            }
        }
    }

    private void handleJobsFinished() {
        // Check if we can turn off any broadcast receivers.
        JobStore jobStore = JobStore.initAndGet(this);
        boolean hasNetworkConstraint = false;
        boolean hasPowerConstraint = false;
        boolean hasIdleConstraint = false;
        boolean hasBootConstraint = false;

        synchronized (jobStore) {
            ArraySet<JobStatus> jobs = jobStore.getJobs();
            for (int i = 0; i < jobs.size(); i++) {
                JobStatus job = jobs.valueAt(i);

                if (job.hasConnectivityConstraint() || job.hasUnmeteredConstraint()) {
                    hasNetworkConstraint = true;
                }

                if (job.hasChargingConstraint()) {
                    hasPowerConstraint = true;
                }

                if (job.hasIdleConstraint()) {
                    hasIdleConstraint = true;
                }

                if (job.isPersisted()) {
                    hasBootConstraint = true;
                }

                if (hasNetworkConstraint && hasPowerConstraint && hasBootConstraint && hasIdleConstraint) {
                    break;
                }
            }
        }

        if (!hasNetworkConstraint) {
            ReceiverUtils.disable(this, NetworkReceiver.class);
        }

        if (!hasPowerConstraint) {
            ReceiverUtils.disable(this, PowerReceiver.class);
        }

        if (!hasIdleConstraint) {
            ReceiverUtils.disable(this, IdleReceiver.class);
        }

        if (!hasBootConstraint) {
            ReceiverUtils.disable(this, BootReceiver.class);
        }

        // Alright we're done, you can go to sleep now.
        if (WAKE_LOCK.isHeld()) {
            WAKE_LOCK.release();
        }
    }

    private void unscheduleJob(int jobId) {
        TimeReceiver.unsetAlarmsForJob(this, jobId);
    }

    public static void schedule(Context context, JobInfo job) {
        context.startService(
                new Intent(context, JobServiceCompat.class)
                        .putExtra(EXTRA_MSG, MSG_SCHEDULE_JOB)
                        .putExtra(EXTRA_JOB, job));
    }

    public static void cancel(Context context, int jobId) {
        context.startService(
                new Intent(context, JobServiceCompat.class)
                        .putExtra(EXTRA_MSG, MSG_CANCEL_JOB)
                        .putExtra(EXTRA_JOB_ID, jobId));
    }

    public static void cancelAll(Context context) {
        context.startService(
                new Intent(context, JobServiceCompat.class)
                        .putExtra(EXTRA_MSG, MSG_CANCEL_ALL));
    }

    static void jobsFinished(Context context) {
        context.startService(
                new Intent(context, JobServiceCompat.class)
                        .putExtra(EXTRA_MSG, MSG_JOBS_FINISHED));
    }

    public static Intent maybeRunJobs(Context context) {
        return new Intent(context, JobServiceCompat.class)
                .putExtra(EXTRA_MSG, MSG_RUN_JOBS)
                .putExtra(EXTRA_RELEASE_WAKE_LOCK, true);
    }

    public static Intent bootIntent(Context context) {
        return new Intent(context, JobServiceCompat.class)
                .putExtra(EXTRA_MSG, MSG_BOOT);
    }
}
