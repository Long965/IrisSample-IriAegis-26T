package com.iritech.irissample.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.iritech.irissample.R;
import com.iritech.irissample.model.EmailRecipient;

import java.util.List;

/**
 * Adapter cho RecyclerView hiển thị danh sách email recipients
 * Có checkbox để select email cần gửi báo cáo
 */
public class EmailRecipientAdapter extends RecyclerView.Adapter<EmailRecipientAdapter.ViewHolder> {

    private Context context;
    private List<EmailRecipient> recipients;
    private OnRecipientActionListener listener;

    public interface OnRecipientActionListener {
        void onDeleteClick(EmailRecipient recipient);
        void onSelectionChanged(int selectedCount);
    }

    public EmailRecipientAdapter(Context context, List<EmailRecipient> recipients) {
        this.context = context;
        this.recipients = recipients;
    }

    public void setOnRecipientActionListener(OnRecipientActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_email_recipient, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EmailRecipient recipient = recipients.get(position);

        holder.checkBox.setChecked(recipient.isSelected());
        holder.textEmail.setText(recipient.getEmailAddress());
        
        // Hiển thị tên nếu có
        if (recipient.getRecipientName() != null && !recipient.getRecipientName().trim().isEmpty()) {
            holder.textName.setText(recipient.getRecipientName());
            holder.textName.setVisibility(View.VISIBLE);
        } else {
            holder.textName.setVisibility(View.GONE);
        }

        // Checkbox click
        holder.checkBox.setOnClickListener(v -> {
            boolean isChecked = holder.checkBox.isChecked();
            recipient.setSelected(isChecked);
            if (listener != null) {
                listener.onSelectionChanged(getSelectedCount());
            }
        });

        // Item click - toggle checkbox
        holder.itemView.setOnClickListener(v -> {
            holder.checkBox.performClick();
        });

        // Delete button click
        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteClick(recipient);
            }
        });
    }

    @Override
    public int getItemCount() {
        return recipients.size();
    }

    /**
     * Đếm số email đã được select
     */
    public int getSelectedCount() {
        int count = 0;
        for (EmailRecipient recipient : recipients) {
            if (recipient.isSelected()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Lấy danh sách email đã được select
     */
    public String[] getSelectedEmails() {
        java.util.ArrayList<String> selected = new java.util.ArrayList<>();
        for (EmailRecipient recipient : recipients) {
            if (recipient.isSelected()) {
                selected.add(recipient.getEmailAddress());
            }
        }
        return selected.toArray(new String[0]);
    }

    /**
     * Select hoặc deselect tất cả
     */
    public void selectAll(boolean select) {
        for (EmailRecipient recipient : recipients) {
            recipient.setSelected(select);
        }
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionChanged(getSelectedCount());
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        TextView textEmail;
        TextView textName;
        ImageButton btnDelete;

        ViewHolder(View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.checkBoxSelect);
            textEmail = itemView.findViewById(R.id.textEmail);
            textName = itemView.findViewById(R.id.textRecipientName);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
