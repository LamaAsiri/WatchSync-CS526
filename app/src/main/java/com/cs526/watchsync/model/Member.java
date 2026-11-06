package com.cs526.watchsync.model;

import com.cs526.watchsync.util.Snap;
import com.google.firebase.database.DataSnapshot;

import java.util.HashMap;
import java.util.Map;

/** A person inside a group (groups/{code}/members/{uid}). */
public class Member {
    public String uid = "";
    public String name = "";
    public String color = "";
    public long joinedAt;

    public static Member from(DataSnapshot s) {
        Member m = new Member();
        m.uid = s.getKey() == null ? "" : s.getKey();
        m.name = Snap.str(s.child("name"));
        m.color = Snap.str(s.child("color"));
        m.joinedAt = Snap.lng(s.child("joinedAt"));
        return m;
    }

    public static Map<String, Object> map(String name, String color) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("color", color);
        m.put("joinedAt", System.currentTimeMillis());
        return m;
    }
}
