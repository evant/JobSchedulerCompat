package me.tatarka.support.server.job.controllers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.support.v4.content.WakefulBroadcastReceiver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import me.tatarka.support.internal.util.ArraySet;
import me.tatarka.support.server.job.JobServiceCompat;
import me.tatarka.support.server.job.JobStore;

/**
 * Created by evantatarka on 11/8/14.
 */
public class TimeReceiver extends WakefulBroadcastReceiver {
    private static final String ACTION_JOB_EXPIRED =
            "me.tatarka.support.jobscheduler.JOB_DEADLINE_EXPIRED";
    private static final String ACTION_JOB_DELAY_EXPIRED =
            "me.tatarka.support.jobscheduler.JOB_DELAY_EXPIRED";

    @Override
    public void onReceive(Context context, Intent intent) {
        List<JobStatus> jobs = getJobsSortedByLatestRuntime(context);
        if (ACTION_JOB_EXPIRED.equals(intent.getAction())) {
            checkExpiredDeadlinesAndResetAlarm(context, jobs);
        } else if (ACTION_JOB_DELAY_EXPIRED.equals(intent.getAction())) {
            checkExpiredDelaysAndResetAlarm(context, jobs);
        }
    }

    public static void setAlarmsForJob(Context context, JobStatus job) {
        if (job.hasTimingDelayConstraint() || job.hasDeadlineConstraint()) {
            List<JobStatus> jobs = getJobsSortedByLatestRuntime(context);
            maybeStopTrackingJob(context, job, jobs);
            maybeUpdateAlarms(context,
                    job.hasTimingDelayConstraint() ? job.getEarliestRunTime() : Long.MAX_VALUE,
                    job.hasDeadlineConstraint() ? job.getLatestRunTimeElapsed() : Long.MAX_VALUE);
        }
    }

    public static void unsetAlarmsForJob(Context context, int jobId) {
        List<JobStatus> jobs = getJobsSortedByLatestRuntime(context);
        JobStatus unsetJob = null;
        for (JobStatus job : jobs) {
            if (job.matches(jobId)) {
                unsetJob = job;
                break;
            }
        }

        if (unsetJob != null) {
            maybeStopTrackingJob(context, unsetJob, jobs);
        }
    }

    private static void maybeStopTrackingJob(Context context, JobStatus job, List<JobStatus> jobs) {
        if (jobs.contains(job)) {
            long nextDelayExpiredElapsedMillis = checkExpiredDelaysAndResetAlarm(context, jobs);
            long nextJobExpiredElapsedMillis = checkExpiredDeadlinesAndResetAlarm(context, jobs);
            ControllerPrefs.getInstance(context).edit()
                    .setNextDelayExipredElapsedMillis(nextDelayExpiredElapsedMillis)
                    .setNextJobExpiredElapsedMillis(nextJobExpiredElapsedMillis)
                    .apply();
        }
    }

    private static long checkExpiredDeadlinesAndResetAlarm(Context context, List<JobStatus> jobs) {
        long nextExpiryTime = Long.MAX_VALUE;
        final long nowElapsedMillis = SystemClock.elapsedRealtime();
        Iterator<JobStatus> it = jobs.iterator();
        boolean jobNeedsRun = false;
        while (it.hasNext()) {
            JobStatus job = it.next();
            if (!job.hasDeadlineConstraint()) {
                continue;
            }
            final long jobDeadline = job.getLatestRunTimeElapsed();
            if (jobDeadline <= nowElapsedMillis) {
                job.deadlineConstraintSatisfied.set(true);
                jobNeedsRun = true;
                it.remove();
            } else { // Sorted by expiry time, so take the next one and stop.
                nextExpiryTime = jobDeadline;
                break;
            }
        }

        if (jobNeedsRun) {
            startWakefulService(context, JobServiceCompat.maybeRunJobs(context));
        }

        return setDeadlineExpiredAlarm(context, nextExpiryTime);
    }

    private static long checkExpiredDelaysAndResetAlarm(Context context, List<JobStatus> jobs) {
        final long nowElapsedMillis = SystemClock.elapsedRealtime();
        long nextDelayTime = Long.MAX_VALUE;
        boolean ready = false;
        Iterator<JobStatus> it = jobs.iterator();
        while (it.hasNext()) {
            final JobStatus job = it.next();
            if (!job.hasTimingDelayConstraint()) {
                continue;
            }
            final long jobDelayTime = job.getEarliestRunTime();
            if (jobDelayTime <= nowElapsedMillis) {
                job.timeDelayConstraintSatisfied.set(true);
                if (canStopTrackingJob(job)) {
                    it.remove();
                }
                if (job.isReady()) {
                    ready = true;
                }
            } else { // Keep going through list to get next delay time.
                if (nextDelayTime > jobDelayTime) {
                    nextDelayTime = jobDelayTime;
                }
            }
        }
        if (ready) {
            startWakefulService(context, JobServiceCompat.maybeRunJobs(context));
        }
        return setDelayExpiredAlarm(context, nextDelayTime);
    }


    private static long setDelayExpiredAlarm(Context context, long alarmTimeElapsedMillis) {
        alarmTimeElapsedMillis = maybeAdjustAlarmTime(alarmTimeElapsedMillis);
        long nextDelayExpiredElapsedMillis = alarmTimeElapsedMillis;
        Intent intent = new Intent(context, TimeReceiver.class);
        intent.setAction(ACTION_JOB_DELAY_EXPIRED);
        PendingIntent nextDelayExpiredAlarmIntent =
                PendingIntent.getBroadcast(context, 0 /* ignored */, intent, 0);
        updateAlarmWithPendingIntent(context, nextDelayExpiredAlarmIntent, nextDelayExpiredElapsedMillis);
        return nextDelayExpiredElapsedMillis;
    }

    private static long setDeadlineExpiredAlarm(Context context, long alarmTimeElapsedMillis) {
        alarmTimeElapsedMillis = maybeAdjustAlarmTime(alarmTimeElapsedMillis);
        long nextJobExpiredElapsedMillis = alarmTimeElapsedMillis;
        Intent intent = new Intent(context, TimeReceiver.class);
        intent.setAction(ACTION_JOB_EXPIRED);
        PendingIntent deadlineExpiredAlarmIntent =
                PendingIntent.getBroadcast(context, 0 /* ignored */, intent, 0);
        updateAlarmWithPendingIntent(context, deadlineExpiredAlarmIntent, nextJobExpiredElapsedMillis);
        return nextJobExpiredElapsedMillis;
    }

    private static long maybeAdjustAlarmTime(long proposedAlarmTimeElapsedMillis) {
        final long earliestWakeupTimeElapsed = SystemClock.elapsedRealtime();
        if (proposedAlarmTimeElapsedMillis < earliestWakeupTimeElapsed) {
            return earliestWakeupTimeElapsed;
        }
        return proposedAlarmTimeElapsedMillis;
    }

    private static void updateAlarmWithPendingIntent(Context context, PendingIntent pi, long alarmTimeElapsed) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmTimeElapsed == Long.MAX_VALUE) {
            am.cancel(pi);
        } else {
            am.set(AlarmManager.ELAPSED_REALTIME, alarmTimeElapsed, pi);
        }
    }

    private static void maybeUpdateAlarms(Context context, long delayExpiredElapsed, long deadlineExpiredElapsed) {
        ControllerPrefs prefs = ControllerPrefs.getInstance(context);
        long nextDelayExpiredElapsedMillis = prefs.getNextDelayExpiredElapsedMillis();
        long nextJobExpiredElapsedMillis = prefs.getNextJobExpiredElapsedMillis();

        if (delayExpiredElapsed < nextDelayExpiredElapsedMillis) {
            nextDelayExpiredElapsedMillis = setDelayExpiredAlarm(context, delayExpiredElapsed);
        }

        if (deadlineExpiredElapsed < nextJobExpiredElapsedMillis) {
            nextJobExpiredElapsedMillis = setDeadlineExpiredAlarm(context, deadlineExpiredElapsed);
        }

        prefs.edit()
                .setNextDelayExipredElapsedMillis(nextDelayExpiredElapsedMillis)
                .setNextJobExpiredElapsedMillis(nextJobExpiredElapsedMillis)
                .apply();
    }

    /**
     * Determines whether this controller can stop tracking the given job.
     * The controller is no longer interested in a job once its time constraint is satisfied, and
     * the job's deadline is fulfilled - unlike other controllers a time constraint can't toggle
     * back and forth.
     */
    private static boolean canStopTrackingJob(JobStatus job) {
        return (!job.hasTimingDelayConstraint() ||
                job.timeDelayConstraintSatisfied.get()) &&
                (!job.hasDeadlineConstraint() ||
                        job.deadlineConstraintSatisfied.get());
    }

    private static List<JobStatus> getJobsSortedByLatestRuntime(Context context) {
        List<JobStatus> result = new ArrayList<JobStatus>();
        JobStore jobStore = JobStore.initAndGet(context);
        synchronized (jobStore) {
            ArraySet<JobStatus> jobs = jobStore.getJobs();
            for (int i = 0; i < jobs.size(); i++) {
                JobStatus job = jobs.valueAt(i);
                if (job.hasTimingDelayConstraint() || job.hasDeadlineConstraint()) {
                    result.add(job);
                }
            }
        }
        Collections.sort(result, new Comparator<JobStatus>() {
            @Override
            public int compare(JobStatus lhs, JobStatus rhs) {
                return Long.valueOf(lhs.getLatestRunTimeElapsed()).compareTo(rhs.getLatestRunTimeElapsed());
            }
        });
        return result;
    }
}
