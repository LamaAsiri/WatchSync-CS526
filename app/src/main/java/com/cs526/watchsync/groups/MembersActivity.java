package com.cs526.watchsync.groups;

import com.cs526.watchsync.util.Lang;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cs526.watchsync.BaseActivity;
import com.cs526.watchsync.R;
import com.cs526.watchsync.data.Db;
import com.cs526.watchsync.main.MainActivity;
import com.cs526.watchsync.model.Member;
import com.cs526.watchsync.util.RowAdapter;
import com.cs526.watchsync.util.Snap;
import com.cs526.watchsync.util.Ui;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MembersActivity extends BaseActivity {
    private String code;
    private final RowAdapter adapter = new RowAdapter();

    public static void open(Context c, String code) {
        Intent i = new Intent(c, MembersActivity.class);
        i.putExtra(GroupActivity.EXTRA_CODE, code);
        c.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_members);
        setupToolbar(Lang.s(R.string.title_members));
        code = getIntent().getStringExtra(GroupActivity.EXTRA_CODE);
        ((TextView) findViewById(R.id.tvInviteCode)).setText(code);

        RecyclerView rv = findViewById(R.id.rvMembers);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        findViewById(R.id.btnLeave).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(Lang.s(R.string.leave_group_q))
                .setMessage(Lang.s(R.string.leave_group_msg))
                .setPositiveButton(Lang.s(R.string.leave), (d, w) -> leave())
                .setNegativeButton(Lang.s(R.string.cancel), null)
                .show());
        load();
    }

    private void load() {
        Db.group(code).get().addOnSuccessListener(g -> {
            String groupName = Snap.str(g.child("name"));
            findViewById(R.id.btnShare).setOnClickListener(v -> Ui.shareCode(this, groupName, code));
            String adminUid = Snap.str(g.child("adminUid"));
            List<Member> members = members(g);
            List<RowAdapter.Row> rows = new ArrayList<>();
            for (Member m : members) {
                RowAdapter.Row r = new RowAdapter.Row();
                r.uid = m.uid;
                r.name = m.name;
                r.color = m.color;
                r.title = m.uid.equals(Db.uid()) ? m.name + " (you)" : m.name;
                r.sub = Lang.s(R.string.joined_date, Ui.format("d MMM", m.joinedAt));
                if (m.uid.equals(adminUid)) {
                    r.badge = Lang.s(R.string.badge_admin);
                    r.badgeBg = Ui.INK;
                    r.badgeFg = Ui.PAPAYA;
                }
                rows.add(r);
            }
            adapter.setRows(rows);
            setToolbarTitle(Lang.s(R.string.members_count_title, members.size()));
        });
    }

    private static List<Member> members(DataSnapshot g) {
        List<Member> list = new ArrayList<>();
        for (DataSnapshot m : g.child("members").getChildren()) list.add(Member.from(m));
        Collections.sort(list, (a, b) -> Long.compare(a.joinedAt, b.joinedAt));
        return list;
    }

    /** If the admin leaves, the oldest member becomes admin. Last person out deletes the group. */
    private void leave() {
        String uid = Db.uid();
        Db.group(code).get().addOnSuccessListener(g -> {
            Map<String, Object> updates = new HashMap<>();
            updates.put("users/" + uid + "/groups/" + code, null);
            List<Member> others = new ArrayList<>();
            for (Member m : members(g)) if (!m.uid.equals(uid)) others.add(m);

            if (others.isEmpty()) {
                updates.put("groups/" + code, null);
            } else {
                updates.put("groups/" + code + "/members/" + uid, null);
                if (uid.equals(Snap.str(g.child("adminUid")))) {
                    updates.put("groups/" + code + "/adminUid", others.get(0).uid);
                }
            }
            Db.root().updateChildren(updates).addOnSuccessListener(x -> {
                Ui.toast(this, Lang.s(R.string.you_left_group));
                Intent i = new Intent(this, MainActivity.class);
                i.putExtra(MainActivity.EXTRA_TAB, R.id.nav_groups);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
            });
        });
    }
}
