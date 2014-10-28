package me.tatarka.support.job;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by evan on 10/27/14.
 */
abstract class IJobServiceCompat {
    private IBinder mBinder;

    IJobServiceCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBinder = new android.app.job.IJobService.Stub() {
                @Override
                public void startJob(android.app.job.JobParameters jobParams) throws RemoteException {
                    IJobServiceCompat.this.startJob(convertFromJobParameters(jobParams));
                }

                @Override
                public void stopJob(android.app.job.JobParameters jobParams) throws RemoteException {
                    IJobServiceCompat.this.stopJob(convertFromJobParameters(jobParams));
                }
            }.asBinder();
        } else {
            mBinder = new IJobService.Stub() {
                @Override
                public void startJob(JobParameters jobParams) throws RemoteException {
                    IJobServiceCompat.this.startJob(jobParams);
                }

                @Override
                public void stopJob(JobParameters jobParams) throws RemoteException {
                    IJobServiceCompat.this.stopJob(jobParams);
                }
            }.asBinder();
        }
    }

    public abstract void startJob(JobParameters jobParams);
    public abstract void stopJob(JobParameters jobParams);

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static me.tatarka.support.job.JobParameters convertFromJobParameters(final android.app.job.JobParameters params) {
        return new me.tatarka.support.job.JobParameters(new IJobCallback.Stub() {
            @Override
            public void jobFinished(int jobId, boolean needsReschedule) throws RemoteException {
                android.app.job.IJobCallback callback = getCallback(params);
                if (callback != null) {
                    callback.jobFinished(jobId, needsReschedule);
                }
            }

            @Override
            public void acknowledgeStartMessage(int jobId, boolean workOngoing) throws RemoteException {
                android.app.job.IJobCallback callback = getCallback(params);
                if (callback != null) {
                    callback.acknowledgeStartMessage(jobId, workOngoing);
                }
            }

            @Override
            public void acknowledgeStopMessage(int jobId, boolean reschedule) throws RemoteException {
                android.app.job.IJobCallback callback = getCallback(params);
                if (callback != null) {
                    callback.acknowledgeStopMessage(jobId, reschedule);
                }
            }
        }, params.getJobId(), params.getExtras(), params.isOverrideDeadlineExpired());
    }

    public IBinder asBinder() {
        return mBinder;
    }

    // getCallback() is a hidden method on JobParameters, so use reflection to get at it.
    private static android.app.job.IJobCallback getCallback(android.app.job.JobParameters params) {
        Method method;
        try {
            method = params.getClass().getDeclaredMethod("getCallback");
            method.setAccessible(true);
            return (android.app.job.IJobCallback) method.invoke(params);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
