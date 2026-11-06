package com.cs526.watchsync.main;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;

import com.cs526.watchsync.BaseActivity;
import com.cs526.watchsync.R;
import com.cs526.watchsync.data.Db;
import com.cs526.watchsync.data.Prefs;
import com.cs526.watchsync.util.ColorPicker;
import com.cs526.watchsync.util.Ui;
import com.google.firebase.database.DataSnapshot;

import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);
        setupToolbar("Edit profile");

        EditText etName = findViewById(R.id.etName);
        etName.setText(Prefs.name(this));
        ((TextView) findViewById(R.id.tvEmail)).setText("Email: " + Db.email() + " (can't be changed)");

        ColorPicker picker = new ColorPicker(findViewById(R.id.colorPicker), Prefs.color(this),
                () -> etName.getText().toString());
        etName.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { picker.refresh(); }
            public void afterTextChanged(Editable s) {}
        });

        findViewById(R.id.btnSave).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) { Ui.toast(this, "Enter a name"); return; }
            String color = picker.selected();
            String uid = Db.uid();
            // Update my profile + my name/colour inside every group I'm in
            Db.me().child("groups").get().addOnSuccessListener(groups -> {
                Map<String, Object> updates = new HashMap<>();
                updates.put("users/" + uid + "/name", name);
                updates.put("users/" + uid + "/color", color);
                for (DataSnapshot g : groups.getChildren()) {
                    updates.put("groups/" + g.getKey() + "/members/" + uid + "/name", name);
                    updates.put("groups/" + g.getKey() + "/members/" + uid + "/color", color);
                }
                Db.root().updateChildren(updates).addOnSuccessListener(x -> {
                    Prefs.saveUser(this, name, color);
                    Ui.toast(this, "Saved");
                    finish();
                }).addOnFailureListener(e -> Ui.toast(this, "Couldn't save: " + e.getMessage()));
            });
        });
    }
}
