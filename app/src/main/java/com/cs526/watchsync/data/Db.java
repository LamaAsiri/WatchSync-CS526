package com.cs526.watchsync.data;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.security.SecureRandom;

/**
 * Firebase Realtime Database layout:
 *
 * users/{uid}            name, email, color
 * users/{uid}/groups     {code: true}
 * users/{uid}/shows      {tmdbId: Show}       my library + my progress (like TV Time)
 *
 * groups/{code}          name, adminUid, createdAt
 *   members/{uid}        name, color, joinedAt
 *   watchlist/{tmdbId}   Show + addedByName
 *   nowWatching          Show   (one show at a time)
 *   upNext               Show   (vote winner waiting for the current show to finish)
 *   finished/{tmdbId}    Show
 *   vote                 open, options/{tmdbId}, ballots/{uid}, winnerTitle
 *   parties/{id}         Party
 */
public final class Db {
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private Db() {}

    public static DatabaseReference root() {
        return FirebaseDatabase.getInstance().getReference();
    }

    public static String uid() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        return u == null ? "" : u.getUid();
    }

    public static String email() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        return u == null || u.getEmail() == null ? "" : u.getEmail();
    }

    public static DatabaseReference user(String uid) {
        return root().child("users").child(uid);
    }

    public static DatabaseReference me() {
        return user(uid());
    }

    public static DatabaseReference group(String code) {
        return root().child("groups").child(code);
    }

    /** 6 characters, no 0/O/1/I so codes are easy to read out loud. */
    public static String newCode() {
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(CODE_CHARS.charAt(r.nextInt(CODE_CHARS.length())));
        return sb.toString();
    }
}
