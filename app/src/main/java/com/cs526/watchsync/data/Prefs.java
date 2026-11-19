package com.cs526.watchsync.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/** SharedPreferences: small settings kept on this phone. */
public final class Prefs {
    private static final String FILE = "watchsync_prefs";

    private Prefs() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static void saveUser(Context c, String name, String color) {
        p(c).edit().putString("name", name).putString("color", color).apply();
    }

    public static String name(Context c) {
        return p(c).getString("name", "");
    }

    public static String color(Context c) {
        return p(c).getString("color", "#C1121F");
    }

    public static boolean reminders(Context c) {
        return p(c).getBoolean("reminders", true);
    }

    public static void setReminders(Context c, boolean on) {
        p(c).edit().putBoolean("reminders", on).apply();
    }

    public static boolean darkMode(Context c) {
        return p(c).getBoolean("dark_mode", false);
    }

    public static void setDarkMode(Context c, boolean on) {
        p(c).edit().putBoolean("dark_mode", on).apply();
    }

    /** Applies the saved light / dark choice to the whole app. */
    public static void applyTheme(Context c) {
        AppCompatDelegate.setDefaultNightMode(darkMode(c)
                ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    /** "" = follow the phone, "en" = English, "ar" = Arabic. */
    public static String language(Context c) {
        return p(c).getString("language", "");
    }

    public static void setLanguage(Context c, String tag) {
        p(c).edit().putString("language", tag).apply();
    }

    /** Log out: forget the account, but keep the dark mode and language choices (they belong to the phone). */
    public static void clear(Context c) {
        boolean dark = darkMode(c);
        String language = language(c);
        p(c).edit().clear().putBoolean("dark_mode", dark).putString("language", language).apply();
    }
}
