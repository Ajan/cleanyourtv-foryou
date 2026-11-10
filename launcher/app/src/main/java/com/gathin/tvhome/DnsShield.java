package com.gathin.tvhome;

import android.content.BroadcastReceiver;
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
 * Status only: it takes no focus.
 */
final class DnsShield {
    static final String PROBE_HOST = "www.youtube.com";
    static final long RETRY_MS = 4_000;
    static final long RED_AFTER_MS = 120_000;
    static final long FRESH_MS = 60_000;
    static final long SLOW_MS = 1_500;

    enum State { HIDDEN, CHECKING, OK, FAILING }

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
        view.setFocusable(false);
        view.setVisibility(View.GONE);
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
            // Off, automatic or no network: nothing strict to watch.
            failingSince = 0;
            show(State.HIDDEN);
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
        int color = next == State.OK ? GREEN : next == State.CHECKING ? AMBER : RED;
        view.setCompoundDrawableTintList(ColorStateList.valueOf(color));
        int text = next == State.OK ? R.string.dns_shield_ok
                : next == State.CHECKING ? R.string.dns_shield_checking : R.string.dns_shield_failing;
        view.setContentDescription(context.getString(text));
        view.setVisibility(View.VISIBLE);
    }
}
