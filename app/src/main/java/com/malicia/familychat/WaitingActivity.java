package com.malicia.familychat;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class WaitingActivity extends AppCompatActivity {

    FirebaseAuth auth;
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waiting);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        Button logoutButton = findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(v -> {
            auth.signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        // Listen for pending status changes
        listenForPendingChanges();
    }

    void listenForPendingChanges() {
        db.collection("pending_members")
                .document(auth.getUid())
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        return;
                    }

                    if (value == null || !value.exists()) {
                        // Pending request rejected or removed, logout
                        auth.signOut();
                        startActivity(new Intent(this, LoginActivity.class));
                        finish();
                    }
                });

        // Also listen for approval
        db.collection("members")
                .document(auth.getUid())
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        return;
                    }

                    if (value != null && value.exists()) {
                        // Approved, go to chat
                        startActivity(new Intent(this, ChatActivity.class));
                        finish();
                    }
                });
    }

}
