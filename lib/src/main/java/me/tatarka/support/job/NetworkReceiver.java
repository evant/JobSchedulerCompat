package me.tatarka.support.job;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.support.v4.net.ConnectivityManagerCompat;

/**
 * @hide *
 */
public class NetworkReceiver extends WakefulBroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnectedOrConnecting()) {
            ReceiverUtils.disable(context, getClass());
            startWakefulService(context, JobServiceCompat.requiredStateChangedIntent(context));
        }
    }
}
