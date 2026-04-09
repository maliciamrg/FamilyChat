package com.malicia.familychat;

import android.view.*;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    List<Message> messages;
    String myEmail;
    private static final int VIEW_ME = 1;
    private static final int VIEW_OTHER = 2;
    private static final int VIEW_DATE = 3;

    public ChatAdapter(List<Message> messages, String myEmail) {
        this.messages = messages;
        this.myEmail = myEmail;
    }

    @Override
    public int getItemViewType(int position) {
        Message msg = messages.get(position);
        if (msg.sender == null) return VIEW_DATE; // date separator
        if (msg.sender.equals(myEmail))
            return VIEW_ME;
        else
            return VIEW_OTHER;
    }

    @NonNull
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_DATE) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_date_separator, parent, false);
            return new DateHolder(v);
        } else if (viewType == VIEW_ME) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.message_me, parent, false);
            return new MeHolder(v);
        } else {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.message_other, parent, false);
            return new OtherHolder(v);
        }
    }

    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message msg = messages.get(position);

        if (holder instanceof DateHolder) {
            ((DateHolder) holder).dateText.setText(msg.text);
            return;
        }

        SimpleDateFormat timeFormat =
                new SimpleDateFormat("HH:mm", Locale.getDefault());
        String time = timeFormat.format(new Date(msg.timestamp));

        if (holder instanceof MeHolder) {
            MeHolder h = (MeHolder) holder;
            h.message.setText(msg.text);
            h.time.setText(time);
        } else {
            OtherHolder h = (OtherHolder) holder;
            h.message.setText(msg.text);
            h.time.setText(time);

            if (isSameSenderAsPrev(position, msg)) {
                h.sender.setVisibility(View.GONE);
            } else {
                h.sender.setVisibility(View.VISIBLE);
                // Show display name instead of email
                String name = msg.senderName != null ? msg.senderName : msg.sender;
                h.sender.setText(name);
            }
        }
    }

    private boolean isSameSenderAsPrev(int position, Message msg) {
        if (position > 0) {
            Message prev = messages.get(position - 1);
            if (prev.sender != null && prev.sender.equals(msg.sender)) {
                return true;
            }
        }
        return false;
    }

    public int getItemCount() {
        return messages.size();
    }

    /**
     * Insert date separator items into the message list.
     * Call this after populating messages and before notifyDataSetChanged().
     */
    public static void insertDateSeparators(List<Message> messages) {
        if (messages.isEmpty()) return;

        SimpleDateFormat dayFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        SimpleDateFormat displayFormat = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault());

        String lastDay = "";
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if (msg.sender == null) continue; // skip existing separators

            String day = dayFormat.format(new Date(msg.timestamp));
            if (!day.equals(lastDay)) {
                Message separator = new Message();
                separator.sender = null; // marks as separator
                separator.text = getDateLabel(msg.timestamp, displayFormat);
                separator.timestamp = msg.timestamp;
                messages.add(i, separator);
                lastDay = day;
                i++; // skip past the separator we just inserted
            } else {
                lastDay = day;
            }
        }
    }

    private static String getDateLabel(long timestamp, SimpleDateFormat displayFormat) {
        Calendar msgCal = Calendar.getInstance();
        msgCal.setTimeInMillis(timestamp);

        Calendar today = Calendar.getInstance();
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);

        if (isSameDay(msgCal, today)) {
            return "Today";
        } else if (isSameDay(msgCal, yesterday)) {
            return "Yesterday";
        } else {
            return displayFormat.format(new Date(timestamp));
        }
    }

    private static boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
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

    class DateHolder extends RecyclerView.ViewHolder {
        TextView dateText;

        public DateHolder(View itemView) {
            super(itemView);
            dateText = itemView.findViewById(R.id.dateText);
        }
    }
}
