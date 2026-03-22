package com.malicia.familychat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.FirebaseFirestore;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class AdminAdapter extends RecyclerView.Adapter<AdminAdapter.MemberViewHolder>{

    ArrayList<AdminActivity.MemberItem> memberItems;
    String familyId;
    FirebaseFirestore db;
    OnMemberClickListener listener;

    public interface OnMemberClickListener {
        void onMemberLongClick(int position);
    }

    public AdminAdapter(ArrayList<AdminActivity.MemberItem> memberItems, String familyId, FirebaseFirestore db){

        this.memberItems=memberItems;
        this.familyId=familyId;
        this.db=db;

    }

    public void setOnMemberClickListener(OnMemberClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @NotNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull @NotNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(android.R.layout.simple_list_item_1, viewGroup, false);
        return new MemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull @NotNull MemberViewHolder viewHolder, int i) {
        AdminActivity.MemberItem item = memberItems.get(i);
        String displayText;
        if (item.isPending) {
            displayText = "Pending: " + item.email;
        } else {
            displayText = item.email + (item.role.equals("admin") ? " (Admin)" : "");
        }
        viewHolder.textView.setText(displayText);
        viewHolder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onMemberLongClick(i);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return memberItems.size();
    }

    static class MemberViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        public MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(android.R.id.text1);
        }
    }
}
