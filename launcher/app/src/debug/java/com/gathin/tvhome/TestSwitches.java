package com.gathin.tvhome;

import android.content.Context;
import android.provider.Settings;

import androidx.annotation.Nullable;

/**
 * Local change (not upstream), DEBUG BUILDS ONLY (src/debug): test switches for checking Home on a real TV without
 * touching the apps' own lists or the settings UI. Set from a computer, for example
 *   adb shell settings put global cyt_test_empty_continue 1
 *   adb shell settings put global cyt_test_show_popular 0
 *   adb shell settings put global cyt_test_greeting_name Alex
 * and delete them afterwards (settings delete global ...). A release build compiles src/release's TestSwitches,
 * which answers "not set" to everything and names none of these settings.
 */
final class TestSwitches {
    private static final String PREFIX = "cyt_test_";

    private TestSwitches() {
    }

    /** Continue as if nothing were in progress. */
    static boolean emptyContinue(Context c) {
        return Boolean.TRUE.equals(bool(c, "empty_continue"));
    }

    @Nullable
    static String string(Context c, String name) {
        try {
            return Settings.Global.getString(c.getContentResolver(), PREFIX + name);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    static Boolean bool(Context c, String name) {
        String v = string(c, name);
        if (v == null) return null;
        return "1".equals(v) || "true".equalsIgnoreCase(v);
    }
}
