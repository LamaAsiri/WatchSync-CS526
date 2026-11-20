package com.cs526.watchsync.main;

import com.cs526.watchsync.util.Lang;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;

import com.cs526.watchsync.BaseActivity;
import com.cs526.watchsync.R;
import com.cs526.watchsync.data.Db;
import com.cs526.watchsync.data.Prefs;
import com.cs526.watchsync.util.Avatars;
import com.cs526.watchsync.util.ColorPicker;
import com.cs526.watchsync.util.Ui;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;

import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends BaseActivity {
    private ColorPicker picker;
    private MaterialButton btnPhoto;
    private View btnRemovePhoto;

    /** The photo shown now (null = colour only), and whether it changed since the screen opened. */
    private Bitmap photo;
    private boolean photoChanged;

    private ActivityResultLauncher<Intent> cropPhoto;
    private ActivityResultLauncher<PickVisualMediaRequest> pickPhoto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);
        setupToolbar(Lang.s(R.string.title_edit_profile));

        // 2) after cropping, show the new photo in the circle (saved only when "Save changes" is pressed)
        cropPhoto = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK) return;
            Bitmap cropped = CropPhotoActivity.result(this);
            if (cropped == null) return;
            setPhoto(cropped);
        });
        // 1) the phone's photo picker (implicit intent); the chosen photo goes to the crop screen
        pickPhoto = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), (Uri uri) -> {
            if (uri == null) return;
            Intent i = new Intent(this, CropPhotoActivity.class);
            i.setData(uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            cropPhoto.launch(i);
        });

        EditText etName = findViewById(R.id.etName);
        etName.setText(Prefs.name(this));
        ((TextView) findViewById(R.id.tvEmail)).setText(Lang.s(R.string.email_cant_change, Db.email()));

        picker = new ColorPicker(findViewById(R.id.colorPicker), Prefs.color(this),
                () -> etName.getText().toString());
        etName.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { picker.refresh(); }
            public void afterTextChanged(Editable s) {}
        });

        btnPhoto = findViewById(R.id.btnPhoto);
        btnRemovePhoto = findViewById(R.id.btnRemovePhoto);
        btnPhoto.setOnClickListener(v -> pickPhoto.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        btnRemovePhoto.setOnClickListener(v -> setPhoto(null));
        findViewById(R.id.tvAvatar).setOnClickListener(v -> btnPhoto.performClick());

        photo = Avatars.mine(this);
        showPhoto();

        View btnSave = findViewById(R.id.btnSave);
        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) { Ui.toast(this, Lang.s(R.string.err_enter_a_name)); return; }
            String color = picker.selected();
            String uid = Db.uid();
            // the photo as text (Base64); null removes it from Firebase
            String photoData = photo == null ? null : Avatars.encode(photo);
            btnSave.setEnabled(false);
            // Update my profile + my name/colour inside every group I'm in
            Db.me().child("groups").get().addOnSuccessListener(groups -> {
                Map<String, Object> updates = new HashMap<>();
                updates.put("users/" + uid + "/name", name);
                updates.put("users/" + uid + "/color", color);
                if (photoChanged) updates.put("users/" + uid + "/photo", photoData);
                for (DataSnapshot g : groups.getChildren()) {
                    updates.put("groups/" + g.getKey() + "/members/" + uid + "/name", name);
                    updates.put("groups/" + g.getKey() + "/members/" + uid + "/color", color);
                }
                Db.root().updateChildren(updates).addOnSuccessListener(x -> {
                    Prefs.saveUser(this, name, color);
                    if (photoChanged) Avatars.saveMine(this, photoData);
                    Ui.toast(this, Lang.s(R.string.saved));
                    finish();
                }).addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    Ui.toast(this, Lang.s(R.string.err_couldnt_save, e.getMessage()));
                });
            }).addOnFailureListener(e -> {
                btnSave.setEnabled(true);
                Ui.toast(this, Lang.s(R.string.err_couldnt_save, e.getMessage()));
            });
        });
    }

    private void setPhoto(Bitmap newPhoto) {
        photo = newPhoto;
        photoChanged = true;
        showPhoto();
    }

    private void showPhoto() {
        picker.setPhoto(photo);
        btnPhoto.setText(Lang.s(photo == null ? R.string.choose_photo : R.string.change_photo));
        btnRemovePhoto.setVisibility(photo == null ? View.GONE : View.VISIBLE);
    }
}
