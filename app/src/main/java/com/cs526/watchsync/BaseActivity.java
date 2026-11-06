package com.cs526.watchsync;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

/** Every inner screen has the same top bar with a back arrow. */
public abstract class BaseActivity extends AppCompatActivity {
    protected void setupToolbar(String title) {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar == null) return;
        toolbar.setTitle(title);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    protected void setToolbarTitle(String title) {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setTitle(title);
    }
}
