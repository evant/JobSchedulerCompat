package me.tatarka.support.job;

import android.content.Context;
import android.os.Build;

import java.util.List;

/**
 * Created by evantatarka on 10/21/14.
 */
public abstract class JobScheduler {
    public static final int RESULT_FAILURE = 0;
    public static final int RESULT_SUCCESS = 1;

    public abstract void cancel(int jobid);

    public abstract void cancelAll();

    public abstract List<JobInfo> getAllPendingJobs();

    public abstract int schedule(JobInfo jobInfo);

    public static JobScheduler getInstance(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return JobSchedulerLollipopDelegate.getLollipopInstance(context);
        } else {
            return JobSchedulerCompat.getCompatInstance(context);
        }
    }
}
