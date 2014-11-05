package me.tatarka.support.job;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.support.v4.net.ConnectivityManagerCompat;
import android.util.Log;
import android.util.SparseArray;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import me.tatarka.support.internal.util.FastXmlSerializer;
import me.tatarka.support.internal.util.XmlUtils;
import me.tatarka.support.os.PersistableBundle;

/**
 * @hide *
 */
public class JobServiceCompat extends Service {
    static final String TAG = "JobServiceCompat";

    private static final String EXTRA_MSG = "EXTRA_MSG";
    private static final String EXTRA_JOB = "EXTRA_JOB";
    private static final String EXTRA_JOB_ID = "EXTRA_JOB_ID";
    private static final String EXTRA_RUN_IMMEDIATELY = "EXTRA_RUN_IMMEDIATELY";
    private static final String EXTRA_NETWORK_STATE_METERED = "EXTRA_NETWORK_STATE_METERED";
    private static final String EXTRA_POWER_STATE_CONNECTED = "EXTRA_POWER_STATE_CONNECTED";
    private static final String EXTRA_NUM_FAILURES = "EXTRA_NUM_FAILURES";

    private static final int MSG_SCHEDULE_JOB = 0;
    private static final int MSG_START_JOB = 1;
    private static final int MSG_CANCEL_JOB = 2;
    private static final int MSG_NETWORK_STATE_CHANGED = 3;
    private static final int MSG_POWER_STATE_CHANGED = 4;

    private JobHandler handler;
    private AlarmManager am;

    private SparseArray<JobServiceConnection> runningJobs = new SparseArray<JobServiceConnection>();

    private final Object handlerLock = new Object();

    private void ensureHandler() {
        synchronized (handlerLock) {
            if (handler == null) {
                handler = new JobHandler(getMainLooper());
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureHandler();
        int what = intent.getIntExtra(EXTRA_MSG, -1);
        Message message = Message.obtain(handler, what, intent);
        handler.handleMessage(message);

        return START_NOT_STICKY;
    }

    private class JobHandler extends Handler {
        public JobHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Intent intent = (Intent) msg.obj;
            switch (msg.what) {
                case MSG_SCHEDULE_JOB: {
                    JobInfo job = intent.getParcelableExtra(EXTRA_JOB);
                    handleSchedule(job);
                    break;
                }
                case MSG_START_JOB: {
                    JobInfo job = intent.getParcelableExtra(EXTRA_JOB);
                    int numFailures = intent.getIntExtra(EXTRA_NUM_FAILURES, 0);
                    boolean runImmediately = intent.getBooleanExtra(EXTRA_RUN_IMMEDIATELY, false);
                    handleStartJob(job, numFailures, runImmediately);
                    break;
                }
                case MSG_CANCEL_JOB: {
                    int jobId = intent.getIntExtra(EXTRA_JOB_ID, 0);
                    handleCancelJob(jobId);
                    break;
                }
                case MSG_NETWORK_STATE_CHANGED: {
                    boolean metered = intent.getBooleanExtra(EXTRA_NETWORK_STATE_METERED, false);
                    handleNetworkStateChanged(intent, metered);
                    break;
                }
                case MSG_POWER_STATE_CHANGED: {
                    boolean connected = intent.getBooleanExtra(EXTRA_POWER_STATE_CONNECTED, false);
                    handlePowerStateChanged(intent, connected);
                    break;
                }
            }
        }
    }

    private void handleSchedule(JobInfo job) {
        long elapsedTime = SystemClock.elapsedRealtime();

        if (job.hasEarlyConstraint()) {
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, elapsedTime + job.getMinLatencyMillis(), toPendingIntent(job, 0, false));
        }

        if (job.hasLateConstraint()) {
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, elapsedTime + job.getMaxExecutionDelayMillis(), toPendingIntent(job, 0, true));
        }

        stopIfFinished();
    }

    private void handleReschedule(JobInfo job, int numFailures) {
        if (job.isRequireDeviceIdle()) {
            // TODO: different reschedule policy
            throw new UnsupportedOperationException("rescheduling idle tasks is not yet implemented");
        }

        long backoffTime;
        switch (job.getBackoffPolicy()) {
            case JobInfo.BACKOFF_POLICY_LINEAR:
                backoffTime = job.getInitialBackoffMillis() * numFailures;
                break;
            case JobInfo.BACKOFF_POLICY_EXPONENTIAL:
                backoffTime = job.getInitialBackoffMillis() * (long) Math.pow(2, numFailures - 1);
                break;
            default:
                throw new IllegalArgumentException("Unknown backoff policy: " + job.getBackoffPolicy());
        }

        if (backoffTime > 5 * 60 * 60 * 1000 /* 5 hours*/) {
            // We have backed-off too long, give up.
            return;
        }

        long elapsedTime = SystemClock.elapsedRealtime();
        am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, elapsedTime + backoffTime, toPendingIntent(job, numFailures, true));

        stopIfFinished();
    }

    private void handleCancelJob(int jobId) {
        PendingIntent pendingIntentFirst = toPendingIntentToCancel(jobId, false);
        PendingIntent pendingIntentSecond = toPendingIntentToCancel(jobId, true);

        if (pendingIntentFirst != null) {
            am.cancel(pendingIntentFirst);
        }
        if (pendingIntentSecond != null) {
            am.cancel(pendingIntentSecond);
        }

        JobServiceConnection runningJob = runningJobs.get(jobId);
        if (runningJob != null) {
            runningJob.stop();
        }

        stopIfFinished();
    }

    private void handleStartJob(final JobInfo job, int numFailures, boolean runImmediately) {
        Intent intent = new Intent();
        intent.setComponent(job.getService());

        int flags = BIND_AUTO_CREATE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            flags |= BIND_WAIVE_PRIORITY;
        }

        if (!runImmediately) {
            int requirementFlags = 0;
            
            // Ensure network
            if (job.getNetworkType() != JobInfo.NETWORK_TYPE_NONE) {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                NetworkInfo netInfo = cm.getActiveNetworkInfo();
                boolean connected = netInfo != null && netInfo.isConnectedOrConnecting();
                boolean hasNecessaryNetwork = connected
                        && (job.getNetworkType() != JobInfo.NETWORK_TYPE_UNMETERED
                        || !ConnectivityManagerCompat.isActiveNetworkMetered(cm));

                if (!hasNecessaryNetwork) {
                    requirementFlags |= JobPersister.FLAG_NETWORK;
                }
            }

            // Ensure power
            if (job.isRequireCharging()) {
                Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                int plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                boolean hasNecessaryPower = plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB;

                if (!hasNecessaryPower) {
                    requirementFlags |= JobPersister.FLAG_POWER;
                }
            }

            // Register listener and fire job requirements are met.
            if (requirementFlags != 0) {
                JobPersister.getInstance(this).addJob(job, requirementFlags);
                
                if ((requirementFlags & JobPersister.FLAG_NETWORK) == JobPersister.FLAG_NETWORK) {
                    ReceiverUtils.enable(this, NetworkReceiver.class);
                }
                if ((requirementFlags & JobPersister.FLAG_POWER) == JobPersister.FLAG_POWER) {
                    ReceiverUtils.enable(this, PowerReceiver.class);
                }
                return;
            }

            // Ensure late constraint alarm is canceled
            if (job.hasLateConstraint()) {
                PendingIntent pendingIntent = toPendingIntentToCancel(job.getId(), true);
                if (pendingIntent != null) {
                    am.cancel(pendingIntent);
                }
            }
        }

        JobServiceConnection connection = new JobServiceConnection(job, numFailures);
        runningJobs.put(job.getId(), connection);
        bindService(intent, connection, flags);
    }

    private void handleNetworkStateChanged(Intent intent, boolean metered) {
        List<JobInfo> pendingNetworkJobs = JobPersister.getInstance(this).getJobs(JobPersister.FLAG_NETWORK);
        for (JobInfo job : pendingNetworkJobs) {
            if (job.getNetworkType() == (metered ? JobInfo.NETWORK_TYPE_ANY : JobInfo.NETWORK_TYPE_UNMETERED)) {
                handleStartJob(job, 0, false);
            } else {
                JobPersister.getInstance(this).addJob(job, JobPersister.FLAG_NETWORK);
            }
        }
        WakefulBroadcastReceiver.completeWakefulIntent(intent);
    }

    private void handlePowerStateChanged(Intent intent, boolean connected) {
        List<JobInfo> pendingPowerJobs = JobPersister.getInstance(this).getJobs(JobPersister.FLAG_POWER);
        for (JobInfo job : pendingPowerJobs) {
            if (job.isRequireCharging() && connected) {
                handleStartJob(job, 0, false);
            } else {
                JobPersister.getInstance(this).addJob(job, JobPersister.FLAG_POWER);
            }
        }
        WakefulBroadcastReceiver.completeWakefulIntent(intent);
    }

    private PendingIntent toPendingIntent(JobInfo job, int numFailures, boolean runImmediately) {
        Intent intent = new Intent(this, JobServiceCompat.class)
                .setAction(job.getId() + ":" + runImmediately)
                .putExtra(EXTRA_MSG, MSG_START_JOB)
                .putExtra(EXTRA_JOB, job)
                .putExtra(EXTRA_NUM_FAILURES, numFailures)
                .putExtra(EXTRA_RUN_IMMEDIATELY, runImmediately);
        return PendingIntent.getService(this, job.getId(), intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent toPendingIntentToCancel(int jobId, boolean runImmediately) {
        Intent intent = new Intent(this, JobServiceCompat.class)
                .setAction(jobId + ":" + runImmediately);
        return PendingIntent.getService(this, jobId, intent, PendingIntent.FLAG_NO_CREATE);
    }

    private PendingIntent getNetworkStatePendingIntent() {
        Intent intent = new Intent(this, JobServiceCompat.class)
                .setAction("me.tatarka.support.job.NETWORK_STATE");
        return PendingIntent.getService(this, 0, intent, 0);
    }

    static void schedule(Context context, JobInfo job) {
        context.startService(
                new Intent(context, JobServiceCompat.class)
                        .putExtra(EXTRA_MSG, MSG_SCHEDULE_JOB)
                        .putExtra(EXTRA_JOB, job));
    }

    static void cancel(Context context, int jobId) {
        context.startService(
                new Intent(context, JobServiceCompat.class)
                        .putExtra(EXTRA_MSG, MSG_CANCEL_JOB)
                        .putExtra(EXTRA_JOB_ID, jobId));
    }

    static Intent networkStateChangedIntent(Context context, boolean metered) {
        return new Intent(context, JobServiceCompat.class)
                .putExtra(EXTRA_MSG, MSG_NETWORK_STATE_CHANGED)
                .putExtra(EXTRA_NETWORK_STATE_METERED, metered);
    }

    static Intent powerStateChangedIntent(Context context, boolean connected) {
        return new Intent(context, JobServiceCompat.class)
                .putExtra(EXTRA_MSG, MSG_POWER_STATE_CHANGED)
                .putExtra(EXTRA_POWER_STATE_CONNECTED, connected);
    }

    private class JobServiceConnection implements ServiceConnection {
        JobInfo job;
        JobParameters jobParams;
        IJobService jobService;

        JobServiceConnection(final JobInfo job, final int numFailures) {
            this.job = job;
            this.jobParams = new JobParameters(new IJobCallback.Stub() {
                @Override
                public void jobFinished(int jobId, boolean needsReschedule) throws RemoteException {
                    finishJob(jobId, JobServiceConnection.this);

                    if (needsReschedule) {
                        handleReschedule(job, numFailures + 1);
                    } else if (runningJobs.size() == 0) {
                        stopSelf();
                    }
                }

                @Override
                public void acknowledgeStartMessage(int jobId, boolean workOngoing) throws RemoteException {
                    if (!workOngoing) {
                        finishJob(jobId, JobServiceConnection.this);
                        if (runningJobs.size() == 0) {
                            stopSelf();
                        }
                    }
                }

                @Override
                public void acknowledgeStopMessage(int jobId, boolean reschedule) throws RemoteException {
                    finishJob(jobId, JobServiceConnection.this);

                    if (reschedule) {
                        handleReschedule(job, numFailures + 1);
                    } else if (runningJobs.size() == 0) {
                        stopSelf();
                    }
                }
            }, job.getId(), job.getExtras(), false);
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            jobService = (IJobService) service;

            try {
                jobService.startJob(jobParams);
            } catch (Exception e) {
                Log.e(TAG, "Error while starting job: " + job.getId());
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            runningJobs.remove(job.getId());
            jobService = null;
            jobParams = null;
        }

        void stop() {
            if (jobService != null) {
                try {
                    jobService.stopJob(jobParams);
                } catch (Exception e) {
                    Log.e(TAG, "Error while stopping job: " + job.getId());
                    throw new RuntimeException(e);
                }
            }
        }

        private void finishJob(int jobId, JobServiceConnection connection) {
            if (runningJobs.get(jobId) != null) {
                unbindService(connection);
            }
            runningJobs.remove(jobId);
            stopIfFinished();
        }
    }

    private void stopIfFinished() {
        if (runningJobs.size() == 0) {
            stopSelf();
        }
    }

    private static class JobPersister {
        static final int FLAG_NETWORK = 1;
        static final int FLAG_POWER = 2;
        static final int FLAG_BOOT = 4;

        private static final String ID = "id";
        private static final String EXTRAS = "extras";
        private static final String SERVICE = "service";
        private static final String REQURIE_CHARGING = "requireCharging";
        private static final String REQUIRE_DEVICE_IDLE = "requireDeviceIdle";
        private static final String NETWORK_TYPE = "networkType";
        private static final String MIN_LATENCY_MILLIS = "minLatencyMillis";
        private static final String MAX_EXECUTION_DELAY_MILLIS = "maxExecutionDelayMillis";
        private static final String PERIODIC = "periodic";
        private static final String PERSISTED = "persisted";
        private static final String INTERVAL_MILLIS = "intervalMillis";
        private static final String INITIAL_BACKOFF_MILLIS = "initialBackoffMillis";
        private static final String BACKOFF_POLICY = "backoffPolicy";

        private static JobPersister INSTANCE;

        static JobPersister getInstance(Context context) {
            if (INSTANCE == null) {
                INSTANCE = new JobPersister(context);
            }
            return INSTANCE;
        }

        private Context context;
        private SharedPreferences sharedPrefs;

        JobPersister(Context context) {
            this.context = context.getApplicationContext();
            sharedPrefs = context.getSharedPreferences("me.tatarka.support.job.SHARE_PREFS", MODE_PRIVATE);
        }

        void addJob(JobInfo job, int flag) {
            try {
                writeJob(job);
                sharedPrefs.edit().putInt(Integer.toString(job.getId()), flag).commit();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage(), e);
            } catch (XmlPullParserException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }

        private void writeJob(JobInfo job) throws IOException, XmlPullParserException {
            FileOutputStream os = null;
            try {
                os = context.openFileOutput(Integer.toString(job.getId()), MODE_PRIVATE);
                XmlSerializer serializer = new FastXmlSerializer();
                serializer.setOutput(os, "utf-8");
                serializer.startDocument(null, true);
                serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
                XmlUtils.writeMapXml(jobToMap(job), null, serializer, job.getExtras());
                serializer.endDocument();
            } finally {
                if (os != null) os.close();
            }
        }

        private JobInfo readJob(int jobId) throws IOException, XmlPullParserException {
            FileInputStream in = null;
            try {
                in = context.openFileInput(Integer.toString(jobId));
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(in, null);
                Map<String, ?> map = XmlUtils.readThisMapXml(parser, null, new String[1], new PersistableBundle.MyReadMapCallback());
                return jobFromMap(map);
            } finally {
                if (in != null) in.close();
            }
        }

        private static Map<String, ?> jobToMap(JobInfo job) {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put(ID, job.getId());
            map.put(EXTRAS, job.getExtras());
            map.put(SERVICE, job.getService().flattenToString());
            map.put(REQURIE_CHARGING, job.isRequireCharging());
            map.put(REQUIRE_DEVICE_IDLE, job.isRequireDeviceIdle());
            map.put(NETWORK_TYPE, job.getNetworkType());
            map.put(MIN_LATENCY_MILLIS, job.getMinLatencyMillis());
            map.put(MAX_EXECUTION_DELAY_MILLIS, job.getMaxExecutionDelayMillis());
            map.put(PERIODIC, job.isPeriodic());
            map.put(PERSISTED, job.isPersisted());
            map.put(INTERVAL_MILLIS, job.getIntervalMillis());
            map.put(INITIAL_BACKOFF_MILLIS, job.getInitialBackoffMillis());
            map.put(BACKOFF_POLICY, job.getBackoffPolicy());
            return map;
        }

        private static JobInfo jobFromMap(Map<String, ?> map) {
            JobInfo.Builder builder = new JobInfo.Builder((Integer) map.get(ID), ComponentName.unflattenFromString((String) map.get(SERVICE)));
            builder.setExtras((PersistableBundle) map.get(EXTRAS));
            builder.setRequiresCharging((Boolean) map.get(REQURIE_CHARGING));
            builder.setRequiresDeviceIdle((Boolean) map.get(REQUIRE_DEVICE_IDLE));
            builder.setRequiredNetworkType((Integer) map.get(NETWORK_TYPE));

            int minLatenceyMillis = (Integer) map.get(MIN_LATENCY_MILLIS);
            if (minLatenceyMillis != 0) {
                builder.setMinimumLatency(minLatenceyMillis);
            }

            int maxExecutionDelayMillis = (Integer) map.get(MAX_EXECUTION_DELAY_MILLIS);
            if (maxExecutionDelayMillis != 0) {
                builder.setOverrideDeadline(maxExecutionDelayMillis);
            }

            if ((Boolean) map.get(PERIODIC)) {
                builder.setPeriodic((Long) map.get(INTERVAL_MILLIS));
            }

            builder.setPersisted((Boolean) map.get(PERSISTED));
            builder.setBackoffCriteria((Long) map.get(INITIAL_BACKOFF_MILLIS), (Integer) map.get(BACKOFF_POLICY));

            return builder.build();
        }

        List<JobInfo> getJobs(int flag) {
            SharedPreferences.Editor editor = sharedPrefs.edit();
            try {
                Map<String, ?> prefMap = sharedPrefs.getAll();
                List<JobInfo> jobs = new ArrayList<JobInfo>();
                for (Map.Entry<String, ?> entry : prefMap.entrySet()) {
                    int flags = (Integer) entry.getValue();
                    if ((flags & flag) == flag) {
                        int jobId = Integer.parseInt(entry.getKey());
                        jobs.add(readJob(jobId));
                        editor.remove(entry.getKey());
                    }
                }
                return jobs;
            } catch (XmlPullParserException e) {
                Log.e(TAG, e.getMessage(), e);
                return Collections.emptyList();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage(), e);
                return Collections.emptyList();
            } finally {
                editor.commit();
            }
        }
    }
}
