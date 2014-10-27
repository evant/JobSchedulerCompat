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
    private List<JobInfo> pendingJobs = new ArrayList<JobInfo>();

    private JobSchedulerCompat(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public synchronized void cancel(int jobid) {
        JobServiceCompat.cancel(context, jobid);
        for (int i = pendingJobs.size() - 1; i >= 0; i--) {
            if (pendingJobs.get(i).getId() == jobid) {
                pendingJobs.remove(i);
                break;
            }
        }
    }

    @Override
    public synchronized void cancelAll() {
        for (JobInfo job : pendingJobs) {
            JobServiceCompat.cancel(context, job.getId());
        }
        pendingJobs.clear();
    }

    @Override
    public synchronized List<JobInfo> getAllPendingJobs() {
        return new ArrayList<JobInfo>(pendingJobs);
    }

    @Override
    public synchronized int schedule(JobInfo jobInfo) {
        JobServiceCompat.schedule(context, jobInfo);
        return JobScheduler.RESULT_SUCCESS;
    }
}
