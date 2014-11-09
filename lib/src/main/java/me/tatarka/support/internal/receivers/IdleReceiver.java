package me.tatarka.support.internal.receivers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.support.v4.content.WakefulBroadcastReceiver;

import me.tatarka.support.internal.util.ArraySet;
import me.tatarka.support.internal.job.JobServiceCompat;
import me.tatarka.support.internal.job.JobStore;

/**
 * Created by evantatarka on 11/8/14.
 */
public class IdleReceiver extends WakefulBroadcastReceiver {
    // Policy: we decide that we're "idle" if the device has been unused /
    // screen off or dreaming for at least this long
    private static final long INACTIVITY_IDLE_THRESHOLD = 71 * 60 * 1000; // millis; 71 min
    private static final long IDLE_WINDOW_SLOP = 5 * 60 * 1000; // 5 minute window, to be nice

    private static final String ACTION_TRIGGER_IDLE =
            "me.tatarka.support.server.job.controllers.IdleReceiver.ACTION_TRIGGER_IDLE";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action.equals(Intent.ACTION_SCREEN_ON)
                || action.equals(Intent.ACTION_DREAMING_STOPPED)) {
            // possible transition to not-idle
            ControllerPrefs prefs = ControllerPrefs.getInstance(context);
            if (prefs.isIdle()) {
                AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                Intent i = new Intent(context, IdleReceiver.class);
                i.setAction(ACTION_TRIGGER_IDLE);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, i, PendingIntent.FLAG_NO_CREATE);
                if (pendingIntent != null) {
                    am.cancel(pendingIntent);
                }
                prefs.edit().setIdle(false).apply();
                reportNewIdleState(context, false);
            }
        } else if (action.equals(Intent.ACTION_SCREEN_OFF)
                || action.equals(Intent.ACTION_DREAMING_STARTED)) {
            // when the screen goes off or dreaming starts, we schedule the
            // alarm that will tell us when we have decided the device is
            // truly idle.
            final long nowElapsed = SystemClock.elapsedRealtime();
            final long when = nowElapsed + INACTIVITY_IDLE_THRESHOLD;
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(context, IdleReceiver.class);
            i.setAction(ACTION_TRIGGER_IDLE);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, i, 0);
            compatSetWindow(am, AlarmManager.ELAPSED_REALTIME_WAKEUP, when, IDLE_WINDOW_SLOP, pendingIntent);
        } else if (action.equals(ACTION_TRIGGER_IDLE)) {
            // idle time starts now
            ControllerPrefs prefs = ControllerPrefs.getInstance(context);
            if (!prefs.isIdle()) {
                prefs.edit().setIdle(true).apply();
                reportNewIdleState(context, true);
            }
        }
    }

    /**
     * Interaction with the task manager service
     */
    void reportNewIdleState(Context context, boolean isIdle) {
        JobStore jobStore = JobStore.initAndGet(context);
        synchronized (jobStore) {
            ArraySet<JobStatus> jobs = jobStore.getJobs();
            for (int i = 0; i < jobs.size(); i++) {
                JobStatus ts = jobs.valueAt(i);
                ts.idleConstraintSatisfied.set(isIdle);
            }
        }
        startWakefulService(context, JobServiceCompat.maybeRunJobs(context));
    }

    private static void compatSetWindow(AlarmManager am, int type, long windowStartMillis, long windowLengthMillis, PendingIntent pendingIntent) {
        // Samsung devices have a bug where setWindow() may run before the start time.
        // https://code.google.com/p/android/issues/detail?id=69525
        boolean isShittySamsungDevice = Build.MANUFACTURER.equalsIgnoreCase("samsung");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && !isShittySamsungDevice) {
            am.setWindow(type, windowStartMillis, windowLengthMillis, pendingIntent);
        } else {
            am.set(type, windowStartMillis, pendingIntent);
        }
    }
}
