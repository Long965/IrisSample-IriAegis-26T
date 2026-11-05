package com.iritech.irissample;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

/**
 * Adapter cho danh sách sinh viên trong StudentManagementActivity
 */
public class StudentMgmtAdapter extends RecyclerView.Adapter<StudentMgmtAdapter.ViewHolder> {

    public interface OnActionListener {
        void onEdit(StudentManagementActivity.StudentItem student);
        void onDelete(StudentManagementActivity.StudentItem student);
    }

    private final List<StudentManagementActivity.StudentItem> list;
    private final OnActionListener listener;

    public StudentMgmtAdapter(List<StudentManagementActivity.StudentItem> list,
                              OnActionListener listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_student_mgmt, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StudentManagementActivity.StudentItem s = list.get(position);

        holder.tvName.setText(s.fullName);
        holder.tvId.setText("Mã: " + s.studentId);
        holder.tvContact.setText(
                (s.email != null && !s.email.isEmpty() ? s.email : "") +
                        (s.phone != null && !s.phone.isEmpty() ? "  |  " + s.phone : "")
        );
        // Hiện có mật khẩu không
        boolean hasPwd = s.checkinPassword != null && !s.checkinPassword.isEmpty();
        holder.tvPassword.setText(hasPwd ? "🔑 Có mật khẩu điểm danh" : "⚠ Chưa có mật khẩu");
        holder.tvPassword.setTextColor(hasPwd ? 0xFF4CAF50 : 0xFFFF9800);

        // ================= LOGIC HIỂN THỊ ẢNH AVATAR SINH VIÊN TỔNG THỂ =================
        String photoPath = getStudentPhotoPathFromDb(holder.itemView.getContext(), s.studentId);

        if (photoPath != null && !photoPath.isEmpty() && new File(photoPath).exists()) {
            // Tối ưu hóa đọc luồng dữ liệu thu nhỏ để tránh lỗi tràn bộ nhớ heap (OOM)
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4; // Thu nhỏ độ phân giải ảnh xuống 4 lần để hiển thị danh sách mượt mà hơn
            Bitmap bitmap = BitmapFactory.decodeFile(photoPath, options);
            holder.imgStudentAvatar.setImageBitmap(bitmap);
        } else {
            // ĐÃ SỬA: Thay thế đoạn code check hệ thống bị lỗi compile
            holder.imgStudentAvatar.setImageResource(android.R.drawable.ic_menu_camera);
        }
        // ==============================================================================

        holder.btnEdit.setOnClickListener(v -> listener.onEdit(s));
        holder.btnDelete.setOnClickListener(v -> listener.onDelete(s));
    }

    // Tiện ích hỗ trợ truy vấn trực tiếp photo_path từ SQLite cục bộ dựa vào mã sinh viên
    private String getStudentPhotoPathFromDb(Context ctx, String studentId) {
        String path = null;
        try {
            DatabaseHelper dbHelper = new DatabaseHelper(ctx);
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT " + DatabaseHelper.COL_PHOTO_PATH +
                    " FROM " + DatabaseHelper.TABLE_STUDENTS +
                    " WHERE " + DatabaseHelper.COL_STUDENT_ID + " = ?", new String[]{studentId});
            if (cursor.moveToFirst()) {
                path = cursor.getString(0);
            }
            cursor.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return path;
    }

    @Override
    public int getItemCount() { return list.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvId, tvContact, tvPassword;
        ImageView imgStudentAvatar;
        ImageButton btnEdit, btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgStudentAvatar = itemView.findViewById(R.id.imgStudentAvatar);
            tvName     = itemView.findViewById(R.id.tvStudentName);
            tvId       = itemView.findViewById(R.id.tvStudentId);
            tvContact  = itemView.findViewById(R.id.tvStudentContact);
            tvPassword = itemView.findViewById(R.id.tvStudentPassword);
            btnEdit    = itemView.findViewById(R.id.btnEditStudent);
            btnDelete  = itemView.findViewById(R.id.btnDeleteStudent);
        }
    }
}