package com.cs526.watchsync.util;

import com.google.firebase.database.DataSnapshot;

/** Small helpers to read values from Firebase safely (no crash on missing fields). */
public final class Snap {
    private Snap() {}

    public static String str(DataSnapshot s) {
        Object v = s.getValue();
        return v == null ? "" : String.valueOf(v);
    }

    public static int num(DataSnapshot s) {
        Object v = s.getValue();
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; }
    }

    public static long lng(DataSnapshot s) {
        Object v = s.getValue();
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return 0L; }
    }

    public static boolean bool(DataSnapshot s) {
        Object v = s.getValue();
        return v instanceof Boolean && (Boolean) v;
    }
}
