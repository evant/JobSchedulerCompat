package me.tatarka.support.job;

import android.content.Context;
import android.os.Build;

import java.util.List;

import me.tatarka.support.internal.JobSchedulerCompat;
import me.tatarka.support.internal.JobSchedulerLollipopDelegate;

/**
 * Created by evantatarka on 10/21/14.
 */
public abstract class JobScheduler {
    /**
     * Returned from {@link #schedule(JobInfo)} when an invalid parameter was supplied. This can
     * occur if the run-time for your job is too short, or perhaps the system can't resolve the
     * requisite {@link JobService} in your package.
     */
    public static final int RESULT_FAILURE = 0;

    /**
     * Returned from {@link #schedule(JobInfo)} if this application has made too many requests for
     * work over too short a time.
     */
    // TODO: Determine if this is necessary.
    public static final int RESULT_SUCCESS = 1;

    /**
     * @param job The job you wish scheduled. See {@link JobInfo.Builder JobInfo.Builder} for more
     *            detail on the sorts of jobs you can schedule.
     * @return If >0, this int returns the jobId of the successfully scheduled job. Otherwise you
     * have to compare the return value to the error codes defined in this class.
     */
    public abstract int schedule(JobInfo job);

    /**
     * Cancel a job that is pending in the JobScheduler.
     *
     * @param jobId unique identifier for this job. Obtain this value from the jobs returned by
     *              {@link #getAllPendingJobs()}.
     */
    public abstract void cancel(int jobId);

    /**
     * Cancel all jobs that have been registered with the JobScheduler by this package.
     */
    public abstract void cancelAll();

    /**
     * @return a list of all the jobs registered by this package that have not yet been executed.
     */
    public abstract List<JobInfo> getAllPendingJobs();

    /**
     * Get an instance of a the {@link JobScheduler}, which will delegate to the android one in api
     * 21+ and use a backported version on older apis.
     *
     * @param context the context
     * @return a JobScheduler instance
     */
    public static JobScheduler getInstance(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return JobSchedulerLollipopDelegate.getLollipopInstance(context);
        } else {
            return JobSchedulerCompat.getCompatInstance(context);
        }
    }
}
