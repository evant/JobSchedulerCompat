package me.tatarka.support.os;

import android.os.Parcel;
import android.os.Parcelable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import me.tatarka.support.internal.util.XmlUtils;

/**
 * A mapping from String values to various types that can be saved to persistent and later
 * restored.
 */
public final class PersistableBundle implements Parcelable, Cloneable, XmlUtils.WriteMapCallback {
    private static final String TAG_PERSISTABLEMAP = "pbundle_as_map";
    public static final PersistableBundle EMPTY = new PersistableBundle(PersistableBundleCompat.EMPTY);

    private Object bundle;

    public PersistableBundle() {
        bundle = PersistableBundleCompat.newInstance();
    }

    public PersistableBundle(int capacity) {
        bundle = PersistableBundleCompat.newInstance(capacity);
    }

    public PersistableBundle(PersistableBundle extras) {
        bundle = PersistableBundleCompat.newInstance(extras.bundle);
    }

    /**
     * @hide
     */
    public PersistableBundle(Object extras) {
        bundle = extras;
    }

    /**
     * Constructs a PersistableBundle containing the mappings passed in.
     *
     * @param map a Map containing only those items that can be persisted.
     * @throws IllegalArgumentException if any element of #map cannot be persisted.
     */
    private PersistableBundle(Map<String, Object> map) {
        bundle = PersistableBundleCompat.newInstance();
        putAll(map);
    }

    /**
     * Returns the number of mappings contained in this Bundle.
     *
     * @return the number of mappings as an int.
     */
    public int size() {
        return PersistableBundleCompat.size(bundle);
    }

    /**
     * Returns true if the mapping of this Bundle is empty, false otherwise.
     */
    public boolean isEmpty() {
        return PersistableBundleCompat.isEmpty(bundle);
    }

    /**
     * Removes all elements from the mapping of this Bundle.
     */
    public void clear() {
        PersistableBundleCompat.clear(bundle);
    }

    /**
     * Returns true if the given key is contained in the mapping of this Bundle.
     *
     * @param key a String key
     * @return true if the key is part of the mapping, false otherwise
     */
    public boolean containsKey(String key) {
        return PersistableBundleCompat.containsKey(bundle, key);
    }

    /**
     * Returns the entry with the given key as an object.
     *
     * @param key a String key
     * @return an Object, or null
     */
    public Object get(String key) {
        return PersistableBundleCompat.get(bundle, key);
    }

    /**
     * Removes any entry with the given key from the mapping of this Bundle.
     *
     * @param key a String key
     */
    public void remove(String key) {
        PersistableBundleCompat.remove(bundle, key);
    }

    /**
     * Inserts all mappings from the given PersistableBundle into this BaseBundle.
     *
     * @param bundle a PersistableBundle
     */
    public void putAll(PersistableBundle bundle) {
        PersistableBundleCompat.putAll(this.bundle, bundle.bundle);
    }

    /**
     * Inserts all mappings from the given Map into this BaseBundle.
     *
     * @param map a Map
     */
    void putAll(Map map) {
        PersistableBundleCompat.putAll(bundle, map);
    }

    /**
     * Returns a Set containing the Strings used as keys in this Bundle.
     *
     * @return a Set of String keys
     */
    public Set<String> keySet() {
        return PersistableBundleCompat.keySet(bundle);
    }

    /**
     * Inserts an int value into the mapping of this Bundle, replacing any existing value for the
     * given key.
     *
     * @param key   a String, or null
     * @param value an int, or null
     */
    public void putInt(String key, int value) {
        PersistableBundleCompat.putInt(bundle, key, value);
    }

    /**
     * Inserts a long value into the mapping of this Bundle, replacing any existing value for the
     * given key.
     *
     * @param key   a String, or null
     * @param value a long
     */
    public void putLong(String key, long value) {
        PersistableBundleCompat.putLong(bundle, key, value);
    }

    /**
     * Inserts a double value into the mapping of this Bundle, replacing any existing value for the
     * given key.
     *
     * @param key   a String, or null
     * @param value a double
     */
    public void putDouble(String key, double value) {
        PersistableBundleCompat.putDouble(bundle, key, value);
    }

    /**
     * Inserts a String value into the mapping of this Bundle, replacing any existing value for the
     * given key.  Either key or value may be null.
     *
     * @param key   a String, or null
     * @param value a String, or null
     */
    public void putString(String key, String value) {
        PersistableBundleCompat.putString(bundle, key, value);
    }

    /**
     * Inserts an int array value into the mapping of this Bundle, replacing any existing value for
     * the given key.  Either key or value may be null.
     *
     * @param key   a String, or null
     * @param value an int array object, or null
     */
    public void putIntArray(String key, int[] value) {
        PersistableBundleCompat.putIntArray(bundle, key, value);
    }

    /**
     * Inserts a long array value into the mapping of this Bundle, replacing any existing value for
     * the given key.  Either key or value may be null.
     *
     * @param key   a String, or null
     * @param value a long array object, or null
     */
    public void putLongArray(String key, long[] value) {
        PersistableBundleCompat.putLongArray(bundle, key, value);
    }

    /**
     * Inserts a double array value into the mapping of this Bundle, replacing any existing value
     * for the given key.  Either key or value may be null.
     *
     * @param key   a String, or null
     * @param value a double array object, or null
     */
    public void putDoubleArray(String key, double[] value) {
        PersistableBundleCompat.putDoubleArray(bundle, key, value);
    }

    /**
     * Inserts a String array value into the mapping of this Bundle, replacing any existing value
     * for the given key.  Either key or value may be null.
     *
     * @param key   a String, or null
     * @param value a String array object, or null
     */
    public void putStringArray(String key, String[] value) {
        PersistableBundleCompat.putStringArray(bundle, key, value);
    }

    /**
     * Returns the value associated with the given key, or 0 if no mapping of the desired type
     * exists for the given key.
     *
     * @param key a String
     * @return an int value
     */
    public int getInt(String key) {
        return PersistableBundleCompat.getInt(bundle, key);
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
        return PersistableBundleCompat.getInt(bundle, key, defaultValue);
    }

    /**
     * Returns the value associated with the given key, or 0L if no mapping of the desired type
     * exists for the given key.
     *
     * @param key a String
     * @return a long value
     */
    public long getLong(String key) {
        return PersistableBundleCompat.getLong(bundle, key);
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
        return PersistableBundleCompat.getLong(bundle, key, defaultValue);
    }

    /**
     * Returns the value associated with the given key, or 0.0 if no mapping of the desired type
     * exists for the given key.
     *
     * @param key a String
     * @return a double value
     */
    public double getDouble(String key) {
        return PersistableBundleCompat.getDouble(bundle, key);
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
        return PersistableBundleCompat.getDouble(bundle, key, defaultValue);
    }

    /**
     * Returns the value associated with the given key, or null if no mapping of the desired type
     * exists for the given key or a null value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return a String value, or null
     */
    public String getString(String key) {
        return PersistableBundleCompat.getString(bundle, key);
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
        return PersistableBundleCompat.getString(bundle, key, defaultValue);
    }

    /**
     * Returns the value associated with the given key, or null if no mapping of the desired type
     * exists for the given key or a null value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return an int[] value, or null
     */
    public int[] getIntArray(String key) {
        return PersistableBundleCompat.getIntArray(bundle, key);
    }

    /**
     * Returns the value associated with the given key, or null if no mapping of the desired type
     * exists for the given key or a null value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return a long[] value, or null
     */
    public long[] getLongArray(String key) {
        return PersistableBundleCompat.getLongArray(bundle, key);
    }

    /**
     * Returns the value associated with the given key, or null if no mapping of the desired type
     * exists for the given key or a null value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return a double[] value, or null
     */
    public double[] getDoubleArray(String key) {
        return PersistableBundleCompat.getDoubleArray(bundle, key);
    }

    /**
     * Returns the value associated with the given key, or null if no mapping of the desired type
     * exists for the given key or a null value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return a String[] value, or null
     */
    public String[] getStringArray(String key) {
        return PersistableBundleCompat.getStringArray(bundle, key);
    }

    /**
     * Inserts a PersistableBundle value into the mapping of this Bundle, replacing any existing
     * value for the given key.  Either key or value may be null.
     *
     * @param key   a String, or null
     * @param value a Bundle object, or null
     */
    public void putPersistableBundle(String key, PersistableBundle value) {
        PersistableBundleCompat.putPersistableBundle(bundle, key, value.bundle);
    }

    /**
     * Returns the value associated with the given key, or null if no mapping of the desired type
     * exists for the given key or a null value is explicitly associated with the key.
     *
     * @param key a String, or null
     * @return a Bundle value, or null
     */
    public PersistableBundle getPersistableBundle(String key) {
        return new PersistableBundle(PersistableBundleCompat.getPersistableBundle(bundle, key));
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
     * Gets the underlying bundle. This is a {@link
     * android.os.PersistableBundle} on api 21+ and {@link android.os.Bundle} on lower apis.
     *
     * @return the underlying bundle
     */
    public Object getRealBundle() {
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
        for (String key : keySet()) {
            map.put(key, get(key));
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
