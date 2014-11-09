package me.tatarka.support.internal.receivers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v4.content.WakefulBroadcastReceiver;

import me.tatarka.support.internal.job.JobServiceCompat;
import me.tatarka.support.internal.job.JobStore;
import me.tatarka.support.internal.util.ArraySet;

/**
 * Created by evantatarka on 11/8/14.
 */
public class IdleReceiver extends WakefulBroadcastReceiver {
    // Policy: we decide that we're "idle" if the device has been unused /
    // screen off or dreaming for at least this long
    private static final long INACTIVITY_IDLE_THRESHOLD = 71 * 60 * 1000; // millis; 71 min
    private static final long IDLE_WINDOW_SLOP = 5 * 60 * 1000; // 5 minute window, to be nice
    private static final long INACTIVITY_ANYWAY_THRESHOLD = 97 * 60 * 1000; // millis; 97 min

    private static final String ACTION_TRIGGER_IDLE =
            "me.tatarka.support.server.job.controllers.IdleReceiver.ACTION_TRIGGER_IDLE";

    private static IdleReceiver sReceiver;

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        ControllerPrefs prefs = ControllerPrefs.getInstance(context);
        boolean isDaydream = prefs.isInDaydreamMode();
        if (action.equals(Intent.ACTION_DREAMING_STARTED)) {
            prefs.edit().setInDaydreamMode(true).apply();
            isDaydream = true;
        } else if (action.equals(Intent.ACTION_DREAMING_STOPPED)) {
            prefs.edit().setInDaydreamMode(false).apply();
            isDaydream = false;
        }

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean isInteractive = pm.isScreenOn();

        boolean isIdle = !isInteractive || isDaydream;

        if (action.equals(Intent.ACTION_SCREEN_ON)
                || action.equals(Intent.ACTION_DREAMING_STOPPED)) {
            // possible transition to not-idle

            if (!isIdle) {
                AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                Intent i = new Intent(context, IdleReceiver.class);
                i.setAction(ACTION_TRIGGER_IDLE);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, i, PendingIntent.FLAG_NO_CREATE);
                if (pendingIntent != null) {
                    am.cancel(pendingIntent);
                }
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
            if (isIdle) {
                reportNewIdleState(context, true);
            } else {
                setAnywayTimer(context);
                enableReceiver(context);
            }
        }
    }

    public static void setIdleForJob(Context context, JobStatus job) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean isInteractive = pm.isScreenOn();
        boolean isDreamMode = ControllerPrefs.getInstance(context).isInDaydreamMode();
        job.idleConstraintSatisfied.set(!isInteractive || isDreamMode);
        
        setAnywayTimer(context);
        enableReceiver(context);
    }

    /** We can't get idle broadcasts while the app is not running. Since our app will probably be
     killed before the device becomes idle, let's set an alarm sometime in the future that will 
     force an idle check. */
    private static void setAnywayTimer(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, IdleReceiver.class);
        intent.setAction(ACTION_TRIGGER_IDLE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
        final long nowElapsed = SystemClock.elapsedRealtime();
        final long when = nowElapsed + INACTIVITY_ANYWAY_THRESHOLD;
        compatSetWindow(am, AlarmManager.ELAPSED_REALTIME_WAKEUP, when, IDLE_WINDOW_SLOP, pendingIntent);
    }

    public static void unsetIdle(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, IdleReceiver.class);
        intent.setAction(ACTION_TRIGGER_IDLE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_NO_CREATE);
        if (pendingIntent != null) {
            am.cancel(pendingIntent);
        }
        disableReceiver(context);
    }

    /**
     * Call this as soon as we get a chance, it will be unregistered whenever our app is killed.
     */
    public static void enableReceiver(Context context) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            filter.addAction(Intent.ACTION_DREAMING_STARTED);
            filter.addAction(Intent.ACTION_DREAMING_STOPPED);
        }
        sReceiver = new IdleReceiver();
        context.getApplicationContext().registerReceiver(sReceiver, filter);
    }

    private static void disableReceiver(Context context) {
        if (sReceiver != null) {
            context.getApplicationContext().unregisterReceiver(sReceiver);
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
