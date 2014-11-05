package me.tatarka.support.job;

import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

import me.tatarka.support.os.PersistableBundle;

/**
 * Created by evantatarka on 10/23/14.
 */
public class JobParameters implements Parcelable {
    private final int jobId;
    private final PersistableBundle extras;
    private final IBinder callback;
    private final boolean overrideDeadlineExpired;

    /** @hide */
    public JobParameters(IBinder callback, int jobId, PersistableBundle extras,
                         boolean overrideDeadlineExpired) {
        this.jobId = jobId;
        this.extras = extras;
        this.callback = callback;
        this.overrideDeadlineExpired = overrideDeadlineExpired;
    }

    /**
     * @return The unique id of this job, specified at creation time.
     */
    public int getJobId() {
        return jobId;
    }

    /**
     * @return The extras you passed in when constructing this job with
     * {@link android.app.job.JobInfo.Builder#setExtras(android.os.PersistableBundle)}. This will
     * never be null. If you did not set any extras this will be an empty bundle.
     */
    public PersistableBundle getExtras() {
        return extras;
    }

    /**
     * For jobs with {@link android.app.job.JobInfo.Builder#setOverrideDeadline(long)} set, this
     * provides an easy way to tell whether the job is being executed due to the deadline
     * expiring. Note: If the job is running because its deadline expired, it implies that its
     * constraints will not be met.
     */
    public boolean isOverrideDeadlineExpired() {
        return overrideDeadlineExpired;
    }

    /** @hide */
    public IJobCallback getCallback() {
        return IJobCallback.Stub.asInterface(callback);
    }

    private JobParameters(Parcel in) {
        jobId = in.readInt();
        extras = PersistableBundle.readPersistableBundle(in);
        callback = in.readStrongBinder();
        overrideDeadlineExpired = in.readInt() == 1;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(jobId);
        PersistableBundle.writePersitableBundle(extras, dest);
        dest.writeStrongBinder(callback);
        dest.writeInt(overrideDeadlineExpired ? 1 : 0);
    }

    public static final Creator<JobParameters> CREATOR = new Creator<JobParameters>() {
        @Override
        public JobParameters createFromParcel(Parcel in) {
            return new JobParameters(in);
        }

        @Override
        public JobParameters[] newArray(int size) {
            return new JobParameters[size];
        }
    };
}

