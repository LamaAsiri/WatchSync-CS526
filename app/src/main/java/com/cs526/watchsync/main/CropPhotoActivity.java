package com.cs526.watchsync.main;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.cs526.watchsync.BaseActivity;
import com.cs526.watchsync.R;
import com.cs526.watchsync.util.Avatars;
import com.cs526.watchsync.util.CropView;
import com.cs526.watchsync.util.Lang;
import com.cs526.watchsync.util.Ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Opened with the photo the user picked (getIntent().getData()).
 * The user moves / zooms it inside the circle, then "Use photo" saves a 512 x 512 square
 * to the cache folder and returns RESULT_OK. EditProfileActivity reads it with result().
 */
public class CropPhotoActivity extends BaseActivity {
    private static final String OUT_FILE = "cropped_photo.jpg";
    private static final int SLIDER_MAX = 100;

    private CropView crop;
    private SeekBar zoom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop_photo);
        setupToolbar(Lang.s(R.string.title_crop_photo));

        crop = findViewById(R.id.cropView);
        zoom = findViewById(R.id.seekZoom);
        View progress = findViewById(R.id.progress);
        View btnUse = findViewById(R.id.btnUsePhoto);
        btnUse.setEnabled(false);

        zoom.setMax(SLIDER_MAX);
        zoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int value, boolean fromUser) {
                if (fromUser) crop.setZoomFraction(value / (float) SLIDER_MAX);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        crop.setOnZoomChanged(f -> zoom.setProgress(Math.round(f * SLIDER_MAX)));
        findViewById(R.id.btnZoomOut).setOnClickListener(v -> step(-10));
        findViewById(R.id.btnZoomIn).setOnClickListener(v -> step(10));

        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());
        btnUse.setOnClickListener(v -> usePhoto());

        Uri uri = getIntent().getData();
        if (uri == null) { finish(); return; }

        // Glide turns the photo the right way up and makes it smaller (max 1600 px) so it fits in memory
        Glide.with(this).asBitmap().load(uri)
                .override(1600)
                .fitCenter()
                .disallowHardwareConfig()
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap b, @Nullable Transition<? super Bitmap> t) {
                        progress.setVisibility(View.GONE);
                        crop.setImage(b);
                        btnUse.setEnabled(true);
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable d) {
                        Ui.toast(CropPhotoActivity.this, Lang.s(R.string.err_open_photo));
                        finish();
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable d) {}
                });
    }

    private void step(int amount) {
        int value = Math.max(0, Math.min(SLIDER_MAX, zoom.getProgress() + amount));
        zoom.setProgress(value);
        crop.setZoomFraction(value / (float) SLIDER_MAX);
    }

    private void usePhoto() {
        Bitmap square = crop.crop(Avatars.SIZE);
        if (square == null) return;
        try (FileOutputStream out = new FileOutputStream(new File(getCacheDir(), OUT_FILE))) {
            square.compress(Bitmap.CompressFormat.JPEG, 92, out);
        } catch (IOException e) {
            Ui.toast(this, Lang.s(R.string.err_open_photo));
            return;
        }
        setResult(RESULT_OK);
        finish();
    }

    /** The photo the user just cropped, or null. */
    @Nullable
    public static Bitmap result(Context c) {
        File f = new File(c.getCacheDir(), OUT_FILE);
        return f.exists() ? BitmapFactory.decodeFile(f.getPath()) : null;
    }
}
