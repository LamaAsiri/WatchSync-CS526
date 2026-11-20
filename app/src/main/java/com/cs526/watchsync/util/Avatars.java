package com.cs526.watchsync.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.LruCache;

import androidx.annotation.Nullable;

import com.cs526.watchsync.data.Db;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Profile photos.
 *
 * The photo is cropped to a 512 x 512 square (bigger than the largest avatar circle on any phone),
 * saved as JPEG and kept in Firebase as text (Base64) at users/{uid}/photo.
 * We don't use Firebase Storage because it needs the paid Blaze plan.
 *
 * My own photo is also kept on this phone (files/my_photo.jpg) so Home and Profile show it right away.
 * Other people's photos are downloaded once and kept in memory while the app is open.
 */
public final class Avatars {
    public interface Callback {
        void done(@Nullable Bitmap photo);
    }

    public static final int SIZE = 512;
    private static final int JPEG_QUALITY = 85;
    private static final String MY_FILE = "my_photo.jpg";

    /** Photos in memory, by uid (up to 16 MB). */
    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(16 * 1024) {
        @Override
        protected int sizeOf(String uid, Bitmap b) {
            return Math.max(1, b.getByteCount() / 1024);
        }
    };
    /** People we already checked who have no photo. */
    private static final Set<String> noPhoto = new HashSet<>();
    /** Screens waiting for a photo that is still downloading. */
    private static final Map<String, List<Callback>> waiting = new HashMap<>();
    private static boolean mineChecked;

    private Avatars() {}

    // ---------- anyone ----------

    /** Someone's photo (members list, RSVPs, spoiler radar). Asks Firebase once, then remembers it. */
    public static void load(String uid, Callback cb) {
        if (uid == null || uid.isEmpty()) { cb.done(null); return; }
        Bitmap b = cache.get(uid);
        if (b != null || noPhoto.contains(uid)) { cb.done(b); return; }

        List<Callback> list = waiting.get(uid);
        if (list != null) { list.add(cb); return; }
        list = new ArrayList<>();
        list.add(cb);
        waiting.put(uid, list);

        Db.user(uid).child("photo").get().addOnCompleteListener(task -> {
            Bitmap photo = null;
            if (task.isSuccessful() && task.getResult() != null) {
                photo = decode(Snap.str(task.getResult()));
            }
            remember(uid, photo);
            List<Callback> callbacks = waiting.remove(uid);
            if (callbacks != null) for (Callback c : callbacks) c.done(photo);
        });
    }

    private static void remember(String uid, @Nullable Bitmap photo) {
        if (uid == null || uid.isEmpty()) return;
        if (photo == null) {
            cache.remove(uid);
            noPhoto.add(uid);
        } else {
            noPhoto.remove(uid);
            cache.put(uid, photo);
        }
    }

    // ---------- me ----------

    /** My photo from this phone, or null if I don't have one. */
    @Nullable
    public static Bitmap mine(Context c) {
        String uid = Db.uid();
        Bitmap b = uid.isEmpty() ? null : cache.get(uid);
        if (b != null) return b;
        File f = new File(c.getFilesDir(), MY_FILE);
        if (!f.exists()) return null;
        b = BitmapFactory.decodeFile(f.getPath());
        if (b != null && !uid.isEmpty()) remember(uid, b);
        return b;
    }

    /** Keeps my photo on this phone (Base64 text from Firebase). null or "" = no photo. */
    public static void saveMine(Context c, @Nullable String base64) {
        File f = new File(c.getApplicationContext().getFilesDir(), MY_FILE);
        String uid = Db.uid();
        if (base64 == null || base64.isEmpty()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            remember(uid, null);
            return;
        }
        byte[] bytes;
        try {
            bytes = Base64.decode(base64, Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            return;
        }
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(bytes);
        } catch (IOException ignored) {
            // the photo is still in Firebase, we'll try again next time
        }
        remember(uid, BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
    }

    /** Checks Firebase once per app run, in case I changed my photo on another phone. */
    public static void refreshMine(Context c, Callback cb) {
        if (mineChecked) return;
        String uid = Db.uid();
        if (uid.isEmpty()) return;
        mineChecked = true;
        Context app = c.getApplicationContext();
        Db.user(uid).child("photo").get().addOnSuccessListener(s -> {
            saveMine(app, Snap.str(s));
            cb.done(mine(app));
        });
    }

    /** Log out: forget my photo and everyone else's. */
    public static void clear(Context c) {
        //noinspection ResultOfMethodCallIgnored
        new File(c.getApplicationContext().getFilesDir(), MY_FILE).delete();
        cache.evictAll();
        noPhoto.clear();
        mineChecked = false;
    }

    // ---------- Base64 ----------

    public static String encode(Bitmap photo) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        photo.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    @Nullable
    public static Bitmap decode(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
