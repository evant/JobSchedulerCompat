package me.tatarka.support.job;

import android.annotation.TargetApi;
import android.app.job.JobParameters;
import android.content.ComponentName;
import android.os.Build;
import android.util.SparseArray;

/**
 * Created by evantatarka on 10/23/14.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class JobServiceLollipopDelegate extends android.app.job.JobService {
    private SparseArray<JobService> runningServices = new SparseArray<JobService>();

    @Override
    public boolean onStartJob(final JobParameters params) {
        String realServiceName = params.getExtras().getString(JobSchedulerLollipopDelegate.EXTRA_DELEGATE_TO_SERVICE);
        ComponentName realService = ComponentName.unflattenFromString(realServiceName);

        JobService jobService = obtainService(realService);
        runningServices.put(params.getJobId(), jobService);
        return jobService.onStartJob(convertFromJobParameters(params));
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        JobService jobService = runningServices.get(params.getJobId());
        runningServices.remove(params.getJobId());
        return jobService.onStopJob(convertFromJobParameters(params));
    }

    private static JobService obtainService(ComponentName serviceName) {
        try {
            Class<?> service = Class.forName(serviceName.getClassName());
            return (JobService) service.newInstance();
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        } catch (InstantiationException e) {
            throw new IllegalArgumentException(e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static me.tatarka.support.job.JobParameters convertFromJobParameters(final JobParameters params) {
        return new me.tatarka.support.job.JobParameters(null, params.getJobId(), params.getExtras(), params.isOverrideDeadlineExpired());
    }
}
