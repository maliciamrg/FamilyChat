package com.malicia.familychat;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.FirebaseFirestore;

@SuppressWarnings("deprecation")
public class LoginActivity extends AppCompatActivity {

    GoogleSignInClient googleClient;
    FirebaseAuth auth;
    FirebaseFirestore db;
    ActivityResultLauncher<Intent> signInLauncher;
    EditText inviteCodeInput;
    Button joinButton;
    SharedPreferences prefs;

    protected void onCreate(Bundle savedInstanceState){

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        prefs = getSharedPreferences("FamilyChatPrefs", MODE_PRIVATE);

        inviteCodeInput = findViewById(R.id.inviteCodeInput);
        joinButton = findViewById(R.id.joinButton);

        // Check if user is already logged in
        if (auth.getCurrentUser() != null) {
            checkUserFamily();
        } else {
            setupGoogleSignIn();
        }

        joinButton.setOnClickListener(v -> joinFamily());

    }

    private void setupGoogleSignIn() {
        GoogleSignInOptions gso =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(getString(R.string.default_web_client_id))
                        .requestEmail()
                        .build();

        googleClient = GoogleSignIn.getClient(this, gso);

        signInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        Task<GoogleSignInAccount> task =
                                GoogleSignIn.getSignedInAccountFromIntent(data);

                        try {

                            GoogleSignInAccount account =
                                    task.getResult(ApiException.class);

                            firebaseAuth(account.getIdToken());

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });

        findViewById(R.id.googleLoginButton).setOnClickListener(v -> {

            Intent signInIntent = googleClient.getSignInIntent();
            signInLauncher.launch(signInIntent);

        });
    }

    void firebaseAuth(String idToken){

        AuthCredential credential =
                GoogleAuthProvider.getCredential(idToken,null);

        auth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {

                    if(task.isSuccessful()){

                        checkUserFamily();

                    }

                });

    }

    private void checkUserFamily() {
        db.collection("members")
                .document(auth.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        // User exists, go to chat
                        startActivity(new Intent(this, ChatActivity.class));
                        finish();
                    } else {
                        // Check if pending
                        db.collection("pending_members")
                                .document(auth.getUid())
                                .get()
                                .addOnSuccessListener(pendingDoc -> {
                                    if (pendingDoc.exists()) {
                                        // Pending approval
                                        startActivity(new Intent(this, WaitingActivity.class));
                                        finish();
                                    } else {
                                        // New user, show invite code
                                        showInviteFields();
                                    }
                                });
                    }
                });
    }

    private void showInviteFields() {
        findViewById(R.id.googleLoginButton).setVisibility(View.GONE);
        inviteCodeInput.setVisibility(View.VISIBLE);
        joinButton.setVisibility(View.VISIBLE);
        String storedCode = prefs.getString("inviteCode", "");
        if (!storedCode.isEmpty()) {
            inviteCodeInput.setText(storedCode);
        }
    }

    private void joinFamily() {
        String code = inviteCodeInput.getText().toString().trim();
        if (code.isEmpty()) {
            Toast.makeText(this, "Enter invite code", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("invites")
                .document(code)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String familyId = doc.getString("familyId");
                        if (familyId != null) {
                            // Add user to pending_members for admin validation
                            java.util.Map<String, Object> pendingMember = new java.util.HashMap<>();
                            pendingMember.put("email", auth.getCurrentUser().getEmail());
                            pendingMember.put("familyId", familyId);
                            pendingMember.put("inviteCode", code);

                            db.collection("pending_members")
                                    .document(auth.getUid())
                                    .set(pendingMember)
                                    .addOnSuccessListener(aVoid -> {
                                        // Store invite code locally
                                        prefs.edit().putString("inviteCode", code).apply();
                                        Toast.makeText(this, "Request sent to admin for approval", Toast.LENGTH_SHORT).show();
                                        startActivity(new Intent(this, WaitingActivity.class));
                                        finish();
                                    });
                        } else {
                            Toast.makeText(this, "Invalid invite code", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "Invalid invite code", Toast.LENGTH_SHORT).show();
                    }
                });
    }

//        EditText email = findViewById(R.id.emailInput);
//        EditText pass = findViewById(R.id.passwordInput);
//
//        findViewById(R.id.loginButton).setOnClickListener(v->{
//
//            auth.signInWithEmailAndPassword(
//                    email.getText().toString(),
//                    pass.getText().toString()
//            ).addOnSuccessListener(res->{
//
//                startActivity(new Intent(this,ChatActivity.class));
//                finish();
//
//            });
//
//        });

}
