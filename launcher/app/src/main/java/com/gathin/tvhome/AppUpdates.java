package com.gathin.tvhome;

import android.app.Notification;
import android.app.PendingIntent;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Local change (not upstream): the Play Store's "updates available" notifications, for the bell in the top bar.
 * With the Google TV launcher disabled nothing else shows them. UpdateWatchService passes the active notifications
 * here; only the Play Store's update channels are read, every other notification is ignored. Idea from Talkies and
 * Matinee (both MIT); written for this launcher.
 */
final class AppUpdates {
    static final class Item {
        String title = "", text = "";
        PendingIntent open;
    }

    private static final String PLAY_STORE = "com.android.vending";
    /** The Play Store's channels for waiting updates. */
    private static final List<String> CHANNELS = Arrays.asList("updates-available", "required");
    /** Lets `adb shell cmd notification post -t <title> tvhome-update-test <text>` try the bell. */
    static final String TEST_TAG = "tvhome-update-test";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static List<Item> items = Collections.emptyList();
    private static Runnable listener;

    private AppUpdates() { }

    static synchronized List<Item> list() {
        return items;
    }

    /** Called on the main thread whenever the list changes. */
    static void setListener(Runnable r) {
        listener = r;
    }

    /** Drops r if it is still the listener (a recreated Home may already have set its own). */
    static void clearListener(Runnable r) {
        if (listener == r) listener = null;
    }

    static void update(StatusBarNotification[] active) {
        List<Item> out = new ArrayList<>();
        if (active != null) {
            for (StatusBarNotification sbn : active) {
                Notification n = sbn.getNotification();
                boolean store = PLAY_STORE.equals(sbn.getPackageName()) && CHANNELS.contains(n.getChannelId());
                boolean test = "com.android.shell".equals(sbn.getPackageName()) && TEST_TAG.equals(sbn.getTag());
                if (!store && !test) continue;
                if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0 && active.length > 1) continue;
                Item it = new Item();
                CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
                CharSequence text = n.extras.getCharSequence(Notification.EXTRA_TEXT);
                it.title = title == null ? "" : title.toString().trim();
                it.text = text == null ? "" : text.toString().trim();
                it.open = n.contentIntent;
                out.add(it);
            }
        }
        synchronized (AppUpdates.class) {
            items = out;
        }
        MAIN.post(() -> {
            if (listener != null) listener.run();
        });
    }
}
