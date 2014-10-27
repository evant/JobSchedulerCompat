package me.tatarka.support.job.sample;

import android.app.Application;
import android.content.ComponentName;

import me.tatarka.support.job.JobInfo;
import me.tatarka.support.job.JobScheduler;

/**
 * Created by evantatarka on 10/23/14.
 */
public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        JobScheduler jobScheduler = JobScheduler.getInstance(this);

        jobScheduler.schedule(new JobInfo.Builder(0, new ComponentName(this, MyJobService.class))
                .setPeriodic(1000)
                .build());
    }
}
