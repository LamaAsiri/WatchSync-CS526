package com.cs526.watchsync.main;

import com.cs526.watchsync.util.Lang;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.cs526.watchsync.R;
import com.cs526.watchsync.auth.WelcomeActivity;
import com.cs526.watchsync.data.Db;
import com.cs526.watchsync.data.LocalDb;
import com.cs526.watchsync.data.Prefs;
import com.cs526.watchsync.model.Show;
import com.cs526.watchsync.util.Avatars;
import com.cs526.watchsync.util.Ui;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.auth.FirebaseAuth;

import java.util.List;

public class ProfileFragment extends Fragment {
    private TextView tvAvatar, tvName, tvEmail, tvStatGroups, tvStatShows, tvStatEpisodes;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        tvAvatar = v.findViewById(R.id.tvAvatar);
        tvName = v.findViewById(R.id.tvName);
        tvEmail = v.findViewById(R.id.tvEmail);
        tvStatGroups = v.findViewById(R.id.tvStatGroups);
        tvStatShows = v.findViewById(R.id.tvStatShows);
        tvStatEpisodes = v.findViewById(R.id.tvStatEpisodes);
        ((TextView) v.findViewById(R.id.tvAbout)).setText(Lang.s(R.string.about_project) + "\n" + Lang.s(R.string.tmdb_notice));

        MaterialSwitch sw = v.findViewById(R.id.switchReminders);
        sw.setChecked(Prefs.reminders(requireContext()));
        sw.setOnCheckedChangeListener((button, on) -> Prefs.setReminders(requireContext(), on));

        MaterialSwitch swDark = v.findViewById(R.id.switchDarkMode);
        swDark.setChecked(Prefs.darkMode(requireContext()));
        swDark.setOnCheckedChangeListener((button, on) -> {
            if (on == Prefs.darkMode(requireContext())) return;
            Prefs.setDarkMode(requireContext(), on);
            Prefs.applyTheme(requireContext()); // the screen redraws itself in the new colours
        });

        TextView btnLanguage = v.findViewById(R.id.btnLanguage);
        btnLanguage.setText(Lang.s(R.string.language_value, languageName(Prefs.language(requireContext()))));
        btnLanguage.setOnClickListener(x -> pickLanguage());

        v.findViewById(R.id.btnEditProfile).setOnClickListener(x -> startActivity(new Intent(requireContext(), EditProfileActivity.class)));
        v.findViewById(R.id.btnLogout).setOnClickListener(x -> logout());
    }

    @Override
    public void onResume() {
        super.onResume();
        String name = Prefs.name(requireContext());
        Ui.avatar(tvAvatar, name, Prefs.color(requireContext()), Avatars.mine(requireContext()));
        Avatars.refreshMine(requireContext(), photo -> {
            if (isAdded()) Ui.avatar(tvAvatar, Prefs.name(requireContext()), Prefs.color(requireContext()), photo);
        });
        tvName.setText(name);
        tvEmail.setText(Db.email());

        List<Show> shows = LocalDb.get(requireContext()).all();
        int episodes = 0;
        for (Show s : shows) if (!s.movie()) episodes += s.progress;
        tvStatShows.setText(String.valueOf(shows.size()));
        tvStatEpisodes.setText(String.valueOf(episodes));
        Db.me().child("groups").get().addOnSuccessListener(g -> {
            if (isAdded()) tvStatGroups.setText(String.valueOf(g.getChildrenCount()));
        });
    }

    /** Phone language / English / العربية. Saved in SharedPreferences, then the app redraws in that language. */
    private void pickLanguage() {
        String[] tags = {"", "en", "ar"};
        String[] names = {Lang.s(R.string.language_system), Lang.s(R.string.language_english), Lang.s(R.string.language_arabic)};
        int current = java.util.Arrays.asList(tags).indexOf(Prefs.language(requireContext()));
        new AlertDialog.Builder(requireContext())
                .setTitle(Lang.s(R.string.language))
                .setSingleChoiceItems(names, Math.max(0, current), (d, which) -> {
                    d.dismiss();
                    if (tags[which].equals(Prefs.language(requireContext()))) return;
                    Prefs.setLanguage(requireContext(), tags[which]);
                    Lang.apply(requireContext()); // the screens are recreated in the new language
                })
                .setNegativeButton(Lang.s(R.string.cancel), null)
                .show();
    }

    private String languageName(String tag) {
        if (tag.equals("en")) return Lang.s(R.string.language_english);
        if (tag.equals("ar")) return Lang.s(R.string.language_arabic);
        return Lang.s(R.string.language_system);
    }

    private void logout() {
        FirebaseAuth.getInstance().signOut();
        LocalDb.get(requireContext()).clear();
        Prefs.clear(requireContext());
        Avatars.clear(requireContext());
        Intent i = new Intent(requireContext(), WelcomeActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
    }
}
