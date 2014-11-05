package me.tatarka.support.os;

import android.os.BaseBundle;
import android.os.Parcel;
import android.os.Parcelable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import me.tatarka.support.internal.util.XmlUtils;

/**
 * Created by evan on 10/28/14.
 */
public final class PersistableBundle implements Parcelable, Cloneable, XmlUtils.WriteMapCallback {
    private static final String TAG_PERSISTABLEMAP = "pbundle_as_map";
    public static final PersistableBundle EMPTY = new PersistableBundle(PersistableBundleCompat.EMPTY);

    private BaseBundle bundle;

    public PersistableBundle() {
        bundle = PersistableBundleCompat.newInstance();
    }

    public PersistableBundle(int capacity) {
        bundle = PersistableBundleCompat.newInstance(capacity);
    }

    public PersistableBundle(PersistableBundle extras) {
        bundle = PersistableBundleCompat.newInstance(extras.bundle);
    }

    /** @hide */
    public PersistableBundle(BaseBundle extras) {
        bundle = extras;
    }

    /**
     * Constructs a PersistableBundle containing the mappings passed in.
     *
     * @param map a Map containing only those items that can be persisted.
     * @throws IllegalArgumentException if any element of #map cannot be persisted.
     */
    private PersistableBundle(Map<String, Object> map) {
        // First stuff everything in.
        putAll(map);

        // Now verify each item throwing an exception if there is a violation.
        Set<String> keys = map.keySet();
        Iterator<String> iterator = keys.iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            Object value = map.get(key);
            if (value instanceof Map) {
                // Fix up any Maps by replacing them with PersistableBundles.
                putPersistableBundle(key, new PersistableBundle((Map<String, Object>) value));
            } else if (!(value instanceof Integer) && !(value instanceof Long) &&
                    !(value instanceof Double) && !(value instanceof String) &&
                    !(value instanceof int[]) && !(value instanceof long[]) &&
                    !(value instanceof double[]) && !(value instanceof String[]) &&
                    !(value instanceof PersistableBundle) && (value != null)) {
                throw new IllegalArgumentException("Bad value in PersistableBundle key=" + key +
                        " value=" + value);
            }
        }
    }

    /**
     * Returns the number of mappings contained in this Bundle.
     *
     * @return the number of mappings as an int.
     */
    public int size() {
        return bundle.size();
    }

    /**
     * Returns true if the mapping of this Bundle is empty, false otherwise.
     */
    public boolean isEmpty() {
        return bundle.isEmpty();
    }

    /**
     * Removes all elements from the mapping of this Bundle.
     */
    public void clear() {
        bundle.clear();
    }

    /**
     * Returns true if the given key is contained in the mapping of this Bundle.
     *
     * @param key a String key
     * @return true if the key is part of the mapping, false otherwise
     */
    public boolean containsKey(String key) {
        return bundle.containsKey(key);
    }

    /**
     * Returns the entry with the given key as an object.
     *
     * @param key a String key
     * @return an Object, or null
     */
    public Object get(String key) {
        return bundle.get(key);
    }

    /**
     * Removes any entry with the given key from the mapping of this Bundle.
     *
     * @param key a String key
     */
    public void remove(String key) {
        bundle.remove(key);
    }

    /**
     * Inserts all mappings from the given PersistableBundle into this BaseBundle.
     *
     * @param bundle a PersistableBundle
     */
    public void putAll(PersistableBundle bundle) {
        bundle.putAll(bundle);
    }

    /**
     * Inserts all mappings from the given Map into this BaseBundle.
     *
     * @param map a Map
     */
    void putAll(Map map) {
        // This is a protected method on BaseBundle, cheat with reflection.
        Method method;
        try {
            method = BaseBundle.class.getDeclaredMethod("putAll", Map.class);
            method.setAccessible(true);
            method.invoke(map);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a Set containing the Strings used as keys in this Bundle.
     *
     * @return a Set of String keys
     */
    public Set<String> keySet() {
        return bundle.keySet();
    }

    /**
     * Inserts an int value into the mapping of this Bundle, replacing any existing value for the
     * given key.
     *
     * @param key   a String, or null
     * @param value an int, or null
     */
    public void putInt(String key, int value) {
        bundle.putInt(key, value);
    }

    /**
     * Inserts a long value into the mapping of this Bundle, replacing any existing value for the
     * given key.
     *
     * @param key   a String, or null
     * @param value a long
     */
    public void putLong(String key, long value) {
        bundle.putLong(key, value);
    }

    /**
     * Inserts a double value into the mapping of this Bundle, replacing any existing value for the
     * given key.
     *
     * @param key   a String, or null
     * @param value a double
     */
    public void putDouble(String key, double value) {
        bundle.putDouble(key, value);
    }

    /**
     * Inserts a String value into the mapping of this Bundle, replacing any existing value for the
     * given key.  Either key or value may be null.
     *
     * @param key   a String, or null
     * @param value a String, or null
     */
    public void putString(String key, String value) {
        bundle.putString(key, value);
    }

    /**
     * Inserts an int array value into the mapping of this Bundle, replacing any existing value for
     * the given key.  Either key or value may be null.
     *
     * @param key   a String, or null
     * @param value an int array object, or null
     */
    public void putIntArray(String key, int[] value) {
        bundle.putIntArray(key, value);
    }

    /**
     * Inserts a long array value into the mapping of this Bundle, replacing any existing value for
     * the given key.  Either key or value may be null.
     *
     * @param key   a String, or null
     * @param value a long array object, or null
     */
    public void putLongArray(String key, long[] value) {
        bundle.putLongArray(key, value);
    }

    /**
     * Inserts a double array value into the mapping of this Bundle, replacing any existing value
     * for the given key.  Either key or value may be null.
     *
     * @param key   a String, or null
     * @param value a double array object, or null
     */
    public void putDoubleArray(String key, double[] value) {
        bundle.putDoubleArray(key, value);
    }

    /**
     * Inserts a String array value into the mapping of this Bundle, replacing any existing value
     * for the given key.  Either key or value may be null.
     *
     * @param key   a String, or null
     * @param value a String array object, or null
     */
    public void putStringArray(String key, String[] value) {
        bundle.putStringArray(key, value);
    }

    /**
     * Returns the value associated with the given key, or 0 if no mapping of the desired type
     * exists for the given key.
     *
     * @param key a String
     * @return an int value
     */
    public int getInt(String key) {
        return bundle.getInt(key);
    }

    /**
     * Returns the value associated with the given key, or defaultValue if no mapping of the desired
     * type exists for the given key.
     *
     * @param key          a String
     * @param defaultValue Value to return if key does not exist
     * @return an int value
     */
    public int getInt(String key, int defaultValue) {
        return bundle.getInt(key, defaultValue);
    }

    /**
     * Returns the value associated with the given key, or 0L if no mapping of the desired type
     * exists for the given key.
     *
     * @param key a String
     * @return a long value
     */
    public long getLong(String key) {
        return bundle.getLong(key);
    }

    /**
     * Returns the value associated with the given key, or defaultValue if no mapping of the desired
     * type exists for the given key.
     *
     * @param key          a String
     * @param defaultValue Value to return if key does not exist
     * @return a long value
     */
    public long getLong(String key, long defaultValue) {
        return bundle.getLong(key, defaultValue);
    }

    /**
     * Returns the value associated with the given key, or 0.0 if no mapping of the desired type
     * exists for the given key.
     *
     * @param key a String
     * @return a double value
     */
    public double getDouble(String key) {
        return bundle.getDouble(key);
    }

    /**
     * Returns the value associated with the given key, or defaultValue if no mapping of the desired
     * type exists for the given key.
     *
     * @param key          a String
     * @param defaultValue Value to return if key does not exist
     * @return a double value
     */
    public double getDouble(String key, double defaultValue) {
        return bundle.getDouble(key, defaultValue);
    }

    /**
     * Returns the value associated with the given key, or null if no mapping of the desired type
     * exists for the given key or a null value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return a String value, or null
     */
    public String getString(String key) {
        return bundle.getString(key);
    }

    /**
     * Returns the value associated with the given key, or defaultValue if no mapping of the desired
     * type exists for the given key or if a null value is explicitly associated with the given
     * key.
     *
     * @param key          a String, or null
     * @param defaultValue Value to return if key does not exist or if a null value is associated
     *                     with the given key.
     * @return the String value associated with the given key, or defaultValue if no valid String
     * object is currently mapped to that key.
     */
    public String getString(String key, String defaultValue) {
        return bundle.getString(key, defaultValue);
    }

    /**
     * Returns the value associated with the given key, or null if no mapping of the desired type
     * exists for the given key or a null value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return an int[] value, or null
     */
    public int[] getIntArray(String key) {
        return bundle.getIntArray(key);
    }

    /**
     * Returns the value associated with the given key, or null if no mapping of the desired type
     * exists for the given key or a null value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return a long[] value, or null
     */
    public long[] getLongArray(String key) {
        return bundle.getLongArray(key);
    }

    /**
     * Returns the value associated with the given key, or null if no mapping of the desired type
     * exists for the given key or a null value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return a double[] value, or null
     */
    public double[] getDoubleArray(String key) {
        return bundle.getDoubleArray(key);
    }

    /**
     * Returns the value associated with the given key, or null if no mapping of the desired type
     * exists for the given key or a null value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return a String[] value, or null
     */
    public String[] getStringArray(String key) {
        return bundle.getStringArray(key);
    }

    /**
     * Inserts a PersistableBundle value into the mapping of this Bundle, replacing any existing
     * value for the given key.  Either key or value may be null.
     *
     * @param key   a String, or null
     * @param value a Bundle object, or null
     */
    public void putPersistableBundle(String key, PersistableBundle value) {
        PersistableBundleCompat.putPersistableBundle(key, value.bundle, bundle);
    }

    /**
     * Returns the value associated with the given key, or null if no mapping of the desired type
     * exists for the given key or a null value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return a Bundle value, or null
     */
    public PersistableBundle getPersistableBundle(String key) {
        return new PersistableBundle(PersistableBundleCompat.getPersistableBundle(key, bundle));
    }

    /**
     * Clones the current PersistableBundle. The internal map is cloned, but the keys and values to
     * which it refers are copied by reference.
     */
    @Override
    public Object clone() {
        return new PersistableBundle(this);
    }

    /**
     * Gets the underlying {@link android.os.BaseBundle}. This is a {@link
     * android.os.PersistableBundle} on api 21+ and {@link android.os.Bundle} on lower apis.
     *
     * @return the underlying bundle
     */
    public BaseBundle getRealBundle() {
        return bundle;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        PersistableBundleCompat.write(dest, bundle);
    }

    private PersistableBundle(Parcel in) {
        this.bundle = PersistableBundleCompat.read(in);
    }

    public static final Creator<PersistableBundle> CREATOR = new Creator<PersistableBundle>() {
        public PersistableBundle createFromParcel(Parcel source) {
            return new PersistableBundle(source);
        }

        public PersistableBundle[] newArray(int size) {
            return new PersistableBundle[size];
        }
    };

    @Override
    public String toString() {
        return bundle.toString();
    }

    public static void writePersitableBundle(PersistableBundle bundle, Parcel parcel) {
        PersistableBundleCompat.write(parcel, bundle.bundle);
    }

    public static PersistableBundle readPersistableBundle(Parcel parcel) {
        return new PersistableBundle(PersistableBundleCompat.read(parcel));
    }

    /**
     * @hide
     */
    public static class MyReadMapCallback implements XmlUtils.ReadMapCallback {
        @Override
        public Object readThisUnknownObjectXml(XmlPullParser in, String tag)
                throws XmlPullParserException, IOException {
            if (TAG_PERSISTABLEMAP.equals(tag)) {
                return restoreFromXml(in);
            }
            throw new XmlPullParserException("Unknown tag=" + tag);
        }
    }

    /**
     * @hide
     */
    @Override
    public void writeUnknownObject(Object v, String name, XmlSerializer out) throws XmlPullParserException, IOException {
        if (v instanceof PersistableBundle) {
            out.startTag(null, TAG_PERSISTABLEMAP);
            out.attribute(null, "name", name);
            ((PersistableBundle) v).saveToXml(out);
            out.endTag(null, TAG_PERSISTABLEMAP);
        } else {
            throw new XmlPullParserException("Unknown Object o=" + v);
        }
    }

    /**
     * @hide
     */
    public void saveToXml(XmlSerializer out) throws IOException, XmlPullParserException {
        Map map = new HashMap();
        for (String key : bundle.keySet()) {
            map.put(key, bundle.get(key));
        }
        XmlUtils.writeMapXml(map, out, this);
    }

    /**
     * @hide
     */
    public static PersistableBundle restoreFromXml(XmlPullParser in) throws IOException,
            XmlPullParserException {
        final int outerDepth = in.getDepth();
        final String startTag = in.getName();
        final String[] tagName = new String[1];
        int event;
        while (((event = in.next()) != XmlPullParser.END_DOCUMENT) &&
                (event != XmlPullParser.END_TAG || in.getDepth() < outerDepth)) {
            if (event == XmlPullParser.START_TAG) {
                return new PersistableBundle((Map<String, Object>)
                        XmlUtils.readThisMapXml(in, startTag, tagName, new MyReadMapCallback()));
            }
        }
        return EMPTY;
    }
}
