package com.malicia.familychat;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;

public class ChatActivity extends AppCompatActivity {

    FirebaseFirestore db;
    FirebaseAuth auth;

    ArrayList<Message> messages = new ArrayList<>();
    ChatAdapter adapter;

    RecyclerView recycler;
    String familyId;
    private boolean isAppInForeground = false;

    @Override
    protected void onCreate(Bundle savedInstanceState){

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        db=FirebaseFirestore.getInstance();
        auth=FirebaseAuth.getInstance();

        // Add auth state listener to verify user is authenticated
        auth.addAuthStateListener(firebaseAuth -> {
            if (firebaseAuth.getCurrentUser() == null) {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            }
        });

        db.collection("members")
                .document(auth.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        familyId = doc.getString("familyId");
                        String role = doc.getString("role");

                        if(role != null && role.equals("admin")){

                            findViewById(R.id.adminButton).setVisibility(View.VISIBLE);

                            findViewById(R.id.adminButton).setOnClickListener(v ->
                                    startActivity(new Intent(this, AdminActivity.class))
                            );
                        }

                        // Now setup chat
                        setupChat();

                        // Listen for membership changes
                        listenForMembershipChanges();
                    } else {
                        // User not a member, logout
                        auth.signOut();
                        startActivity(new Intent(this, LoginActivity.class));
                        finish();
                    }
                });
    }

    void setupChat() {
        recycler=findViewById(R.id.chatRecycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ChatAdapter(messages,auth.getCurrentUser().getEmail(),db);
        recycler.setAdapter(adapter);

        findViewById(R.id.sendButton).setOnClickListener(v->sendMessage());

        listenMessages();

        // Store FCM token for notifications
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                storeToken(task.getResult());
            }
        });
    }

    void sendMessage(){
        if (auth.getCurrentUser() == null) return;

        EditText input=findViewById(R.id.messageInput);

        Message msg = new Message();

        msg.text = input.getText().toString();
        msg.sender = auth.getCurrentUser().getEmail();
        msg.timestamp = System.currentTimeMillis();
        msg.familyId = familyId;

        db.collection("family_chat").add(msg);
        input.setText("");

    }

    void listenMessages(){

        db.collection("family_chat")
                .orderBy("timestamp")
                .addSnapshotListener((value,error)->{

                    if (value != null) {
                        int oldSize = messages.size();
                        messages.clear();

                        for (DocumentSnapshot doc : value) {
                            Message messageIn = doc.toObject(Message.class);
                            if (messageIn != null && messageIn.familyId != null && messageIn.familyId.equals(familyId)) {
                                messages.add(messageIn);
                            }
                        }

                        adapter.notifyDataSetChanged();

                        recycler.scrollToPosition(messages.size() - 1);

                        // Show notification if new message and app not in foreground
                        if (messages.size() > oldSize && !isAppInForeground && !messages.isEmpty()) {
                            Message lastMsg = messages.get(messages.size() - 1);
                            if (!lastMsg.sender.equals(auth.getCurrentUser().getEmail())) {
                                showNotification(lastMsg.sender, lastMsg.text);
                            }
                        }
                    }
                });
    }

    void listenForMembershipChanges() {
        db.collection("members")
                .document(auth.getUid())
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        return;
                    }

                    if (value != null && value.exists()) {
                        String newFamilyId = value.getString("familyId");
                        if (newFamilyId != null && !newFamilyId.equals(familyId)) {
                            familyId = newFamilyId;
                            refreshChat();
                        }
                    } else {
                        // Membership revoked, logout
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

    void showNotification(String sender, String message) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        String channelId = "family_chat_channel";
        String channelName = "Family Chat Messages";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, ChatActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("New message from " + sender)
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        notificationManager.notify((int) System.currentTimeMillis(), notificationBuilder.build());
    }
}
