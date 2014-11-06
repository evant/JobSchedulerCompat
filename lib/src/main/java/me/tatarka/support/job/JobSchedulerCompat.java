package me.tatarka.support.job;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by evantatarka on 10/21/14.
 */
class JobSchedulerCompat extends JobScheduler {
    private static JobSchedulerCompat INSTANCE;

    static synchronized JobSchedulerCompat getCompatInstance(Context context) {
        if (INSTANCE == null) INSTANCE = new JobSchedulerCompat(context);
        return INSTANCE;
    }

    private Context context;
    private JobPersister jobPersister;

    private JobSchedulerCompat(Context context) {
        this.context = context.getApplicationContext();
        this.jobPersister = JobPersister.getInstance(context);
    }

    @Override
    public synchronized void cancel(int jobid) {
        JobServiceCompat.cancel(context, jobid);
    }

    @Override
    public synchronized void cancelAll() {
        for (JobInfo job : jobPersister.getPendingJobs()) {
            JobServiceCompat.cancel(context, job.getId());
        }
    }

    @Override
    public synchronized List<JobInfo> getAllPendingJobs() {
        return jobPersister.getPendingJobs();
    }

    @Override
    public synchronized int schedule(JobInfo jobInfo) {
        JobServiceCompat.schedule(context, jobInfo);
        return JobScheduler.RESULT_SUCCESS;
    }
}
