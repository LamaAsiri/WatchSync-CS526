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
import com.cs526.watchsync.util.Snap;
import com.cs526.watchsync.util.Ui;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class JoinGroupActivity extends BaseActivity {
    private String foundCode;
    private boolean alreadyMember;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_group);
        setupToolbar("");

        EditText etCode = findViewById(R.id.etCode);
        View card = findViewById(R.id.cardFound);
        View btnJoin = findViewById(R.id.btnJoin);
        TextView tvName = findViewById(R.id.tvFoundName);
        TextView tvInfo = findViewById(R.id.tvFoundInfo);

        findViewById(R.id.btnFind).setOnClickListener(v -> {
            String code = etCode.getText().toString().trim().toUpperCase(Locale.ROOT);
            if (code.length() != 6) { Ui.toast(this, "The code has 6 characters"); return; }
            Db.group(code).get().addOnSuccessListener(g -> {
                if (!g.exists()) {
                    card.setVisibility(View.GONE);
                    btnJoin.setVisibility(View.GONE);
                    Ui.toast(this, "No group found. Check the code with your friend.");
                    return;
                }
                foundCode = code;
                String adminName = Snap.str(g.child("members").child(Snap.str(g.child("adminUid"))).child("name"));
                String now = Snap.str(g.child("nowWatching/title"));
                tvName.setText(Snap.str(g.child("name")));
                tvInfo.setText("Admin: " + adminName + " · " + g.child("members").getChildrenCount() + " members"
                        + (now.isEmpty() ? "" : " · watching " + now));
                card.setVisibility(View.VISIBLE);
                alreadyMember = g.child("members").hasChild(Db.uid());
                ((TextView) btnJoin).setText(alreadyMember ? "Open group (you're already in)" : "Join group");
                btnJoin.setVisibility(View.VISIBLE);
            }).addOnFailureListener(e -> Ui.toast(this, "No connection: " + e.getMessage()));
        });

        btnJoin.setOnClickListener(v -> {
            if (foundCode == null) return;
            if (alreadyMember) {
                GroupActivity.open(this, foundCode);
                finish();
                return;
            }
            String uid = Db.uid();
            Map<String, Object> updates = new HashMap<>();
            updates.put("groups/" + foundCode + "/members/" + uid, Member.map(Prefs.name(this), Prefs.color(this)));
            updates.put("users/" + uid + "/groups/" + foundCode, true);
            Db.root().updateChildren(updates).addOnSuccessListener(x -> {
                GroupActivity.open(this, foundCode);
                finish();
            }).addOnFailureListener(e -> Ui.toast(this, "Couldn't join: " + e.getMessage()));
        });
    }
}
