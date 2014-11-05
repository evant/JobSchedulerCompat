package me.tatarka.support.job;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

import me.tatarka.support.os.PersistableBundle;

/**
 * Created by evantatarka on 10/21/14.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class JobSchedulerLollipopDelegate extends JobScheduler {
    private Context context;
    private android.app.job.JobScheduler jobScheduler;

    private static JobSchedulerLollipopDelegate INSTANCE;
    static synchronized JobSchedulerLollipopDelegate getLollipopInstance(Context context) {
        if (INSTANCE == null) INSTANCE = new JobSchedulerLollipopDelegate(context);
        return INSTANCE;
    }

    private JobSchedulerLollipopDelegate(Context context) {
        this.context = context.getApplicationContext();
        jobScheduler = (android.app.job.JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    }

    @Override
    public void cancel(int jobid) {
        jobScheduler.cancel(jobid);
    }

    @Override
    public void cancelAll() {
        jobScheduler.cancelAll();
    }

    @Override
    public List<JobInfo> getAllPendingJobs() {
        List<android.app.job.JobInfo> jobs = jobScheduler.getAllPendingJobs();
        List<JobInfo> result = new ArrayList<JobInfo>(jobs.size());
        for (android.app.job.JobInfo job : jobs) {
            result.add(convertFromJobInfo(job));
        }
        return result;
    }

    @Override
    public int schedule(JobInfo job) {
        return jobScheduler.schedule(convertJobInfo(job));
    }

    private static android.app.job.JobInfo convertJobInfo(JobInfo job) {
        android.app.job.JobInfo.Builder builder = new android.app.job.JobInfo.Builder(job.getId(), job.getService());

        builder.setExtras((android.os.PersistableBundle) job.getExtras().getRealBundle());
        builder.setRequiresCharging(job.isRequireCharging());
        builder.setRequiresDeviceIdle(job.isRequireDeviceIdle());
        builder.setRequiredNetworkType(job.getNetworkType());

        if (job.getMinLatencyMillis() != 0) {
            builder.setMinimumLatency(job.getMinLatencyMillis());
        }

        if (job.getMaxExecutionDelayMillis() != 0) {
            builder.setOverrideDeadline(job.getMaxExecutionDelayMillis());
        }

        if (job.isPeriodic()) {
            builder.setPeriodic(job.getIntervalMillis());
        }

        builder.setPersisted(job.isPersisted());
        builder.setBackoffCriteria(job.getInitialBackoffMillis(), job.getBackoffPolicy());

        return builder.build();
    }

    private static JobInfo convertFromJobInfo(android.app.job.JobInfo job) {
        JobInfo.Builder builder = new JobInfo.Builder(job.getId(), job.getService());
        builder.setExtras(new PersistableBundle(job.getExtras()));
        builder.setRequiresCharging(job.isRequireCharging());
        builder.setRequiresDeviceIdle(job.isRequireDeviceIdle());
        builder.setRequiredNetworkType(job.getNetworkType());

        if (job.getMinLatencyMillis() != 0) {
            builder.setMinimumLatency(job.getMinLatencyMillis());
        }

        if (job.getMaxExecutionDelayMillis() != 0) {
            builder.setOverrideDeadline(job.getMaxExecutionDelayMillis());
        }

        if (job.isPeriodic()) {
            builder.setPeriodic(job.getIntervalMillis());
        }

        builder.setPersisted(job.isPersisted());
        builder.setBackoffCriteria(job.getInitialBackoffMillis(), job.getBackoffPolicy());

        return builder.build();
    }
}
