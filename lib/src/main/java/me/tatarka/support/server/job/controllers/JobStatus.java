/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package me.tatarka.support.server.job.controllers;

import android.content.ComponentName;
import android.os.Parcelable;
import android.os.SystemClock;
import android.text.format.DateUtils;

import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;

import me.tatarka.support.job.JobInfo;
import me.tatarka.support.os.PersistableBundle;

/**
 * Uniquely identifies a job internally.
 * Created from the public {@link android.app.job.JobInfo} object when it lands on the scheduler.
 * Contains current state of the requirements of the job, as well as a function to evaluate
 * whether it's ready to run.
 * This object is shared among the various controllers - hence why the different fields are atomic.
 * This isn't strictly necessary because each controller is only interested in a specific field,
 * and the receivers that are listening for global state change will all run on the main looper,
 * but we don't enforce that so this is safer.
 *
 * @hide
 */
public class JobStatus {
    public static final long NO_LATEST_RUNTIME = Long.MAX_VALUE;
    public static final long NO_EARLIEST_RUNTIME = 0L;

    final JobInfo job;
    final String name;
    final String tag;

    // Constraints.
    public final AtomicBoolean chargingConstraintSatisfied = new AtomicBoolean();
    public final AtomicBoolean timeDelayConstraintSatisfied = new AtomicBoolean();
    public final AtomicBoolean deadlineConstraintSatisfied = new AtomicBoolean();
    public final AtomicBoolean idleConstraintSatisfied = new AtomicBoolean();
    public final AtomicBoolean unmeteredConstraintSatisfied = new AtomicBoolean();
    public final AtomicBoolean connectivityConstraintSatisfied = new AtomicBoolean();

    /**
     * Earliest point in the future at which this job will be eligible to run. A value of 0
     * indicates there is no delay constraint. See {@link #hasTimingDelayConstraint()}.
     */
    private long earliestRunTimeElapsedMillis;
    /**
     * Latest point in the future at which this job must be run. A value of {@link Long#MAX_VALUE}
     * indicates there is no deadline constraint. See {@link #hasDeadlineConstraint()}.
     */
    private long latestRunTimeElapsedMillis;
    /**
     * How many times this job has failed, used to compute back-off.
     */
    private final int numFailures;

    private JobStatus(JobInfo job, int numFailures) {
        this.job = job;
        this.name = job.getService().flattenToShortString();
        this.tag = "*job*/" + this.name;
        this.numFailures = numFailures;
    }

    /**
     * Create a newly scheduled job.
     */
    public JobStatus(JobInfo job) {
        this(job, 0);

        final long elapsedNow = SystemClock.elapsedRealtime();

        if (job.isPeriodic()) {
            earliestRunTimeElapsedMillis = elapsedNow;
            latestRunTimeElapsedMillis = elapsedNow + job.getIntervalMillis();
        } else {
            earliestRunTimeElapsedMillis = job.hasEarlyConstraint() ?
                    elapsedNow + job.getMinLatencyMillis() : NO_EARLIEST_RUNTIME;
            latestRunTimeElapsedMillis = job.hasLateConstraint() ?
                    elapsedNow + job.getMaxExecutionDelayMillis() : NO_LATEST_RUNTIME;
        }
    }

    /**
     * Create a new JobStatus that was loaded from disk. We ignore the provided
     * {@link JobInfo} time criteria because we can load a persisted periodic job
     * from the {@link me.tatarka.support.server.job.JobStore} and still want to respect its
     * wallclock runtime rather than resetting it on every boot.
     * We consider a freshly loaded job to no longer be in back-off.
     */
    public JobStatus(JobInfo job, long earliestRunTimeElapsedMillis,
                     long latestRunTimeElapsedMillis) {
        this(job, 0);

        this.earliestRunTimeElapsedMillis = earliestRunTimeElapsedMillis;
        this.latestRunTimeElapsedMillis = latestRunTimeElapsedMillis;
    }

    /**
     * Create a new job to be rescheduled with the provided parameters.
     */
    public JobStatus(JobStatus rescheduling, long newEarliestRuntimeElapsedMillis,
                     long newLatestRuntimeElapsedMillis, int backoffAttempt) {
        this(rescheduling.job, backoffAttempt);

        earliestRunTimeElapsedMillis = newEarliestRuntimeElapsedMillis;
        latestRunTimeElapsedMillis = newLatestRuntimeElapsedMillis;
    }

    public JobInfo getJob() {
        return job;
    }

    public int getJobId() {
        return job.getId();
    }

    public int getNumFailures() {
        return numFailures;
    }

    public ComponentName getServiceComponent() {
        return job.getService();
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return tag;
    }

    public PersistableBundle getExtras() {
        return job.getExtras();
    }

    public boolean hasConnectivityConstraint() {
        return job.getNetworkType() == JobInfo.NETWORK_TYPE_ANY;
    }

    public boolean hasUnmeteredConstraint() {
        return job.getNetworkType() == JobInfo.NETWORK_TYPE_UNMETERED;
    }

    public boolean hasChargingConstraint() {
        return job.isRequireCharging();
    }

    public boolean hasTimingDelayConstraint() {
        return earliestRunTimeElapsedMillis != NO_EARLIEST_RUNTIME;
    }

    public boolean hasDeadlineConstraint() {
        return latestRunTimeElapsedMillis != NO_LATEST_RUNTIME;
    }

    public boolean hasIdleConstraint() {
        return job.isRequireDeviceIdle();
    }

    public boolean isPersisted() {
        return job.isPersisted();
    }

    public long getEarliestRunTime() {
        return earliestRunTimeElapsedMillis;
    }

    public long getLatestRunTimeElapsed() {
        return latestRunTimeElapsedMillis;
    }

    /**
     * @return Whether or not this job is ready to run, based on its requirements. This is true if
     * the constraints are satisfied <strong>or</strong> the deadline on the job has expired.
     */
    public synchronized boolean isReady() {
        return isConstraintsSatisfied()
                || (hasDeadlineConstraint() && deadlineConstraintSatisfied.get());
    }

    /**
     * @return Whether the constraints set on this job are satisfied.
     */
    public synchronized boolean isConstraintsSatisfied() {
        return (!hasChargingConstraint() || chargingConstraintSatisfied.get())
                && (!hasTimingDelayConstraint() || timeDelayConstraintSatisfied.get())
                && (!hasConnectivityConstraint() || connectivityConstraintSatisfied.get())
                && (!hasUnmeteredConstraint() || unmeteredConstraintSatisfied.get())
                && (!hasIdleConstraint() || idleConstraintSatisfied.get());
    }

    public boolean matches(int jobId) {
        return this.job.getId() == jobId;
    }

    @Override
    public String toString() {
        return String.valueOf(hashCode()).substring(0, 3) + ".."
                + ":[" + job.getService()
                + ",jId=" + job.getId()
                + ",R=(" + formatRunTime(earliestRunTimeElapsedMillis, NO_EARLIEST_RUNTIME)
                + "," + formatRunTime(latestRunTimeElapsedMillis, NO_LATEST_RUNTIME) + ")"
                + ",N=" + job.getNetworkType() + ",C=" + job.isRequireCharging()
                + ",I=" + job.isRequireDeviceIdle() + ",F=" + numFailures
                + ",P=" + job.isPersisted()
                + (isReady() ? "(READY)" : "")
                + "]";
    }

    private String formatRunTime(long runtime, long defaultValue) {
        if (runtime == defaultValue) {
            return "none";
        } else {
            long elapsedNow = SystemClock.elapsedRealtime();
            long nextRuntime = runtime - elapsedNow;
            if (nextRuntime > 0) {
                return DateUtils.formatElapsedTime(nextRuntime / 1000);
            } else {
                return "-" + DateUtils.formatElapsedTime(nextRuntime / -1000);
            }
        }
    }

    /**
     * Convenience function to identify a job uniquely without pulling all the data that
     * {@link #toString()} returns.
     */
    public String toShortString() {
        return job.getService().flattenToShortString() + " jId=" + job.getId();
    }

    // Dumpsys infrastructure
    public void dump(PrintWriter pw, String prefix) {
        pw.print(prefix);
        pw.println(this.toString());
    }
}
