// IJobCallback.aidl
package android.app.job;

// Declare any non-default types here with import statements

interface IJobCallback {
    void jobFinished(int jobId, boolean needsReschedule);
    void acknowledgeStartMessage(int jobId, boolean workOngoing);
    void acknowledgeStopMessage(int jobId, boolean reschedule);
}
