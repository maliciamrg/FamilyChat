package com.malicia.familychat;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

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
        String displayName;
        String role;
        boolean isPending;
        String uid;

        MemberItem(String email, String displayName, String role, boolean isPending, String uid) {
            this.email = email;
            this.displayName = displayName;
            this.role = role;
            this.isPending = isPending;
            this.uid = uid;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        Toolbar toolbar = findViewById(R.id.adminToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Admin Panel");
        }

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        db.collection("members")
                .document(auth.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    familyId = doc.getString("familyId");
                    setupAdminFeatures();
                });
    }

    private void setupAdminFeatures() {
        findViewById(R.id.createInviteButton).setOnClickListener(v -> {
            String code = "FAMILY-" +
                    UUID.randomUUID().toString().substring(0, 6).toUpperCase();

            Map<String, Object> invite = new HashMap<>();
            invite.put("familyId", familyId);
            invite.put("createdBy", auth.getCurrentUser().getEmail());

            db.collection("invites")
                    .document(code)
                    .set(invite)
                    .addOnSuccessListener(aVoid -> showInviteDialog(code));
        });

        // Clear chat with confirmation dialog
        findViewById(R.id.deleteMessagesButton).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Clear Chat History")
                    .setMessage("Are you sure you want to delete all messages? This cannot be undone.")
                    .setPositiveButton("Delete All", (dialog, which) -> {
                        db.collection("family_chat")
                                .whereEqualTo("familyId", familyId)
                                .get()
                                .addOnSuccessListener(snapshot -> {
                                    for (DocumentSnapshot doc : snapshot) {
                                        doc.getReference().delete();
                                    }
                                    Toast.makeText(this, "Chat cleared", Toast.LENGTH_SHORT).show();
                                });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        recycler = findViewById(R.id.listMembers);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        adapter = new AdminAdapter(memberItems);
        adapter.setOnMemberClickListener(position -> showMemberContextMenu(position));
        recycler.setAdapter(adapter);

        loadMembers();
    }

    private void loadMembers() {
        memberItems.clear();

        db.collection("members")
                .whereEqualTo("familyId", familyId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (DocumentSnapshot doc : snapshot) {
                        String email = doc.getString("email");
                        String displayName = doc.getString("displayName");
                        String role = doc.getString("role");
                        String uid = doc.getId();
                        memberItems.add(new MemberItem(email, displayName, role, false, uid));
                    }

                    db.collection("pending_members")
                            .whereEqualTo("familyId", familyId)
                            .get()
                            .addOnSuccessListener(pendingSnapshot -> {
                                for (DocumentSnapshot doc : pendingSnapshot) {
                                    String email = doc.getString("email");
                                    String displayName = doc.getString("displayName");
                                    String uid = doc.getId();
                                    memberItems.add(new MemberItem(email, displayName, "pending", true, uid));
                                }
                                adapter.notifyDataSetChanged();
                            });
                });
    }

    private void showMemberContextMenu(int position) {
        MemberItem member = memberItems.get(position);
        String title = member.displayName != null ? member.displayName : member.email;
        String[] options;

        if (member.isPending) {
            options = new String[]{"✅ Approve", "❌ Reject"};
        } else {
            options = new String[]{"🚫 Remove from Family", "👑 Make Admin", "👤 Make Member"};
        }

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(options, (dialog, which) -> {
                    if (member.isPending) {
                        if (which == 0) validateMember(member);
                        else rejectMember(member);
                    } else {
                        switch (which) {
                            case 0:
                                confirmKickMember(member);
                                break;
                            case 1:
                                changeMemberRole(member.uid, "admin");
                                break;
                            case 2:
                                changeMemberRole(member.uid, "member");
                                break;
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmKickMember(MemberItem member) {
        String name = member.displayName != null ? member.displayName : member.email;
        new AlertDialog.Builder(this)
                .setTitle("Remove Member")
                .setMessage("Are you sure you want to remove " + name + " from the family?")
                .setPositiveButton("Remove", (dialog, which) -> kickOffMember(member.uid))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void kickOffMember(String uid) {
        db.collection("members")
                .document(uid)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Member removed", Toast.LENGTH_SHORT).show();
                    loadMembers();
                });
    }

    private void changeMemberRole(String uid, String newRole) {
        db.collection("members")
                .document(uid)
                .update("role", newRole)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Role updated", Toast.LENGTH_SHORT).show();
                    loadMembers();
                });
    }

    private void showInviteDialog(String code) {
        new AlertDialog.Builder(this)
                .setTitle("Invite Code Created")
                .setMessage("Share this code with your family member:\n\n" + code)
                .setPositiveButton("Share", (dialog, which) -> {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_TEXT,
                            "Join our FamilyChat with this code: " + code);
                    startActivity(Intent.createChooser(shareIntent, "Share Invite Code"));
                })
                .setNeutralButton("Copy", (dialog, which) -> {
                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("Invite Code", code));
                    Toast.makeText(this, "Code copied!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Done", null)
                .show();
    }

    private void validateMember(MemberItem member) {
        db.collection("pending_members")
                .document(member.uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String email = doc.getString("email");
                        String displayName = doc.getString("displayName");
                        String pendingFamilyId = doc.getString("familyId");

                        Map<String, Object> memberData = new HashMap<>();
                        memberData.put("email", email);
                        memberData.put("displayName", displayName);
                        memberData.put("familyId", pendingFamilyId);
                        memberData.put("role", "member");

                        db.collection("members")
                                .document(member.uid)
                                .set(memberData)
                                .addOnSuccessListener(aVoid -> {
                                    db.collection("pending_members")
                                            .document(member.uid)
                                            .delete()
                                            .addOnSuccessListener(aVoid2 -> {
                                                Toast.makeText(this, "Member approved!", Toast.LENGTH_SHORT).show();
                                                loadMembers();
                                            });
                                });
                    }
                });
    }

    private void rejectMember(MemberItem member) {
        String name = member.displayName != null ? member.displayName : member.email;
        new AlertDialog.Builder(this)
                .setTitle("Reject Request")
                .setMessage("Reject " + name + "'s request to join?")
                .setPositiveButton("Reject", (dialog, which) -> {
                    db.collection("pending_members")
                            .document(member.uid)
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Request rejected", Toast.LENGTH_SHORT).show();
                                loadMembers();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
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
