package com.malicia.familychat;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class AdminAdapter extends RecyclerView.Adapter<AdminAdapter.MemberViewHolder> {

    ArrayList<AdminActivity.MemberItem> memberItems;
    OnMemberClickListener listener;

    // Avatar background colors
    private static final int[] AVATAR_COLORS = {
            0xFF075E54, 0xFF128C7E, 0xFF25D366, 0xFF34B7F1,
            0xFFE91E63, 0xFF9C27B0, 0xFFFF5722, 0xFF795548
    };

    public interface OnMemberClickListener {
        void onMemberClick(int position);
    }

    public AdminAdapter(ArrayList<AdminActivity.MemberItem> memberItems) {
        this.memberItems = memberItems;
    }

    public void setOnMemberClickListener(OnMemberClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.item_member, viewGroup, false);
        return new MemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemberViewHolder holder, int i) {
        AdminActivity.MemberItem item = memberItems.get(i);

        // Display name or email
        String displayName = item.displayName != null ? item.displayName : item.email;
        holder.memberName.setText(displayName);
        holder.memberEmail.setText(item.email);

        // Avatar - first letter with colored circle
        String initial = displayName != null && !displayName.isEmpty()
                ? displayName.substring(0, 1).toUpperCase() : "?";
        holder.memberAvatar.setText(initial);
        GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setShape(GradientDrawable.OVAL);
        avatarBg.setColor(AVATAR_COLORS[Math.abs(item.email.hashCode()) % AVATAR_COLORS.length]);
        holder.memberAvatar.setBackground(avatarBg);

        // Role badge
        if (item.isPending) {
            holder.memberBadge.setText("Pending");
            holder.memberBadge.setBackgroundResource(R.drawable.badge_pending);
            holder.memberBadge.setTextColor(0xFFE65100);
            holder.memberBadge.setVisibility(View.VISIBLE);
        } else if ("admin".equals(item.role)) {
            holder.memberBadge.setText("Admin");
            holder.memberBadge.setBackgroundResource(R.drawable.badge_admin);
            holder.memberBadge.setTextColor(0xFF1565C0);
            holder.memberBadge.setVisibility(View.VISIBLE);
        } else {
            holder.memberBadge.setVisibility(View.GONE);
        }

        // Click listener (tap instead of long-press for discoverability)
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMemberClick(holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return memberItems.size();
    }

    static class MemberViewHolder extends RecyclerView.ViewHolder {
        TextView memberAvatar;
        TextView memberName;
        TextView memberEmail;
        TextView memberBadge;

        public MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            memberAvatar = itemView.findViewById(R.id.memberAvatar);
            memberName = itemView.findViewById(R.id.memberName);
            memberEmail = itemView.findViewById(R.id.memberEmail);
            memberBadge = itemView.findViewById(R.id.memberBadge);
        }
    }
}
