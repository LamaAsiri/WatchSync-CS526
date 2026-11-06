package com.cs526.watchsync.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;

import com.cs526.watchsync.BaseActivity;
import com.cs526.watchsync.R;
import com.cs526.watchsync.data.Db;
import com.cs526.watchsync.data.Prefs;
import com.cs526.watchsync.main.MainActivity;
import com.cs526.watchsync.util.ColorPicker;
import com.cs526.watchsync.util.Ui;
import com.google.firebase.auth.FirebaseAuth;

import java.util.HashMap;
import java.util.Map;

public class SignUpActivity extends BaseActivity {
    private EditText etName, etEmail, etPassword, etConfirm;
    private View btnCreate;
    private ColorPicker picker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);
        setupToolbar("");

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirm = findViewById(R.id.etConfirm);
        btnCreate = findViewById(R.id.btnCreate);

        picker = new ColorPicker(findViewById(R.id.colorPicker), Ui.AVATAR_COLORS[0],
                () -> etName.getText().toString());
        etName.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { picker.refresh(); }
            public void afterTextChanged(Editable s) {}
        });

        btnCreate.setOnClickListener(v -> createAccount());
        findViewById(R.id.tvLogin).setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void createAccount() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        String confirm = etConfirm.getText().toString();

        if (name.isEmpty()) { Ui.toast(this, "Enter your name"); return; }
        if (email.isEmpty()) { Ui.toast(this, "Enter your email"); return; }
        if (password.length() < 6) { Ui.toast(this, "Password needs at least 6 characters"); return; }
        if (!password.equals(confirm)) { Ui.toast(this, "Passwords don't match"); return; }

        btnCreate.setEnabled(false);
        String color = picker.selected();
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    Map<String, Object> me = new HashMap<>();
                    me.put("name", name);
                    me.put("email", email);
                    me.put("color", color);
                    Db.me().updateChildren(me).addOnCompleteListener(t -> {
                        Prefs.saveUser(this, name, color);
                        Intent i = new Intent(this, MainActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                    });
                })
                .addOnFailureListener(e -> {
                    btnCreate.setEnabled(true);
                    Ui.toast(this, "Sign up failed: " + e.getMessage());
                });
    }
}
