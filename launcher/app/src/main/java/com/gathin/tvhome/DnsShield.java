package com.gathin.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.net.ConnectivityManager;
import android.net.DnsResolver;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Local change (not upstream): a shield in the top bar while Private DNS is in strict mode (a resolver name such as
 * dns.adguard-dns.com set as private_dns_specifier). Strict mode never falls back to the router, so after the TV
 * wakes from standby every name lookup hangs until the secure connection to the resolver is up again: about a
 * minute on a Sony BRAVIA VU31, and an app opened in that minute (YouTube) shows "Can't connect" and stays stuck.
 *
 * The shield asks one real question - does a fresh lookup answer? - with the resolver's cache skipped, so an
 * answer means the resolver itself replied. Green: lookups work. Amber: still waiting (just woken). Red: no answer
 * for {@link #RED_AFTER_MS}. Hidden when Private DNS is off or automatic, or below Android 10 (no DnsResolver).
 * Status only, unless the launcher holds WRITE_SECURE_SETTINGS: then focus can land on it and OK opens a menu to
 * pause Private DNS for 30 minutes (mode opportunistic; DnsResume puts hostname back), turn it off, or turn it on.
 * While it is paused or off with a resolver name still saved, the shield stays, grey, so it can be turned back on.
 */
final class DnsShield {
    static final String PROBE_HOST = "www.youtube.com";
    static final long RETRY_MS = 4_000;
    static final long RED_AFTER_MS = 120_000;
    static final long FRESH_MS = 60_000;
    static final long SLOW_MS = 1_500;

    enum State { HIDDEN, CHECKING, OK, FAILING, OFF }

    static final String MODE = "private_dns_mode", SPECIFIER = "private_dns_specifier";
    static final String PREFS = "dns_shield", PAUSED_UNTIL = "paused_until";
    static final long PAUSE_MS = 30 * 60_000L;
    private static final int GREY = 0xFF8E8E93;

    private static final int GREEN = 0xFF34C759;
    private static final int AMBER = 0xFFFFB020;
    private static final int RED = 0xFFFF453A;

    private final Context context;
    private final TextView view;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Executor main = handler::post;
    private final ConnectivityManager cm;

    private State state = State.HIDDEN;
    private boolean resumed;
    private boolean inFlight;
    private long failingSince;      // elapsedRealtime of the first unanswered check in a row; 0 while answered
    private long lastOk;
    private int generation;         // bumps on every network or screen change; older answers are ignored
    @Nullable private CancellationSignal cancel;

    private final Runnable retry = this::check;

    private final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onLinkPropertiesChanged(@NonNull Network network, @NonNull LinkProperties lp) {
            handler.post(() -> restart(false));
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(() -> restart(true));
        }
    };

    /** Standby on a TV is screen off: the lookups hang right after the screen comes back. */
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            restart(true);
        }
    };

    DnsShield(Context context, TextView view) {
        this.context = context;
        this.view = view;
        this.cm = context.getSystemService(ConnectivityManager.class);
        applySwitchable();
        view.setVisibility(View.GONE);
    }

    /** Selectable (OK opens the menu) only while the permission is held; re-checked each time Home comes back,
     *  since the install grants it after Home has already started. */
    private void applySwitchable() {
        boolean can = canSwitch(context);
        view.setFocusable(can);
        view.setOnClickListener(can ? v -> { if (menuOpener != null) menuOpener.run(); } : null);
        if (!can && view.hasFocus()) view.clearFocus();
    }

    /** Whether this launcher may switch Private DNS (WRITE_SECURE_SETTINGS, granted over ADB). */
    static boolean canSwitch(Context c) {
        return c.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** The saved resolver name, or null: what "on" returns to. */
    @Nullable
    private String specifier() {
        String s = Settings.Global.getString(context.getContentResolver(), SPECIFIER);
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    private static long pausedUntil(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(PAUSED_UNTIL, 0);
    }

    /** Strict mode back on with the saved name, and any pause and its alarm forgotten. */
    static void turnOn(Context c) {
        Settings.Global.putString(c.getContentResolver(), MODE, "hostname");
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(PAUSED_UNTIL).apply();
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am != null) am.cancel(resumeIntent(c));
    }

    private static PendingIntent resumeIntent(Context c) {
        return PendingIntent.getBroadcast(c, 0, new Intent(c, DnsResume.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void turnOff(long forMs) {
        Settings.Global.putString(context.getContentResolver(), MODE, "opportunistic");
        SharedPreferences.Editor e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        AlarmManager am = context.getSystemService(AlarmManager.class);
        if (forMs > 0) {
            long until = System.currentTimeMillis() + forMs;
            e.putLong(PAUSED_UNTIL, until);
            if (am != null) am.set(AlarmManager.RTC_WAKEUP, until, resumeIntent(context));
        } else {
            e.remove(PAUSED_UNTIL);
            if (am != null) am.cancel(resumeIntent(context));
        }
        e.apply();
    }

    /** Home draws the menu (the Inputs menu's glass dropdown); set by MainActivity. */
    @Nullable Runnable menuOpener;

    /** The menu's header: the saved resolver name, e.g. "Private DNS: dns.adguard-dns.com"; null: no menu. */
    @Nullable
    String menuTitle() {
        String name = specifier();
        if (name == null) return null;
        boolean strict = "hostname".equals(Settings.Global.getString(context.getContentResolver(), MODE));
        long until = pausedUntil(context);
        if (!strict && until > System.currentTimeMillis()) {
            // In the TV's own 12- or 24-hour style: "Paused until 11:51 PM".
            String at = android.text.format.DateFormat.getTimeFormat(context).format(new java.util.Date(until));
            return context.getString(R.string.dns_paused_until, at);
        }
        return context.getString(strict ? R.string.dns_menu_title : R.string.dns_menu_title_off, name);
    }

    /** The menu's rows, as string ids, for the current state: on, paused, or off. */
    int[] menuItems() {
        boolean strict = "hostname".equals(Settings.Global.getString(context.getContentResolver(), MODE));
        boolean paused = !strict && pausedUntil(context) > System.currentTimeMillis();
        return strict ? new int[]{R.string.dns_pause, R.string.dns_turn_off}
                : paused ? new int[]{R.string.dns_turn_on, R.string.dns_turn_off}
                : new int[]{R.string.dns_turn_on};
    }

    /** Runs the row the user picked (one of menuItems()). */
    void choose(int item) {
        try {
            if (item == R.string.dns_pause) turnOff(PAUSE_MS);
            else if (item == R.string.dns_turn_off) turnOff(0);
            else turnOn(context);
        } catch (SecurityException e) {
            return;   // the permission was taken away since: leave everything as it is
        }
        restart(true);
    }

    static boolean supported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    void start() {
        if (!supported() || cm == null) return;
        cm.registerDefaultNetworkCallback(callback);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(screenReceiver, filter);
        }
    }

    void stop() {
        if (!supported() || cm == null) return;
        try {
            cm.unregisterNetworkCallback(callback);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            context.unregisterReceiver(screenReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        handler.removeCallbacksAndMessages(null);
        cancelQuery();
    }

    /** Home is in front: check unless a recent answer still stands. */
    void resume() {
        resumed = true;
        applySwitchable();
        if (state == State.OK && SystemClock.elapsedRealtime() - lastOk < FRESH_MS) return;
        check();
    }

    /** Home is behind an app: no retries; the next resume checks again. */
    void pause() {
        resumed = false;
        handler.removeCallbacks(retry);
    }

    /** The network or the screen changed: start over, and with forget, drop the last answer. */
    private void restart(boolean forget) {
        generation++;
        cancelQuery();
        handler.removeCallbacks(retry);
        if (forget) {
            lastOk = 0;
            failingSince = 0;
            if (state == State.OK) show(State.CHECKING);
        }
        if (resumed) check();
    }

    private void check() {
        handler.removeCallbacks(retry);
        if (!supported() || cm == null || inFlight) return;
        Network network = cm.getActiveNetwork();
        LinkProperties lp = network == null ? null : cm.getLinkProperties(network);
        if (lp == null || lp.getPrivateDnsServerName() == null) {
            // Off, automatic or no network: nothing strict to watch. A pause whose alarm was missed ends here.
            failingSince = 0;
            long until = pausedUntil(context);
            if (until > 0 && until <= System.currentTimeMillis() && canSwitch(context) && specifier() != null) {
                turnOn(context);
                return;   // the network callback brings the next check
            }
            show(lp != null && canSwitch(context) && specifier() != null ? State.OFF : State.HIDDEN);
            return;
        }
        if (state == State.HIDDEN) show(State.CHECKING);
        inFlight = true;
        int gen = generation;
        long started = SystemClock.elapsedRealtime();
        cancel = new CancellationSignal();
        // Not answered quickly: say "waiting" without sitting out the resolver's own timeout.
        handler.postDelayed(() -> {
            if (inFlight && gen == generation && state != State.FAILING) show(State.CHECKING);
        }, SLOW_MS);
        DnsResolver.getInstance().query(network, PROBE_HOST, DnsResolver.FLAG_NO_CACHE_LOOKUP, main, cancel,
                new DnsResolver.Callback<List<InetAddress>>() {
                    @Override
                    public void onAnswer(@NonNull List<InetAddress> answer, int rcode) {
                        finish(gen, started, true);
                    }

                    @Override
                    public void onError(@NonNull DnsResolver.DnsException error) {
                        finish(gen, started, false);
                    }
                });
    }

    private void finish(int gen, long started, boolean answered) {
        if (gen != generation) return;   // a restart cancelled this one and already started the next
        inFlight = false;
        cancel = null;
        long now = SystemClock.elapsedRealtime();
        if (answered) {
            lastOk = now;
            failingSince = 0;
            show(State.OK);
            return;
        }
        if (failingSince == 0) failingSince = started;
        show(now - failingSince >= RED_AFTER_MS ? State.FAILING : State.CHECKING);
        if (resumed) handler.postDelayed(retry, RETRY_MS);
    }

    private void cancelQuery() {
        if (cancel != null) cancel.cancel();
        cancel = null;
        inFlight = false;
    }

    private void show(State next) {
        state = next;
        if (next == State.HIDDEN) {
            view.setVisibility(View.GONE);
            return;
        }
        int color = next == State.OK ? GREEN : next == State.CHECKING ? AMBER : next == State.OFF ? GREY : RED;
        view.setCompoundDrawableTintList(ColorStateList.valueOf(color));
        int text = next == State.OK ? R.string.dns_shield_ok
                : next == State.CHECKING ? R.string.dns_shield_checking
                : next == State.OFF ? R.string.dns_shield_off : R.string.dns_shield_failing;
        view.setContentDescription(context.getString(text));
        view.setVisibility(View.VISIBLE);
    }
}
