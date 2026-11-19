package com.cs526.watchsync.util;

import android.content.Context;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.cs526.watchsync.data.Prefs;

import java.util.Locale;

/**
 * App language (English / Arabic).
 * All texts live in res/values/strings.xml (English) and res/values-ar/strings.xml (Arabic).
 * The choice is saved in SharedPreferences, like dark mode: "" = follow the phone, "en", or "ar".
 */
public final class Lang {
    private static Context app;

    private Lang() {}

    /** Called once from WatchSyncApp. */
    public static void init(Context c) {
        app = c.getApplicationContext();
    }

    /** Applies the saved language to the whole app (screens are redrawn in the new language). */
    public static void apply(Context c) {
        String tag = Prefs.language(c);
        AppCompatDelegate.setApplicationLocales(tag.isEmpty()
                ? LocaleListCompat.getEmptyLocaleList() : LocaleListCompat.forLanguageTags(tag));
    }

    /** The language the app is showing now. */
    public static Locale locale() {
        String tag = app == null ? "" : Prefs.language(app);
        return tag.isEmpty() ? Locale.getDefault() : Locale.forLanguageTag(tag);
    }

    public static boolean arabic() {
        return "ar".equals(locale().getLanguage());
    }

    /** A context whose resources use the chosen language (works in models, receivers and helpers too). */
    private static Context ctx() {
        String tag = Prefs.language(app);
        if (tag.isEmpty()) return app;
        Configuration config = new Configuration(app.getResources().getConfiguration());
        config.setLocale(Locale.forLanguageTag(tag));
        return app.createConfigurationContext(config);
    }

    /** Text from strings.xml in the chosen language, e.g. Lang.s(R.string.n_shows, 3). */
    public static String s(int id, Object... args) {
        Context c = ctx();
        return args.length == 0 ? c.getString(id) : c.getString(id, args);
    }

    /** Singular / plural text from strings.xml, e.g. "1 episode" / "2 episodes". */
    public static String plural(int id, int n) {
        return ctx().getResources().getQuantityString(id, n, n);
    }
}
