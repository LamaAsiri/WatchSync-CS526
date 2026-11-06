package com.cs526.watchsync.auth;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import com.cs526.watchsync.BaseActivity;
import com.cs526.watchsync.R;
import com.cs526.watchsync.util.Ui;
import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);
        setupToolbar("");

        EditText etEmail = findViewById(R.id.etEmail);
        View status = findViewById(R.id.tvStatus);
        findViewById(R.id.btnSend).setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            if (email.isEmpty()) { Ui.toast(this, "Enter your email"); return; }
            FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                    .addOnSuccessListener(r -> status.setVisibility(View.VISIBLE))
                    .addOnFailureListener(e -> Ui.toast(this, "Couldn't send: " + e.getMessage()));
        });
    }
}
