package com.cs526.watchsync.auth;

import com.cs526.watchsync.util.Lang;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;

import com.cs526.watchsync.BaseActivity;
import com.cs526.watchsync.R;
import com.cs526.watchsync.data.Db;
import com.cs526.watchsync.data.Prefs;
import com.cs526.watchsync.main.MainActivity;
import com.cs526.watchsync.util.Avatars;
import com.cs526.watchsync.util.Snap;
import com.cs526.watchsync.util.Ui;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;

public class LoginActivity extends BaseActivity {
    private EditText etEmail, etPassword;
    private View btnLogin;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        setupToolbar("");

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        progress = findViewById(R.id.progress);

        btnLogin.setOnClickListener(v -> login());
        findViewById(R.id.tvForgot).setOnClickListener(v -> startActivity(new Intent(this, ForgotPasswordActivity.class)));
        findViewById(R.id.tvSignUp).setOnClickListener(v -> {
            startActivity(new Intent(this, SignUpActivity.class));
            finish();
        });
    }

    private void login() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        if (email.isEmpty() || password.isEmpty()) {
            Ui.toast(this, Lang.s(R.string.err_enter_email_password));
            return;
        }
        setLoading(true);
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> Db.me().get().addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        DataSnapshot me = task.getResult();
                        Prefs.saveUser(this, Snap.str(me.child("name")), Snap.str(me.child("color")));
                        Avatars.saveMine(this, Snap.str(me.child("photo")));
                    }
                    openHome();
                }))
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Ui.toast(this, Lang.s(R.string.err_login_failed, e.getMessage()));
                });
    }

    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void openHome() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
    }
}
