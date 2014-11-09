package me.tatarka.support.server.job.controllers;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

import me.tatarka.support.internal.util.ArraySet;
import me.tatarka.support.server.job.JobServiceCompat;
import me.tatarka.support.server.job.JobStore;

/**
 * @hide
 */
public class BootReceiver extends WakefulBroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        startWakefulService(context, JobServiceCompat.bootIntent(context));
    }
}
