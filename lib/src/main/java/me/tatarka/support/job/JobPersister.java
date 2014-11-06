package me.tatarka.support.job;

import android.content.ComponentName;
import android.content.Context;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.tatarka.support.internal.util.FastXmlSerializer;
import me.tatarka.support.internal.util.XmlUtils;
import me.tatarka.support.os.PersistableBundle;

/**
* Created by evan on 11/5/14.
*/
class JobPersister {
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

    JobPersister(Context context) {
        this.context = context.getApplicationContext();
    }

    void addPendingJob(JobInfo job) {
        try {
            writeJob(job);
        } catch (IOException e) {
            Log.e(JobServiceCompat.TAG, e.getMessage(), e);
        } catch (XmlPullParserException e) {
            Log.e(JobServiceCompat.TAG, e.getMessage(), e);
        }
    }

    void removePendingJob(int jobId) {
        if (!context.deleteFile(Integer.toString(jobId))) {
            Log.e(JobServiceCompat.TAG, "Unable to delete job file: " + jobId);
        }
    }

    List<JobInfo> getPendingJobs() {
        File jobDir = context.getFilesDir();
        String[] jobIdNames = jobDir.list();
        if (jobIdNames == null) {
            Log.e(JobServiceCompat.TAG, "Error reading dir: " + jobDir);
            return Collections.emptyList();
        }

        try {
            List<JobInfo> jobs = new ArrayList<JobInfo>();
            for (String jobIdName : jobIdNames) {
                int jobId = Integer.parseInt(jobIdName);
                jobs.add(readJob(jobId));
            }
            return jobs;
        } catch (XmlPullParserException e) {
            Log.e(JobServiceCompat.TAG, e.getMessage(), e);
            return Collections.emptyList();
        } catch (IOException e) {
            Log.e(JobServiceCompat.TAG, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private void writeJob(JobInfo job) throws IOException, XmlPullParserException {
        FileOutputStream os = null;
        try {
            os = context.openFileOutput(Integer.toString(job.getId()), Context.MODE_PRIVATE);
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

}
