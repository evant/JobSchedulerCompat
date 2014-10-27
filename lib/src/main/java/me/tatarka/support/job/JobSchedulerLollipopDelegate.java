package me.tatarka.support.job;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by evantatarka on 10/21/14.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class JobSchedulerLollipopDelegate extends JobScheduler {
    /** @hide **/
    public static final String EXTRA_DELEGATE_TO_SERVICE = JobSchedulerLollipopDelegate.class.getName() + ".DELEGATE_TO_SERVICE";

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
        return jobScheduler.schedule(convertJobInfo(context, job));
    }

    private static android.app.job.JobInfo convertJobInfo(Context context, JobInfo job) {
        android.app.job.JobInfo.Builder builder = new android.app.job.JobInfo.Builder(job.getId(), new ComponentName(context, JobServiceLollipopDelegate.class));

        PersistableBundle extras = (PersistableBundle) job.getExtras();
        if (extras == null) {
            extras = new PersistableBundle();
        }
        extras.putString(EXTRA_DELEGATE_TO_SERVICE, job.getService().flattenToString());

        builder.setExtras(extras);
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
        builder.setExtras(job.getExtras());
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
