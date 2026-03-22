package com.malicia.familychat;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AdminActivity extends AppCompatActivity {

    FirebaseFirestore db;
    FirebaseAuth auth;
    RecyclerView recycler;
    ArrayList<MemberItem> memberItems = new ArrayList<>();
    AdminAdapter adapter;
    String familyId;

    static class MemberItem {
        String email;
        String role;
        boolean isPending;
        String uid;

        MemberItem(String email, String role, boolean isPending, String uid) {
            this.email = email;
            this.role = role;
            this.isPending = isPending;
            this.uid = uid;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState){

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Admin Panel");
        }

        db=FirebaseFirestore.getInstance();
        auth=FirebaseAuth.getInstance();

        db.collection("members")
                .document(auth.getUid())
                .get()
                .addOnSuccessListener(doc -> {

                    familyId = doc.getString("familyId");

                    // Now that familyId is set, set up the rest
                    setupAdminFeatures();

                });

    }

    private void setupAdminFeatures() {
        findViewById(R.id.createInviteButton).setOnClickListener(v -> {

            String code = "FAMILY-" +
                    UUID.randomUUID().toString().substring(0, 5);

            Map<String, Object> invite = new HashMap<>();

            invite.put("familyId", familyId);
            invite.put("createdBy", auth.getCurrentUser().getEmail());

            db.collection("invites")
                    .document(code)
                    .set(invite)
                    .addOnSuccessListener(aVoid -> {
                        // Show the code and share option
                        showInviteDialog(code);
                    });

        });

        findViewById(R.id.deleteMessagesButton).setOnClickListener(v -> {

            db.collection("family_chat")
                    .whereEqualTo("familyId", familyId)
                    .get()
                    .addOnSuccessListener(snapshot -> {

                        for(DocumentSnapshot doc : snapshot){

                            doc.getReference().delete();

                        }

                    });

        });

        findViewById(R.id.logoutButton).setOnClickListener(v -> logout());

        recycler = findViewById(R.id.listMembers);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        adapter = new AdminAdapter(memberItems, familyId, db);
        adapter.setOnMemberClickListener(position -> showMemberContextMenu(position));
        recycler.setAdapter(adapter);

        loadMembers();
    }

    private void loadMembers() {
        memberItems.clear();

        // Load approved members
        db.collection("members")
                .whereEqualTo("familyId", familyId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    for(DocumentSnapshot doc : snapshot){
                        String email = doc.getString("email");
                        String role = doc.getString("role");
                        String uid = doc.getId();
                        memberItems.add(new MemberItem(email, role, false, uid));
                    }

                    // Load pending members
                    db.collection("pending_members")
                            .whereEqualTo("familyId", familyId)
                            .get()
                            .addOnSuccessListener(pendingSnapshot -> {
                                for(DocumentSnapshot doc : pendingSnapshot){
                                    String email = doc.getString("email");
                                    String uid = doc.getId();
                                    memberItems.add(new MemberItem(email, "pending", true, uid));
                                }

                                adapter.notifyDataSetChanged();

                                if (!memberItems.isEmpty()) {
                                    recycler.scrollToPosition(memberItems.size() - 1);
                                }
                            });
                });
    }

    private void showMemberContextMenu(int position) {
        MemberItem member = memberItems.get(position);
        String[] options;
        if (member.isPending) {
            options = new String[]{"Validate", "Reject"};
        } else {
            options = new String[]{"Kick Off", "Change Role to Admin", "Change Role to Member"};
        }
        new AlertDialog.Builder(this)
                .setTitle("Member: " + member.email)
                .setItems(options, (dialog, which) -> {
                    if (member.isPending) {
                        switch (which) {
                            case 0:
                                validateMember(member);
                                break;
                            case 1:
                                rejectMember(member);
                                break;
                        }
                    } else {
                        switch (which) {
                            case 0:
                                kickOffMember(member.email);
                                break;
                            case 1:
                                changeMemberRole(member.email, "admin");
                                break;
                            case 2:
                                changeMemberRole(member.email, "member");
                                break;
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void kickOffMember(String email) {
        // Find the user ID by email and delete from members collection
        db.collection("members")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (DocumentSnapshot doc : snapshot) {
                        doc.getReference().delete()
                                .addOnSuccessListener(aVoid -> {
                                    loadMembers();
                                });
                    }
                });
    }

    private void changeMemberRole(String email, String newRole) {
        db.collection("members")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (DocumentSnapshot doc : snapshot) {
                        doc.getReference().update("role", newRole)
                                .addOnSuccessListener(aVoid -> {
                                    loadMembers();
                                });
                    }
                });
    }

    private void logout() {
        auth.signOut();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void showInviteDialog(String code) {
        new AlertDialog.Builder(this)
                .setTitle("Invite Code Created")
                .setMessage("Invite Code: " + code)
                .setPositiveButton("Share", (dialog, which) -> {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_TEXT, "Join my family chat with this code: " + code);
                    startActivity(Intent.createChooser(shareIntent, "Share Invite Code"));
                })
                .setNegativeButton("OK", null)
                .show();
    }

    private void validateMember(MemberItem member) {
        // Move from pending_members to members
        db.collection("pending_members")
                .document(member.uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String email = doc.getString("email");
                        String familyId = doc.getString("familyId");
                        String inviteCode = doc.getString("inviteCode");

                        // Add to members
                        Map<String, Object> memberData = new HashMap<>();
                        memberData.put("email", email);
                        memberData.put("familyId", familyId);
                        memberData.put("role", "member");

                        db.collection("members")
                                .document(member.uid)
                                .set(memberData)
                                .addOnSuccessListener(aVoid -> {
                                    // Delete from pending
                                    db.collection("pending_members")
                                            .document(member.uid)
                                            .delete()
                                            .addOnSuccessListener(aVoid2 -> {
                                                loadMembers();
                                            });
                                });
                    }
                });
    }

    private void rejectMember(MemberItem member) {
        // Delete from pending_members
        db.collection("pending_members")
                .document(member.uid)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    loadMembers();
                });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
