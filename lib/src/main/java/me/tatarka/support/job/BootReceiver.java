package me.tatarka.support.job;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

/**
 * Created by evantatarka on 11/7/14.
 */
public class BootReceiver extends WakefulBroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        startWakefulService(context, JobServiceCompat.bootIntent(context));
    }
}
