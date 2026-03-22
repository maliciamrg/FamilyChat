
package com.malicia.familychat;

import android.view.*;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>{

    List<Message> messages;
    String myEmail;
    FirebaseFirestore db;
    private static final int VIEW_ME = 1;
    private static final int VIEW_OTHER = 2;

    public ChatAdapter(List<Message> messages,String myEmail,FirebaseFirestore db){

        this.messages=messages;
        this.myEmail=myEmail;
        this.db=db;

    }

    @Override
    public int getItemViewType(int position) {

        Message msg = messages.get(position);

        if(msg.sender.equals(myEmail))
            return VIEW_ME;
        else
            return VIEW_OTHER;
    }

    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        if(viewType == VIEW_ME){

            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.message_me, parent, false);

            return new MeHolder(v);

        } else {

            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.message_other, parent, false);

            return new OtherHolder(v);
        }
    }

    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        SimpleDateFormat timeFormat =
                new SimpleDateFormat("HH:mm", Locale.getDefault());

        Message msg = messages.get(position);

        String time = timeFormat.format(new Date(msg.timestamp));

        if(holder instanceof MeHolder){

            MeHolder h = (MeHolder) holder;

            h.message.setText(msg.text);
            h.time.setText(time);

        }
        else{

            OtherHolder h = (OtherHolder) holder;

            h.message.setText(msg.text);
            h.time.setText(time);

            if(isSameSenderAsPrev(position, msg)){
                h.sender.setVisibility(View.GONE);
            }
            else{
                h.sender.setVisibility(View.VISIBLE);
                h.sender.setText(msg.sender);
            }
        }

    }

    private boolean isSameSenderAsPrev(int position, Message msg) {
        boolean showSender = false;

        if(position > 0){
            Message prev = messages.get(position - 1);

            if(prev.sender.equals(msg.sender)){
                showSender = true;
            }
        }
        return showSender;
    }

    public int getItemCount(){

        return messages.size();

    }

    class MeHolder extends RecyclerView.ViewHolder {

        TextView time;
        TextView message;

        public MeHolder(View itemView) {
            super(itemView);
            message = itemView.findViewById(R.id.messageText);
            time = itemView.findViewById(R.id.timeText);
        }
    }

    class OtherHolder extends RecyclerView.ViewHolder {

        TextView time;
        TextView message;
        TextView sender;

        public OtherHolder(View itemView) {
            super(itemView);
            message = itemView.findViewById(R.id.messageText);
            sender = itemView.findViewById(R.id.senderName);
            time = itemView.findViewById(R.id.timeText);
        }
    }
}
