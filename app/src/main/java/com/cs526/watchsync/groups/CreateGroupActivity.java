package com.cs526.watchsync.groups;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.cs526.watchsync.BaseActivity;
import com.cs526.watchsync.R;
import com.cs526.watchsync.data.Db;
import com.cs526.watchsync.data.Prefs;
import com.cs526.watchsync.model.Member;
import com.cs526.watchsync.util.Ui;

import java.util.HashMap;
import java.util.Map;

public class CreateGroupActivity extends BaseActivity {
    private EditText etName;
    private View btnCreate;
    private String groupName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);
        setupToolbar("");

        etName = findViewById(R.id.etGroupName);
        btnCreate = findViewById(R.id.btnCreate);
        btnCreate.setOnClickListener(v -> {
            groupName = etName.getText().toString().trim();
            if (groupName.isEmpty()) { Ui.toast(this, "Give your group a name"); return; }
            btnCreate.setEnabled(false);
            tryCode(0);
        });
    }

    /** Makes a random code and checks nobody else has it. */
    private void tryCode(int attempt) {
        String code = Db.newCode();
        Db.group(code).get().addOnSuccessListener(existing -> {
            if (existing.exists() && attempt < 5) { tryCode(attempt + 1); return; }
            String uid = Db.uid();
            Map<String, Object> updates = new HashMap<>();
            updates.put("groups/" + code + "/name", groupName);
            updates.put("groups/" + code + "/adminUid", uid);
            updates.put("groups/" + code + "/createdAt", System.currentTimeMillis());
            updates.put("groups/" + code + "/members/" + uid, Member.map(Prefs.name(this), Prefs.color(this)));
            updates.put("users/" + uid + "/groups/" + code, true);
            Db.root().updateChildren(updates)
                    .addOnSuccessListener(x -> showCode(code))
                    .addOnFailureListener(e -> { btnCreate.setEnabled(true); Ui.toast(this, "Couldn't create: " + e.getMessage()); });
        }).addOnFailureListener(e -> { btnCreate.setEnabled(true); Ui.toast(this, "No connection: " + e.getMessage()); });
    }

    private void showCode(String code) {
        btnCreate.setVisibility(View.GONE);
        etName.setEnabled(false);
        findViewById(R.id.layoutCode).setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.tvCode)).setText(code);
        findViewById(R.id.btnCopy).setOnClickListener(v -> Ui.copy(this, code));
        findViewById(R.id.btnShare).setOnClickListener(v -> Ui.shareCode(this, groupName, code));
        findViewById(R.id.btnOpenGroup).setOnClickListener(v -> {
            GroupActivity.open(this, code);
            finish();
        });
    }
}
