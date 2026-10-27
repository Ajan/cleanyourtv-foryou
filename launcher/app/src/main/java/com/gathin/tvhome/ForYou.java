package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Local change (not upstream): the For You row's data, read on the TV from what the dock apps publish to Android TV.
 *   Continue   Watch Next entries of type continue, next episode and new episode, newest first.
 *   Popular    each dock app's first non-empty preview channel ("Top Picks For You", "Recommended for you"...),
 *              taken in turn from each app in dock order, duplicates removed (Apple TV publishes one row many times).
 * Only dock_apps count. Needs READ_TV_LISTINGS (granted over ADB). Blocking: call it off the main thread.
 */
final class ForYou {
    static final int MAX_CONTINUE = 16, MAX_POPULAR = 24;

    static final class Item {
        String pkg, appLabel, title, meta, art, intentUri;
        float aspect = 16f / 9f;
        int progress = -1;           // percent, or -1 for none
        long when;
    }

    private ForYou() { }

    private static String s(Cursor c, int i) {
        String v = c.isNull(i) ? null : c.getString(i);
        return v == null ? "" : v;
    }

    /** TvContract's poster aspect constants to width/height; unknown is 16:9. */
    private static float aspect(int code) {
        switch (code) {
            case 1: return 3f / 2f;
            case 2: return 4f / 3f;
            case 3: return 1f;
            case 4: return 2f / 3f;
            case 5: return 1f / 1.441f;
            default: return 16f / 9f;
        }
    }

    private static String label(Context c, String pkg, Map<String, String> cache) {
        String l = cache.get(pkg);
        if (l != null) return l;
        try {
            PackageManager pm = c.getPackageManager();
            l = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            l = pkg;
        }
        cache.put(pkg, l);
        return l;
    }

    private static String episode(String season, String episode) {
        if (season.isEmpty() && episode.isEmpty()) return "";
        if (season.isEmpty()) return "E" + episode;
        return "S" + season + (episode.isEmpty() ? "" : " E" + episode);
    }

    static List<Item> loadContinue(Context c, List<String> dock) {
        List<Item> out = new ArrayList<>();
        Set<String> dockSet = new HashSet<>(dock);
        Map<String, String> labels = new HashMap<>();
        String[] cols = {
                TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME, TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE,
                TvContract.WatchNextPrograms.COLUMN_TITLE, TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
                TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
                TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS, TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
                TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI, TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
                TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_POSTER_ART_ASPECT_RATIO};
        Map<String, Item> byKey = new LinkedHashMap<>();
        try (Cursor cur = c.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI, cols, null, null, null)) {
            while (cur != null && cur.moveToNext()) {
                String pkg = s(cur, 0);
                if (!dockSet.contains(pkg)) continue;
                int type = cur.isNull(1) ? -1 : cur.getInt(1);
                if (type != TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE
                        && type != TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT
                        && type != TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_NEW) continue;
                Item it = new Item();
                it.pkg = pkg;
                it.appLabel = label(c, pkg, labels);
                it.title = s(cur, 2);
                if (it.title.isEmpty()) continue;
                String ep = episode(s(cur, 3), s(cur, 4));
                String kind = type == TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT ? "Next episode"
                        : type == TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_NEW ? "New episode" : "";
                StringBuilder meta = new StringBuilder();
                for (String part : new String[]{kind, ep, it.appLabel}) {
                    if (part.isEmpty()) continue;
                    if (meta.length() > 0) meta.append(" · ");
                    meta.append(part);
                }
                it.meta = meta.toString();
                long pos = cur.isNull(5) ? 0 : cur.getLong(5), dur = cur.isNull(6) ? 0 : cur.getLong(6);
                if (type == TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE && pos > 0 && dur > 0) {
                    it.progress = (int) Math.max(2, Math.min(100, pos * 100 / dur));
                }
                String poster = s(cur, 7), thumb = s(cur, 8);
                it.art = !thumb.isEmpty() ? thumb : poster;
                it.aspect = !thumb.isEmpty() ? 16f / 9f : aspect(cur.isNull(11) ? 0 : cur.getInt(11));
                it.intentUri = s(cur, 9);
                it.when = cur.isNull(10) ? 0 : cur.getLong(10);
                // One card per title and app: the newest entry wins.
                String key = pkg + "\n" + it.title.toLowerCase();
                Item old = byKey.get(key);
                if (old == null || it.when > old.when) byKey.put(key, it);
            }
        } catch (RuntimeException e) {
            return out;   // permission not granted yet, or the provider is missing: no Continue
        }
        out.addAll(byKey.values());
        out.sort((a, b) -> Long.compare(b.when, a.when));
        return out.size() > MAX_CONTINUE ? new ArrayList<>(out.subList(0, MAX_CONTINUE)) : out;
    }

    static List<Item> loadPopular(Context c, List<String> dock) {
        ContentResolver cr = c.getContentResolver();
        Map<String, String> labels = new HashMap<>();
        Map<String, Long> firstChannel = new HashMap<>();
        String[] ch = {TvContract.Channels._ID, TvContract.Channels.COLUMN_PACKAGE_NAME, TvContract.Channels.COLUMN_TYPE,
                TvContract.Channels.COLUMN_DISPLAY_NAME};
        Map<String, List<Long>> channels = new HashMap<>();
        try (Cursor cur = cr.query(TvContract.Channels.CONTENT_URI, ch, null, null, TvContract.Channels._ID + " ASC")) {
            while (cur != null && cur.moveToNext()) {
                if (!TvContract.Channels.TYPE_PREVIEW.equals(s(cur, 2))) continue;
                String pkg = s(cur, 1);
                if (!dock.contains(pkg)) continue;
                if (s(cur, 3).toLowerCase().contains("music")) continue;   // YouTube Music is not a watch pick
                channels.computeIfAbsent(pkg, k -> new ArrayList<>()).add(cur.getLong(0));
            }
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
        Map<String, List<Item>> perApp = new LinkedHashMap<>();
        String[] pp = {TvContract.PreviewPrograms.COLUMN_TITLE, TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION,
                TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI, TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI,
                TvContract.PreviewPrograms.COLUMN_INTENT_URI, TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
                TvContract.PreviewPrograms.COLUMN_SEASON_DISPLAY_NUMBER, TvContract.PreviewPrograms.COLUMN_EPISODE_DISPLAY_NUMBER};
        for (String pkg : dock) {
            List<Long> ids = channels.get(pkg);
            if (ids == null) continue;
            for (long id : ids) {
                List<Item> items = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                Uri programs = TvContract.buildPreviewProgramsUriForChannel(id);
                try (Cursor cur = cr.query(programs, pp, null, null, null)) {
                    while (cur != null && cur.moveToNext() && items.size() < MAX_POPULAR) {
                        String title = s(cur, 0);
                        if (title.isEmpty() || !seen.add(title.toLowerCase())) continue;
                        Item it = new Item();
                        it.pkg = pkg;
                        it.appLabel = label(c, pkg, labels);
                        it.title = title;
                        String ep = episode(s(cur, 6), s(cur, 7));
                        it.meta = ep.isEmpty() ? it.appLabel : ep + " · " + it.appLabel;
                        String poster = s(cur, 2), thumb = s(cur, 3);
                        it.art = !poster.isEmpty() ? poster : thumb;
                        it.aspect = !poster.isEmpty() ? aspect(cur.isNull(5) ? 0 : cur.getInt(5)) : 16f / 9f;
                        it.intentUri = s(cur, 4);
                        items.add(it);
                    }
                } catch (RuntimeException e) {
                    continue;
                }
                if (!items.isEmpty()) {   // the app's first row with something in it; the rest are repeats or niche
                    perApp.put(pkg, items);
                    break;
                }
            }
        }
        // Take from each app in turn, in dock order, so no single app fills the row.
        List<Item> out = new ArrayList<>();
        for (int round = 0; out.size() < MAX_POPULAR; round++) {
            boolean any = false;
            for (List<Item> items : perApp.values()) {
                if (round < items.size() && out.size() < MAX_POPULAR) {
                    out.add(items.get(round));
                    any = true;
                }
            }
            if (!any) break;
        }
        return out;
    }
}
