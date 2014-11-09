package me.tatarka.support.internal;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.List;

import me.tatarka.support.internal.util.ArraySet;
import me.tatarka.support.job.JobInfo;
import me.tatarka.support.job.JobScheduler;
import me.tatarka.support.internal.job.JobServiceCompat;
import me.tatarka.support.internal.job.JobStore;
import me.tatarka.support.internal.receivers.JobStatus;

/** @hide */
public class JobSchedulerCompat extends JobScheduler {
    private static JobSchedulerCompat INSTANCE;

    public static synchronized JobSchedulerCompat getCompatInstance(Context context) {
        if (INSTANCE == null) INSTANCE = new JobSchedulerCompat(context);
        return INSTANCE;
    }

    private Context context;
    private PackageManager pm;

    private JobSchedulerCompat(Context context) {
        this.context = context.getApplicationContext();
        pm = context.getPackageManager();
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
        checkPermissions(jobInfo);
        JobServiceCompat.schedule(context, jobInfo);
        return JobScheduler.RESULT_SUCCESS;
    }

    private void checkPermissions(JobInfo job) {
        String packageName = context.getPackageName();
        if (pm.checkPermission(Manifest.permission.WAKE_LOCK, packageName) != PackageManager.PERMISSION_GRANTED) {
            throw new IllegalStateException("Error: the WAKE_LOCK permission is required on api < 21.");
        }

        if (job.getNetworkType() != JobInfo.NETWORK_TYPE_NONE) {
            if (pm.checkPermission(Manifest.permission.ACCESS_NETWORK_STATE, packageName) != PackageManager.PERMISSION_GRANTED) {
                throw new IllegalStateException("Error: requested a job network constraint without holding ACCESS_NETWORK_STATE permission on api < 21.");
            }
        }

        if (job.isPersisted()) {
            if (pm.checkPermission(Manifest.permission.RECEIVE_BOOT_COMPLETED, packageName) != PackageManager.PERMISSION_GRANTED) {
                throw new IllegalStateException("Error: requested job to be persisted without holding RECEIVE_BOOT_COMPLETE permission.");
            }
        }
    }
}
