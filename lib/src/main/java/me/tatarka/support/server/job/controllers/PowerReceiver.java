package me.tatarka.support.server.job.controllers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.support.v4.content.WakefulBroadcastReceiver;

import me.tatarka.support.internal.util.ArraySet;
import me.tatarka.support.server.job.JobServiceCompat;
import me.tatarka.support.server.job.JobStore;

/**
 * @hide
 */
public class PowerReceiver extends WakefulBroadcastReceiver {
    private static final String ACTION_CHARGING_STABLE =
            "me.tatarka.support.server.job.controllers.PowerReceiver.ACTION_CHARGING_STABLE";
    /** Wait this long after phone is plugged in before doing any work. */
    private static final long STABLE_CHARGING_THRESHOLD_MILLIS = 2 * 60 * 1000; // 2 minutes.

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (Intent.ACTION_BATTERY_LOW.equals(action)) {
            // If we get this action, the battery is discharging => it isn't plugged in so
            // there's no work to cancel. We track this variable for the case where it is
            // charging, but hasn't been for long enough to be healthy.
            ControllerPrefs.getInstance(context).edit().setBatteryLow(true).apply();
        } else if (Intent.ACTION_BATTERY_OKAY.equals(action)) {
            ControllerPrefs.getInstance(context).edit().setBatteryLow(false).apply();
            maybeReportNewChargingState(context, isCharging(context));
        } else if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
            // Set up an alarm for ACTION_CHARGING_STABLE - we don't want to kick off tasks
            // here if the user unplugs the phone immediately.
            setStableChargingAlarm(context);
        } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
            // If an alarm is set, breathe a sigh of relief and cancel it - crisis averted.
            cancelStableChargingAlarm(context);
            maybeReportNewChargingState(context, false);
        } else if (ACTION_CHARGING_STABLE.equals(action)) {
            // Here's where we actually do the notify for a task being ready.
            maybeReportNewChargingState(context, isBatteryLow(context));
        }
    }

    private void maybeReportNewChargingState(Context context, boolean stablePower) {
        boolean reportChange = false;
        final JobStore jobStore = JobStore.initAndGet(context);
        synchronized (jobStore) {
            ArraySet<JobStatus> jobs = jobStore.getJobs();
            for (int i = 0; i < jobs.size(); i++) {
                JobStatus ts = jobs.valueAt(i);
                boolean previous = ts.chargingConstraintSatisfied.getAndSet(stablePower);
                if (previous != stablePower) {
                    reportChange = true;
                }
            }
        }
        // Let the scheduler know that state has changed. This may or may not result in an
        // execution.
        if (reportChange) {
            startWakefulService(context, JobServiceCompat.maybeRunJobs(context));
        }
    }

    private void setStableChargingAlarm(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, PowerReceiver.class);
        intent.setAction(ACTION_CHARGING_STABLE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);

        final long alarmTriggerElapsed = SystemClock.elapsedRealtime() + STABLE_CHARGING_THRESHOLD_MILLIS;
        am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, alarmTriggerElapsed, pendingIntent);
    }

    private void cancelStableChargingAlarm(Context context) {
        Intent intent = new Intent(context, PowerReceiver.class);
        intent.setAction(ACTION_CHARGING_STABLE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_NO_CREATE);
        if (pendingIntent != null) {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            am.cancel(pendingIntent);
        }
    }

    private boolean isCharging(Context context) {
        Intent i = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        return plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB;
    }

    private boolean isBatteryLow(Context context) {
        return ControllerPrefs.getInstance(context).isBatteryLow();
    }
}
