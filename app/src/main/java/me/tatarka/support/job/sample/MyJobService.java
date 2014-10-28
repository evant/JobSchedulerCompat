package me.tatarka.support.job.sample;

import android.util.Log;
import me.tatarka.support.job.JobParameters;
import me.tatarka.support.job.JobService;

/**
 * Created by evantatarka on 10/23/14.
 */
public class MyJobService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d("MyJobService", "ran job (" + params.getJobId() + ")");
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
