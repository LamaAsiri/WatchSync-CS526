package com.cs526.watchsync.util;

import com.cs526.watchsync.R;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.bumptech.glide.Glide;
import com.cs526.watchsync.data.TmdbApi;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class Ui {
    public static final String[] AVATAR_COLORS = {"#C1121F", "#780000", "#003049", "#669BBC"};

    public static final int INK = Color.parseColor("#003049");
    public static final int PAPAYA = Color.parseColor("#FDF0D5");
    public static final int RED = Color.parseColor("#C1121F");
    public static final int LAVA = Color.parseColor("#780000");
    public static final int RED_TINT = Color.parseColor("#F8D7D3");
    public static final int STEEL_TINT = Color.parseColor("#DCE8F0");
    public static final int LINE = Color.parseColor("#EED9B0");
    public static final int MUTED = Color.parseColor("#4F5F6E");

    private Ui() {}

    /** Round avatar with the first letter of the name in the person's colour. */
    public static void avatar(TextView v, String name, String color) {
        avatar(v, name, color, null);
    }

    /** Round avatar: the person's photo if they have one, otherwise the first letter in their colour. */
    public static void avatar(TextView v, String name, String color, @Nullable Bitmap photo) {
        if (photo != null) {
            RoundedBitmapDrawable round = RoundedBitmapDrawableFactory.create(v.getResources(), photo);
            round.setCircular(true);
            round.setAntiAlias(true);
            v.setBackgroundTintList(null);
            v.setBackground(round);
            v.setText("");
            return;
        }
        v.setBackgroundResource(R.drawable.bg_circle);
        String letter = (name == null || name.trim().isEmpty()) ? "?" : name.trim().substring(0, 1).toUpperCase(Locale.ROOT);
        v.setText(letter);
        int bg = parse(color, RED);
        v.setBackgroundTintList(ColorStateList.valueOf(bg));
        v.setTextColor("#669BBC".equalsIgnoreCase(color) ? INK : Color.WHITE);
    }

    public static void badge(TextView v, String text, int bg, int fg) {
        v.setText(text);
        v.setBackgroundTintList(ColorStateList.valueOf(bg));
        v.setTextColor(fg);
        v.setVisibility(android.view.View.VISIBLE);
    }

    public static int parse(String color, int fallback) {
        try { return Color.parseColor(color); } catch (Exception e) { return fallback; }
    }

    public static void poster(ImageView v, String posterPath) {
        String url = TmdbApi.posterUrl(posterPath);
        if (url == null) {
            Glide.with(v).clear(v);
            v.setImageDrawable(null);
            return;
        }
        Glide.with(v).load(url).centerCrop().into(v);
    }

    public static String when(long ms) {
        return new SimpleDateFormat("EEE d MMM · h:mm a", Lang.locale()).format(new Date(ms));
    }

    public static String time(long ms) {
        return new SimpleDateFormat("h:mm a", Lang.locale()).format(new Date(ms));
    }

    public static String format(String pattern, long ms) {
        return new SimpleDateFormat(pattern, Lang.locale()).format(new Date(ms));
    }

    public static String countdown(long partyTime) {
        long diff = partyTime - System.currentTimeMillis();
        if (diff <= 0) return Lang.s(R.string.started);
        long mins = diff / 60000;
        long days = mins / (60 * 24);
        long hours = (mins / 60) % 24;
        long m = mins % 60;
        if (days > 0) return Lang.s(R.string.starts_in_dh, days, hours);
        if (hours > 0) return Lang.s(R.string.starts_in_hm, hours, m);
        return Lang.s(R.string.starts_in_m, m);
    }

    public static void toast(Context c, String msg) {
        Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
    }

    public static String firstName(String name) {
        if (name == null || name.trim().isEmpty()) return Lang.s(R.string.there);
        return name.trim().split(" ")[0];
    }

    public static void copy(Context c, String text) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("code", text));
        toast(c, Lang.s(R.string.copied_x, text));
    }

    public static void shareCode(Context c, String groupName, String code) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, Lang.s(R.string.share_code_text, groupName, code));
        c.startActivity(Intent.createChooser(i, Lang.s(R.string.share_group_code)));
    }
}
