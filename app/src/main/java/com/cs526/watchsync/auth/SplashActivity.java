package com.cs526.watchsync.auth;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.cs526.watchsync.data.Prefs;
import com.cs526.watchsync.main.MainActivity;
import com.google.firebase.auth.FirebaseAuth;

/** First screen: already logged in -> Home, otherwise -> Welcome. */
public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Prefs.applyTheme(this); // light or dark, saved in SharedPreferences
        super.onCreate(savedInstanceState);
        boolean loggedIn = FirebaseAuth.getInstance().getCurrentUser() != null;
        startActivity(new Intent(this, loggedIn ? MainActivity.class : WelcomeActivity.class));
        finish();
    }
}
