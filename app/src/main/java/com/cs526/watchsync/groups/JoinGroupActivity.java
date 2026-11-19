package com.cs526.watchsync.groups;

import com.cs526.watchsync.util.Lang;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.cs526.watchsync.BaseActivity;
import com.cs526.watchsync.R;
import com.cs526.watchsync.data.Db;
import com.cs526.watchsync.data.LocalDb;
import com.cs526.watchsync.data.Prefs;
import com.cs526.watchsync.model.Member;
import com.cs526.watchsync.util.Snap;
import com.cs526.watchsync.util.Ui;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class JoinGroupActivity extends BaseActivity {
    private String foundCode;
    private String foundName = "";
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
            if (code.length() != 6) { Ui.toast(this, Lang.s(R.string.err_code_6)); return; }
            Db.group(code).get().addOnSuccessListener(g -> {
                if (!g.exists()) {
                    card.setVisibility(View.GONE);
                    btnJoin.setVisibility(View.GONE);
                    Ui.toast(this, Lang.s(R.string.err_no_group));
                    return;
                }
                foundCode = code;
                String adminName = Snap.str(g.child("members").child(Snap.str(g.child("adminUid"))).child("name"));
                String now = Snap.str(g.child("nowWatching/title"));
                foundName = Snap.str(g.child("name"));
                tvName.setText(foundName);
                tvInfo.setText(Lang.s(R.string.join_found_sub, adminName, g.child("members").getChildrenCount())
                        + (now.isEmpty() ? "" : Lang.s(R.string.join_found_watching, now)));
                card.setVisibility(View.VISIBLE);
                alreadyMember = g.child("members").hasChild(Db.uid());
                ((TextView) btnJoin).setText(alreadyMember ? Lang.s(R.string.open_group_already_in) : Lang.s(R.string.join_group));
                btnJoin.setVisibility(View.VISIBLE);
            }).addOnFailureListener(e -> Ui.toast(this, Lang.s(R.string.err_no_connection, e.getMessage())));
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
                LocalDb.get(this).logActivity("group", Lang.s(R.string.log_joined_group, foundName));
                GroupActivity.open(this, foundCode);
                finish();
            }).addOnFailureListener(e -> Ui.toast(this, Lang.s(R.string.err_couldnt_join, e.getMessage())));
        });
    }
}
