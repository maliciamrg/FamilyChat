package com.malicia.familychat;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;

public class ChatActivity extends AppCompatActivity {

    FirebaseFirestore db;
    FirebaseAuth auth;

    ArrayList<Message> messages = new ArrayList<>();
    ChatAdapter adapter;

    RecyclerView recycler;
    TextView emptyStateView;
    String familyId;
    boolean isAdmin = false;
    private boolean isAppInForeground = false;
    private ListenerRegistration chatListener;
    private ListenerRegistration membershipListener;
    private static final int NOTIFICATION_PERMISSION_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("FamilyChat");
        }

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Request notification permission on API 33+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_CODE);
            }
        }

        // Add auth state listener to verify user is authenticated
        auth.addAuthStateListener(firebaseAuth -> {
            if (firebaseAuth.getCurrentUser() == null) {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            }
        });

        emptyStateView = findViewById(R.id.emptyStateText);

        db.collection("members")
                .document(auth.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        familyId = doc.getString("familyId");
                        String role = doc.getString("role");
                        isAdmin = role != null && role.equals("admin");
                        invalidateOptionsMenu();

                        setupChat();
                        listenForMembershipChanges();
                    } else {
                        auth.signOut();
                        startActivity(new Intent(this, LoginActivity.class));
                        finish();
                    }
                });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.clear();
        if (isAdmin) {
            menu.add(Menu.NONE, R.id.action_admin, 0, "Admin Panel");
        }
        menu.add(Menu.NONE, R.id.action_logout, 1, "Logout");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_admin) {
            startActivity(new Intent(this, AdminActivity.class));
            return true;
        } else if (id == R.id.action_logout) {
            auth.signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    void setupChat() {
        recycler = findViewById(R.id.chatRecycler);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recycler.setLayoutManager(layoutManager);

        adapter = new ChatAdapter(messages, auth.getCurrentUser().getEmail());
        recycler.setAdapter(adapter);

        findViewById(R.id.sendButton).setOnClickListener(v -> sendMessage());

        listenMessages();

        // Store FCM token for notifications
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                storeToken(task.getResult());
            }
        });
    }

    void sendMessage() {
        if (auth.getCurrentUser() == null) return;

        EditText input = findViewById(R.id.messageInput);
        String text = input.getText().toString().trim();

        if (text.isEmpty()) return;

        FirebaseUser user = auth.getCurrentUser();
        Message msg = new Message();
        msg.text = text;
        msg.sender = user.getEmail();
        msg.senderUid = user.getUid();
        msg.senderName = user.getDisplayName() != null ? user.getDisplayName() : user.getEmail();
        msg.timestamp = System.currentTimeMillis();
        msg.familyId = familyId;

        db.collection("family_chat").add(msg);
        input.setText("");
    }

    void listenMessages() {
        // Remove previous listener to prevent leaks
        if (chatListener != null) {
            chatListener.remove();
        }

        chatListener = db.collection("family_chat")
                .whereEqualTo("familyId", familyId)
                .orderBy("timestamp")
                .addSnapshotListener((value, error) -> {
                    if (value != null) {
                        int oldSize = messages.size();
                        messages.clear();

                        for (DocumentSnapshot doc : value) {
                            Message messageIn = doc.toObject(Message.class);
                            if (messageIn != null) {
                                messages.add(messageIn);
                            }
                        }

                        // Insert date separators (Today, Yesterday, etc.)
                        ChatAdapter.insertDateSeparators(messages);

                        adapter.notifyDataSetChanged();

                        // Update empty state
                        if (messages.isEmpty()) {
                            emptyStateView.setVisibility(View.VISIBLE);
                            recycler.setVisibility(View.GONE);
                        } else {
                            emptyStateView.setVisibility(View.GONE);
                            recycler.setVisibility(View.VISIBLE);
                            recycler.scrollToPosition(messages.size() - 1);
                        }

                        // Show notification if new message and app not in foreground
                        if (messages.size() > oldSize && !isAppInForeground && !messages.isEmpty()) {
                            Message lastMsg = messages.get(messages.size() - 1);
                            if (!lastMsg.sender.equals(auth.getCurrentUser().getEmail())) {
                                String name = lastMsg.senderName != null ? lastMsg.senderName : lastMsg.sender;
                                showNotification(name, lastMsg.text);
                            }
                        }
                    }
                });
    }

    void listenForMembershipChanges() {
        membershipListener = db.collection("members")
                .document(auth.getUid())
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;

                    if (value != null && value.exists()) {
                        String newFamilyId = value.getString("familyId");
                        if (newFamilyId != null && !newFamilyId.equals(familyId)) {
                            familyId = newFamilyId;
                            refreshChat();
                        }
                    } else {
                        auth.signOut();
                        startActivity(new Intent(this, LoginActivity.class));
                        finish();
                    }
                });
    }

    void refreshChat() {
        messages.clear();
        adapter.notifyDataSetChanged();
        listenMessages();
    }

    void storeToken(String token) {
        java.util.Map<String, Object> deviceToken = new java.util.HashMap<>();
        deviceToken.put("email", auth.getCurrentUser().getEmail());
        deviceToken.put("familyId", familyId);
        deviceToken.put("token", token);

        db.collection("device_tokens")
                .document(auth.getUid())
                .set(deviceToken);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isAppInForeground = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        isAppInForeground = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatListener != null) chatListener.remove();
        if (membershipListener != null) membershipListener.remove();
    }

    void showNotification(String sender, String message) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        String channelId = "family_chat_channel";
        String channelName = "Family Chat Messages";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, ChatActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("New message from " + sender)
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        notificationManager.notify((int) System.currentTimeMillis(), notificationBuilder.build());
    }
}
