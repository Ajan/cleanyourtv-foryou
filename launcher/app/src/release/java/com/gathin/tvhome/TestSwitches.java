package com.gathin.tvhome;

import android.content.Context;

import androidx.annotation.Nullable;

/** Local change (not upstream): the release side of the debug-only test switches (src/debug): none are read. */
final class TestSwitches {
    private TestSwitches() {
    }

    static boolean emptyContinue(Context c) {
        return false;
    }

    @Nullable
    static String string(Context c, String name) {
        return null;
    }

    @Nullable
    static Boolean bool(Context c, String name) {
        return null;
    }
}
