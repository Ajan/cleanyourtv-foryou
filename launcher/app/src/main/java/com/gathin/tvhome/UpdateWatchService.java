package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Local change (not upstream): notification access for the update bell. Android binds it once notification access
 * is allowed (variant-install.sh: cmd notification allow_listener). It reads only the Play Store's update
 * notifications (AppUpdates) and keeps nothing.
 */
public class UpdateWatchService extends NotificationListenerService {
    @Override
    public void onListenerConnected() {
        refresh();
    }

    @Override
    public void onListenerDisconnected() {
        AppUpdates.update(null);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        refresh();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        refresh();
    }

    private void refresh() {
        try {
            AppUpdates.update(getActiveNotifications());
        } catch (RuntimeException e) {
            AppUpdates.update(null);   // not connected (yet)
        }
    }
}
