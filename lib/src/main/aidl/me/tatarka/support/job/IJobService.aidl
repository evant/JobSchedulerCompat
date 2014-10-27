// IJobService.aidl
package me.tatarka.support.job;

// Declare any non-default types here with import statements
import me.tatarka.support.job.JobParameters;

interface IJobService {
    void startJob(in me.tatarka.support.job.JobParameters jobParams);
    void stopJob(in me.tatarka.support.job.JobParameters jobParams);
}
