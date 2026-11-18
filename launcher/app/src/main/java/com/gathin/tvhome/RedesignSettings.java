package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * Local change (not upstream): the redesign's own Launcher settings - the name after the greeting, whether the
 * greeting shows, and whether the Popular tab shows. Kept in the launcher's preferences (Config.SETTINGS_NAME) under
 * rd_ keys, without bumping the settings revision: Home picks them up in onResume and animates the change instead of
 * rebuilding. Nothing is hard-coded: with no name saved, Home falls back to a theme's greeting_name (empty by
 * default).
 */
final class RedesignSettings {
    static final String NAME_KEY = "rd_greeting_name";
    static final String SHOW_GREETING_KEY = "rd_show_greeting";
    static final String SHOW_POPULAR_KEY = "rd_show_popular";
    static final int NAME_MAX = 20;

    private RedesignSettings() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }

    /** The saved name (trimmed, at most NAME_MAX), "" for "no name", or null when none was ever saved. */
    @Nullable
    static String name(Context c) {
        String test = TestSwitches.string(c, "greeting_name");
        if (test != null) return clean(test);
        SharedPreferences p = prefs(c);
        return p.contains(NAME_KEY) ? clean(p.getString(NAME_KEY, "")) : null;
    }

    static boolean showGreeting(Context c) {
        Boolean test = TestSwitches.bool(c, "show_greeting");
        return test != null ? test : prefs(c).getBoolean(SHOW_GREETING_KEY, true);
    }

    static boolean showPopular(Context c) {
        Boolean test = TestSwitches.bool(c, "show_popular");
        return test != null ? test : prefs(c).getBoolean(SHOW_POPULAR_KEY, true);
    }

    static String clean(@Nullable String s) {
        if (s == null) return "";
        s = s.trim().replaceAll("\\s+", " ");
        return s.length() > NAME_MAX ? s.substring(0, NAME_MAX).trim() : s;
    }

    // ---- the rows in Launcher settings ------------------------------------------------------------------------

    /** Adds the three rows after {@code after}, in the same column; they look like every other settings row. */
    static void addRows(Activity a, View after) {
        ViewGroup column = (ViewGroup) after.getParent();
        View rows = LayoutInflater.from(a).inflate(R.layout.rd_settings_rows, column, false);
        column.addView(rows, column.indexOfChild(after) + 1);
        TextView name = rows.findViewById(R.id.rdNameChoice);
        TextView greeting = rows.findViewById(R.id.rdGreetingChoice);
        TextView popular = rows.findViewById(R.id.rdPopularChoice);
        Runnable refresh = () -> {
            String n = name(a);
            name.setText(a.getString(R.string.rd_name_state,
                    n == null || n.isEmpty() ? a.getString(R.string.rd_name_none) : n));
            greeting.setText(a.getString(R.string.rd_show_greeting_state,
                    a.getString(showGreeting(a) ? R.string.on : R.string.off)));
            popular.setText(a.getString(R.string.rd_show_popular_state,
                    a.getString(showPopular(a) ? R.string.on : R.string.off)));
        };
        name.setOnClickListener(v -> editName(a, refresh));
        greeting.setOnClickListener(v -> {
            prefs(a).edit().putBoolean(SHOW_GREETING_KEY, !showGreeting(a)).apply();
            refresh.run();
        });
        popular.setOnClickListener(v -> {
            prefs(a).edit().putBoolean(SHOW_POPULAR_KEY, !showPopular(a)).apply();
            refresh.run();
        });
        refresh.run();
    }

    /** A text field with the on-screen keyboard; Save (or the keyboard's Done) keeps it, trimmed. */
    private static void editName(Activity a, Runnable refresh) {
        EditText field = new EditText(a);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
                | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(NAME_MAX)});
        String now = name(a);
        field.setText(now == null ? "" : now);
        field.setSelection(field.getText().length());
        FrameLayout box = new FrameLayout(a);
        int pad = Math.round(24 * a.getResources().getDisplayMetrics().density);
        box.setPadding(pad, 0, pad, 0);
        box.addView(field);
        Runnable save = () -> {
            prefs(a).edit().putString(NAME_KEY, clean(field.getText().toString())).apply();
            refresh.run();
        };
        AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle(R.string.rd_name_title)
                .setMessage(R.string.rd_name_message)
                .setView(box)
                .setPositiveButton(R.string.rd_name_save, (d, w) -> save.run())
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        field.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            save.run();
            dialog.dismiss();
            return true;
        });
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        dialog.show();
        field.requestFocus();
    }
}
