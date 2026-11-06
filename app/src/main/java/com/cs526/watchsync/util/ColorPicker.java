package com.cs526.watchsync.util;

import android.view.View;
import android.widget.TextView;

import com.cs526.watchsync.R;

/** The 4 avatar colours row (used in Sign up and Edit profile). */
public class ColorPicker {
    public interface NameSource {
        String name();
    }

    private final View root;
    private final TextView avatar;
    private final View[] dots = new View[4];
    private final NameSource nameSource;
    private String selected;

    public ColorPicker(View root, String startColor, NameSource nameSource) {
        this.root = root;
        this.nameSource = nameSource;
        this.avatar = root.findViewById(R.id.tvAvatar);
        dots[0] = root.findViewById(R.id.color0);
        dots[1] = root.findViewById(R.id.color1);
        dots[2] = root.findViewById(R.id.color2);
        dots[3] = root.findViewById(R.id.color3);
        for (int i = 0; i < dots.length; i++) {
            final String color = Ui.AVATAR_COLORS[i];
            dots[i].setOnClickListener(v -> select(color));
        }
        select(startColor == null || startColor.isEmpty() ? Ui.AVATAR_COLORS[0] : startColor);
    }

    public void select(String color) {
        selected = color;
        for (int i = 0; i < dots.length; i++) {
            boolean on = Ui.AVATAR_COLORS[i].equalsIgnoreCase(color);
            dots[i].setScaleX(on ? 1.15f : 0.85f);
            dots[i].setScaleY(on ? 1.15f : 0.85f);
            dots[i].setAlpha(on ? 1f : 0.55f);
        }
        refresh();
    }

    public void refresh() {
        Ui.avatar(avatar, nameSource.name(), selected);
    }

    public String selected() {
        return selected;
    }
}
