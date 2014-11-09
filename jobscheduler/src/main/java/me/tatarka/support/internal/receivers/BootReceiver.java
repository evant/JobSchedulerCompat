package me.tatarka.support.internal.receivers;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

import me.tatarka.support.internal.job.JobServiceCompat;

/**
 * @hide
 */
public class BootReceiver extends WakefulBroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        startWakefulService(context, JobServiceCompat.bootIntent(context));
    }
}
