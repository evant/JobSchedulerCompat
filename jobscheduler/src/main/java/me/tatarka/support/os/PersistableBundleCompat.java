package me.tatarka.support.os;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.PersistableBundle;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

/**
 * Created by evantatarka on 10/21/14.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class PersistableBundleCompat {
    public static final Object EMPTY;

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            EMPTY = PersistableBundle.EMPTY;
        } else {
            EMPTY = Bundle.EMPTY;
        }
    }

    static Object newInstance() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new PersistableBundle();
        } else {
            return new Bundle();
        }
    }

    static Object newInstance(int capacity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new PersistableBundle(capacity);
        } else {
            return new Bundle(capacity);
        }
    }

    static Object newInstance(Object extras) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new PersistableBundle((PersistableBundle) extras);
        } else {
            return new Bundle((Bundle) extras);
        }
    }

    static void write(Parcel parcel, Object bundle) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            parcel.writePersistableBundle((PersistableBundle) bundle);
        } else {
            parcel.writeBundle((Bundle) bundle);
        }
    }

    static Object read(Parcel parcel) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return parcel.readPersistableBundle();
        } else {
            return parcel.readBundle();
        }
    }

    static void putPersistableBundle(Object bundle, String key, Object value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ((PersistableBundle) bundle).putPersistableBundle(key, (PersistableBundle) value);
        } else {
            ((Bundle) bundle).putBundle(key, (Bundle) value);
        }
    }

    static Object getPersistableBundle(Object bundle, String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return ((PersistableBundle) bundle).getPersistableBundle(key);
        } else {
            return ((Bundle) bundle).getBundle(key);
        }
    }

    static boolean instanceOf(Object bundle) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return bundle instanceof PersistableBundle;
        } else {
            return bundle instanceof Bundle;
        }
    }

    static int size(Object bundle) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return ((PersistableBundle) bundle).size();
        } else {
            return ((Bundle) bundle).size();
        }
    }

    static boolean isEmpty(Object bundle) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return ((PersistableBundle) bundle).isEmpty();
        } else {
            return ((Bundle) bundle).isEmpty();
        }
    }

    static void clear(Object bundle) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ((PersistableBundle) bundle).clear();
        } else {
            ((Bundle) bundle).clear();
        }
    }

    static boolean containsKey(Object bundle, String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return ((PersistableBundle) bundle).containsKey(key);
        } else {
            return ((Bundle) bundle).containsKey(key);
        }
    }

    static Object get(Object bundle, String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return ((PersistableBundle) bundle).get(key);
        } else {
            return ((Bundle) bundle).get(key);
        }
    }

    static void remove(Object bundle, String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ((PersistableBundle) bundle).remove(key);
        } else {
            ((Bundle) bundle).remove(key);
        }
    }

    static void putAll(Object bundle, Object allBundle) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ((PersistableBundle) bundle).putAll((PersistableBundle) allBundle);
        } else {
            ((Bundle) bundle).putAll((Bundle) allBundle);
        }
    }

    static void putAll(Object bundle, Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Integer) {
                putInt(bundle, key, (Integer) value);
            } else if (value instanceof Long) {
                putLong(bundle, key, (Long) value);
            } else if (value instanceof Double) {
                putDouble(bundle, key, (Double) value);
            } else if (value instanceof String) {
                putString(bundle, key, (String) value);
            } else if (value instanceof int[]) {
                putIntArray(bundle, key, (int[]) value);
            } else if (value instanceof long[]) {
                putLongArray(bundle, key, (long[]) value);
            } else if (value instanceof double[]) {
                putDoubleArray(bundle, key, (double[]) value);
            } else if (value instanceof String[]) {
                putStringArray(bundle, key, (String[]) value);
            } else if (value instanceof Map) {
                // Fix up any Maps by replacing them with PersistableBundles.
                Object persitableBundle = newInstance();
                putAll(persitableBundle, (Map<String, Object>) value);
                putPersistableBundle(bundle, key, persitableBundle);
            } else {
                throw new IllegalArgumentException("Bad value in PersistableBundle key=" + key +
                        " value=" + value);
            }
        }
    }

    static Set<String> keySet(Object bundle) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return ((PersistableBundle) bundle).keySet();
        } else {
            return ((Bundle) bundle).keySet();
        }
    }

    static void putInt(Object bundle, String key, int value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ((PersistableBundle) bundle).putInt(key, value);
        } else {
            ((Bundle) bundle).putInt(key, value);
        }
    }

    static void putLong(Object bundle, String key, long value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ((PersistableBundle) bundle).putLong(key, value);
        } else {
            ((Bundle) bundle).putLong(key, value);
        }
    }

    static void putDouble(Object bundle, String key, double value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ((PersistableBundle) bundle).putDouble(key, value);
        } else {
            ((Bundle) bundle).putDouble(key, value);
        }
    }

    static void putString(Object bundle, String key, String value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ((PersistableBundle) bundle).putString(key, value);
        } else {
            ((Bundle) bundle).putString(key, value);
        }
    }

    static void putIntArray(Object bundle, String key, int[] value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ((PersistableBundle) bundle).putIntArray(key, value);
        } else {
            ((Bundle) bundle).putIntArray(key, value);
        }
    }

    static void putLongArray(Object bundle, String key, long[] value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ((PersistableBundle) bundle).putLongArray(key, value);
        } else {
            ((Bundle) bundle).putLongArray(key, value);
        }
    }

    static void putDoubleArray(Object bundle, String key, double[] value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ((PersistableBundle) bundle).putDoubleArray(key, value);
        } else {
            ((Bundle) bundle).putDoubleArray(key, value);
        }
    }

    static void putStringArray(Object bundle, String key, String[] value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ((PersistableBundle) bundle).putStringArray(key, value);
        } else {
            ((Bundle) bundle).putStringArray(key, value);
        }
    }

    static int getInt(Object bundle, String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return ((PersistableBundle) bundle).getInt(key);
        } else {
            return ((Bundle) bundle).getInt(key);
        }
    }

    static int getInt(Object bundle, String key, int defaultValue) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return ((PersistableBundle) bundle).getInt(key, defaultValue);
        } else {
            return ((Bundle) bundle).getInt(key, defaultValue);
        }
    }

    static long getLong(Object bundle, String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return ((PersistableBundle) bundle).getLong(key);
        } else {
            return ((Bundle) bundle).getLong(key);
        }
    }

    static long getLong(Object bundle, String key, long defaultValue) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return ((PersistableBundle) bundle).getLong(key, defaultValue);
        } else {
            return ((Bundle) bundle).getLong(key, defaultValue);
        }
    }

    static double getDouble(Object bundle, String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return ((PersistableBundle) bundle).getDouble(key);
        } else {
            return ((Bundle) bundle).getDouble(key);
        }
    }

    static double getDouble(Object bundle, String key, double defaultValue) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return ((PersistableBundle) bundle).getDouble(key, defaultValue);
        } else {
            return ((Bundle) bundle).getDouble(key, defaultValue);
        }
    }

    static String getString(Object bundle, String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return ((PersistableBundle) bundle).getString(key);
        } else {
            return ((Bundle) bundle).getString(key);
        }
    }

    static String getString(Object bundle, String key, String defaultValue) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return ((PersistableBundle) bundle).getString(key, defaultValue);
        } else {
            String str = ((Bundle) bundle).getString(key);
            return str == null ? defaultValue : str;
        }
    }

    static int[] getIntArray(Object bundle, String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return ((PersistableBundle) bundle).getIntArray(key);
        } else {
            return ((Bundle) bundle).getIntArray(key);
        }
    }

    static long[] getLongArray(Object bundle, String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return ((PersistableBundle) bundle).getLongArray(key);
        } else {
            return ((Bundle) bundle).getLongArray(key);
        }
    }

    static double[] getDoubleArray(Object bundle, String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return ((PersistableBundle) bundle).getDoubleArray(key);
        } else {
            return ((Bundle) bundle).getDoubleArray(key);
        }
    }

    static String[] getStringArray(Object bundle, String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return ((PersistableBundle) bundle).getStringArray(key);
        } else {
            return ((Bundle) bundle).getStringArray(key);
        }
    }
}
