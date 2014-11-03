package me.tatarka.support.job;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

/**
 * Created by evantatarka on 10/31/14.
 */
public class PowerReceiver extends WakefulBroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        boolean connected = false;
        if (intent.getAction().equals(Intent.ACTION_POWER_CONNECTED)) {
            connected = true;
        } else if (intent.getAction().equals(Intent.ACTION_POWER_DISCONNECTED)) {
            connected = false;
        }
        ReceiverUtils.disable(context, PowerReceiver.class);
        startWakefulService(context, JobServiceCompat.powerStateChangedIntent(context, connected));
    }
}
