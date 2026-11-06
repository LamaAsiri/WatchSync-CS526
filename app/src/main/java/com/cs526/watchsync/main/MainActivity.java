package com.cs526.watchsync.main;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.cs526.watchsync.R;
import com.cs526.watchsync.auth.WelcomeActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

/** Bottom navigation with 4 fragments: Home, My Shows, Groups, Profile. */
public class MainActivity extends AppCompatActivity {
    public static final String EXTRA_TAB = "tab";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setOnItemSelectedListener(item -> {
            show(fragmentFor(item.getItemId()));
            return true;
        });
        nav.setOnItemReselectedListener(item -> { /* stay */ });

        if (savedInstanceState == null) {
            int tab = getIntent().getIntExtra(EXTRA_TAB, R.id.nav_home);
            nav.setSelectedItemId(tab);
            show(fragmentFor(tab));
        }
        askNotificationPermission();
    }

    private Fragment fragmentFor(int id) {
        if (id == R.id.nav_shows) return new MyShowsFragment();
        if (id == R.id.nav_groups) return new GroupsFragment();
        if (id == R.id.nav_profile) return new ProfileFragment();
        return new HomeFragment();
    }

    private void show(Fragment f) {
        getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer, f).commit();
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
    }
}
