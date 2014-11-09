package me.tatarka.support.job;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.tatarka.support.internal.util.ArraySet;
import me.tatarka.support.server.job.JobServiceCompat;
import me.tatarka.support.server.job.JobStore;
import me.tatarka.support.server.job.controllers.JobStatus;

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

    private JobSchedulerCompat(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public synchronized void cancel(int jobid) {
        JobServiceCompat.cancel(context, jobid);
    }

    @Override
    public synchronized void cancelAll() {
        JobServiceCompat.cancelAll(context);
    }

    @Override
    public synchronized List<JobInfo> getAllPendingJobs() {
        JobStore jobStore = JobStore.initAndGet(context);
        List<JobInfo> result = new ArrayList<JobInfo>();
        synchronized (jobStore) {
            ArraySet<JobStatus> jobs = jobStore.getJobs();
            for (int i = 0; i < jobs.size(); i++) {
                result.add(jobs.valueAt(i).getJob());
            }
        }
        return result;
    }

    @Override
    public synchronized int schedule(JobInfo jobInfo) {
        JobServiceCompat.schedule(context, jobInfo);
        return JobScheduler.RESULT_SUCCESS;
    }
}
