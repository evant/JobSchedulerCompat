package me.tatarka.support.server.job.controllers;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Created by evantatarka on 11/8/14.
 */
public class ControllerPrefs {
    private static final String PREFS_NAME = "me.tatarka.support.job.controllers.PREFS";
    private static final String KEY_BATTERY_LOW = "battery_low";
    private static final String KEY_NEXT_JOB_EXPIRED_ELAPSED_MILLIS = "next_job_expired_elapsed_millis";
    private static final String KEY_NEXT_DELAY_EXPIRED_ELAPSED_MILLIS = "next_delay_expired_elapsed_millis";
    private static final String KEY_IDLE = "idle";

    private static ControllerPrefs sInstance;

    public static ControllerPrefs getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ControllerPrefs(context);
        }
        return sInstance;
    }

    private final SharedPreferences prefs;

    private ControllerPrefs(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    boolean isBatteryLow() {
        return prefs.getBoolean(KEY_BATTERY_LOW, false);
    }

    boolean isIdle() {
        return prefs.getBoolean(KEY_IDLE, false);
    }

    long getNextJobExpiredElapsedMillis() {
        return prefs.getLong(KEY_NEXT_JOB_EXPIRED_ELAPSED_MILLIS, 0);
    }

    long getNextDelayExpiredElapsedMillis() {
        return prefs.getLong(KEY_NEXT_DELAY_EXPIRED_ELAPSED_MILLIS, 0);
    }

    Editor edit() {
        return new Editor(prefs.edit());
    }

    public void clear() {
        prefs.edit().clear().apply();
    }

    static class Editor {
        SharedPreferences.Editor editor;

        private Editor(SharedPreferences.Editor editor) {
            this.editor = editor;
        }

        Editor setBatteryLow(boolean value) {
            editor.putBoolean(KEY_BATTERY_LOW, value);
            return this;
        }

        Editor setNextJobExpiredElapsedMillis(long value) {
            editor.putLong(KEY_NEXT_JOB_EXPIRED_ELAPSED_MILLIS, value);
            return this;
        }

        Editor setNextDelayExipredElapsedMillis(long value) {
            editor.putLong(KEY_NEXT_DELAY_EXPIRED_ELAPSED_MILLIS, value);
            return this;
        }

        Editor setIdle(boolean value) {
            editor.putBoolean(KEY_IDLE, value);
            return this;
        }

        boolean commit() {
            return editor.commit();
        }

        void apply() {
            editor.apply();
        }
    }
}
