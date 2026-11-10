package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Local change (not upstream): the end of a 30-minute Private DNS pause from the shield's menu. Puts strict mode
 * back, unless the pause was already ended or turned into "off" (no pause time left in DnsShield's prefs).
 */
public class DnsResume extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        long until = context.getSharedPreferences(DnsShield.PREFS, Context.MODE_PRIVATE)
                .getLong(DnsShield.PAUSED_UNTIL, 0);
        if (until <= 0 || !DnsShield.canSwitch(context)) return;
        try {
            DnsShield.turnOn(context);
        } catch (SecurityException ignored) {
        }
    }
}
