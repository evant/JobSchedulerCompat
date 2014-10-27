package me.tatarka.support.job;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

public class JobServiceCompat extends IntentService {
    static final String SCHEDULE = "SCHEDULE";
    static final String CANCEL = "CANCEL";

    static final String EXTRA_METHOD = "EXTRA_METHOD";
    static final String EXTRA_JOB = "EXTRA_JOB";
    static final String EXTRA_JOB_ID = "EXTRA_JOB_ID";

    private AlarmManager am;

    public JobServiceCompat() {
        super("JobServiceCompat");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String method = intent.getStringExtra(EXTRA_METHOD);

        if (SCHEDULE.equals(method)) {
            JobInfo job = intent.getParcelableExtra(EXTRA_JOB);
            handleSchedule(job);
        } else if (CANCEL.equals(method)) {
            int jobId = intent.getIntExtra(EXTRA_JOB_ID, 0);
            handleCancel(jobId);
        }
    }

    private void handleSchedule(JobInfo job) {
        if (job.isPeriodic()) {
            am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, 0, job.getIntervalMillis(), toPendingIntent(job));
        } else {
            throw new UnsupportedOperationException("Not yet implemented!");
        }

    }

    private void handleCancel(int jobId) {

    }

    private static PendingIntent toPendingIntent(JobInfo job) {

    }

    static void schedule(Context context, JobInfo job) {
        context.startService(
                new Intent(context, JobServiceCompat.class)
                        .putExtra(EXTRA_JOB, job));
    }

    static void cancel(Context context, int jobId) {
        context.startService(
                new Intent(context, JobServiceCompat.class)
                        .putExtra(CANCEL, jobId));
    }
}
