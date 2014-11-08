package me.tatarka.support.job;

/**
 * Created by evantatarka on 11/7/14.
 */
class JobInfoUtil {
    static boolean hasRequiredNetwork(JobInfo job, int networkType) {
        return job.getNetworkType() == JobInfo.NETWORK_TYPE_NONE
                || networkType != JobInfo.NETWORK_TYPE_NONE && !(job.getNetworkType() == JobInfo.NETWORK_TYPE_UNMETERED && networkType != JobInfo.NETWORK_TYPE_UNMETERED);
    }

    static boolean hasRequiredPowerState(JobInfo job, boolean isCharging) {
        return !job.isRequireCharging() || isCharging;
    }
}
