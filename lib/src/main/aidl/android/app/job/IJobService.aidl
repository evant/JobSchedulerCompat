// IJobService.aidl
package android.app.job;

// Declare any non-default types here with import statements

interface IJobService {
    void startJob(in JobParameters jobParams);
    void stopJob(in JobParameters jobParams);
}
