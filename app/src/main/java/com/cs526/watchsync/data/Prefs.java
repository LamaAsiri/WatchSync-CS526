package com.cs526.watchsync.data;

import android.content.Context;
import android.content.SharedPreferences;

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

    public static void clear(Context c) {
        p(c).edit().clear().apply();
    }
}
