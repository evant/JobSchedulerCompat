package me.tatarka.support.os;

import android.os.BaseBundle;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.PersistableBundle;

/**
 * Created by evantatarka on 10/21/14.
 */
class PersistableBundleCompat {
    public static final BaseBundle EMPTY;
    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            EMPTY = PersistableBundle.EMPTY;
        } else {
            EMPTY = Bundle.EMPTY;
        }
    }

     static BaseBundle newInstance() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new PersistableBundle();
        } else {
            return new Bundle();
        }
    }

     static BaseBundle newInstance(int capacity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new PersistableBundle(capacity);
        } else {
            return new Bundle(capacity);
        }
    }

     static BaseBundle newInstance(BaseBundle extras) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new PersistableBundle((PersistableBundle) extras);
        } else {
            return new Bundle((Bundle) extras);
        }
    }

     static void write(Parcel parcel, BaseBundle bundle) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            parcel.writePersistableBundle((PersistableBundle) bundle);
        } else {
            parcel.writeBundle((Bundle) bundle);
        }
    }

     static BaseBundle read(Parcel parcel) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return parcel.readPersistableBundle();
        } else {
            return parcel.readBundle();
        }
    }

     static void putPersistableBundle(String key, BaseBundle value, BaseBundle bundle) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ((PersistableBundle) bundle).putPersistableBundle(key, (PersistableBundle) value);
        } else {
            ((Bundle) bundle).putBundle(key, (Bundle) value);
        }
    }

     static BaseBundle getPersistableBundle(String key, BaseBundle bundle) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return ((PersistableBundle) bundle).getPersistableBundle(key);
        } else {
            return ((Bundle) bundle).getBundle(key);
        }
    }

     static boolean instanceOf(BaseBundle bundle) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return bundle instanceof PersistableBundle;
        } else {
            return bundle instanceof Bundle;
        }
    }
}
