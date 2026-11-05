package com.iritech.irissample.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.iritech.irissample.R;
import com.iritech.irissample.model.SubjectWithSchedules;

import java.util.List;

/**
 * Adapter đơn giản cho danh sách môn học
 * Không hiển thị schedule, chỉ thông tin cơ bản
 */
public class SubjectWithSchedulesAdapter extends RecyclerView.Adapter<SubjectWithSchedulesAdapter.ViewHolder> {

    private Context context;
    private List<SubjectWithSchedules> subjectList;
    private OnSubjectActionListener listener;

    public interface OnSubjectActionListener {
        void onSubjectClick(SubjectWithSchedules subject);
        void onViewStudentsClick(SubjectWithSchedules subject);
        void onImportStudentsClick(SubjectWithSchedules subject);
        void onMoreOptionsClick(SubjectWithSchedules subject, View anchor);
    }

    public SubjectWithSchedulesAdapter(Context context, List<SubjectWithSchedules> subjectList) {
        this.context = context;
        this.subjectList = subjectList;
    }

    public void setOnSubjectActionListener(OnSubjectActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_subject, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SubjectWithSchedules subject = subjectList.get(position);
        
        holder.txtSubjectId.setText(subject.getSubjectId());
        holder.txtSubjectName.setText(subject.getSubjectName());

        // Hiển thị khung giờ học
        String timeSlot = subject.getTimeSlot();
        if (!TextUtils.isEmpty(timeSlot)) {
            holder.txtTimeSlot.setText(timeSlot);
            holder.layoutTimeSlot.setVisibility(View.VISIBLE);
        } else {
            holder.layoutTimeSlot.setVisibility(View.GONE);
        }

        // Hiển thị giảng viên hoặc trạng thái chưa phân công
        if (subject.isAssigned() && subject.getInstructorName() != null) {
            holder.txtCreatedBy.setText(subject.getInstructorName());
            holder.txtCreatedBy.setTextColor(0xFF388E3C); // Green
            holder.imgInstructorIcon.setImageResource(R.drawable.ic_person);
        } else {
            holder.txtCreatedBy.setText("Chưa phân công giảng viên");
            holder.txtCreatedBy.setTextColor(0xFFE65100); // Orange
            holder.imgInstructorIcon.setImageResource(R.drawable.ic_warning);
        }

        // Click vào item
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSubjectClick(subject);
            }
        });

        // Nút xem sinh viên
        holder.btnViewStudents.setOnClickListener(v -> {
            if (listener != null) {
                listener.onViewStudentsClick(subject);
            }
        });

        // Nút import sinh viên
        holder.btnImportStudents.setOnClickListener(v -> {
            if (listener != null) {
                listener.onImportStudentsClick(subject);
            }
        });

        // Nút menu options (Xóa môn học)
        holder.btnMoreOptions.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMoreOptionsClick(subject, v);
            }
        });
    }

    @Override
    public int getItemCount() {
        return subjectList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtSubjectId, txtSubjectName, txtCreatedBy, txtTimeSlot;
        ImageView imgInstructorIcon;
        LinearLayout layoutTimeSlot;
        Button btnViewStudents, btnImportStudents, btnMoreOptions;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtSubjectId = itemView.findViewById(R.id.txtSubjectId);
            txtSubjectName = itemView.findViewById(R.id.txtSubjectName);
            txtCreatedBy = itemView.findViewById(R.id.txtCreatedBy);
            txtTimeSlot = itemView.findViewById(R.id.txtTimeSlot);
            imgInstructorIcon = itemView.findViewById(R.id.imgInstructorIcon);
            layoutTimeSlot = itemView.findViewById(R.id.layoutTimeSlot);
            btnViewStudents = itemView.findViewById(R.id.btnViewStudents);
            btnImportStudents = itemView.findViewById(R.id.btnImportStudents);
            btnMoreOptions = itemView.findViewById(R.id.btnMoreOptions);
        }
    }
}
