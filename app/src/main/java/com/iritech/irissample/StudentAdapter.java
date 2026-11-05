package com.iritech.irissample;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;

public class StudentAdapter extends BaseAdapter {

    private Context context;
    private ArrayList<String> studentNames;
    private ArrayList<String> studentIds;
    private ArrayList<String> studentSubjects;
    private OnStudentActionListener listener;

    private int selectedPosition = -1;

    private static final int[] AVATAR_COLORS = {
            0xFF66CFCF, 0xFF4DB6AC, 0xFFFF8A65,
            0xFF7E57C2, 0xFF42A5F5, 0xFFEF5350
    };

    // ĐÃ SỬA: Thêm đầy đủ 3 hàm cần thiết cho Interface lắng nghe
    public interface OnStudentActionListener {
        void onEditClick(String studentId);
        void onDeleteClick(String studentId); // Thêm hàm này để đồng bộ với StudentListActivity
        void onStudentSelected(String studentId);
    }

    public StudentAdapter(Context context,
                          ArrayList<String> studentNames,
                          ArrayList<String> studentIds,
                          ArrayList<String> studentSubjects,
                          OnStudentActionListener listener) {

        this.context = context;
        this.studentNames = studentNames;
        this.studentIds = studentIds;
        this.studentSubjects = studentSubjects;
        this.listener = listener;
    }

    @Override
    public int getCount() {
        return studentNames.size();
    }

    @Override
    public Object getItem(int position) {
        return studentNames.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context)
                    .inflate(R.layout.item_student, parent, false);

            holder = new ViewHolder();
            holder.layoutRoot = convertView.findViewById(R.id.layoutRoot);
            holder.tvAvatar = convertView.findViewById(R.id.tvAvatar);

            View avatarView = convertView.findViewById(R.id.imgStudentAvatar);
            if (avatarView instanceof ImageView) {
                holder.imgStudentAvatar = (ImageView) avatarView;
            } else {
                holder.imgStudentAvatar = null;
            }

            holder.tvStudentName = convertView.findViewById(R.id.tvStudentName);
            holder.tvStudentStatus = convertView.findViewById(R.id.tvStudentStatus);
            holder.btnEdit = convertView.findViewById(R.id.btnEdit);

            // ĐÃ SỬA: Tìm thêm nút xóa btnDelete từ giao diện item_student nếu có
            View deleteView = convertView.findViewById(R.id.btnDelete);
            if (deleteView instanceof ImageButton) {
                holder.btnDelete = (ImageButton) deleteView;
            } else {
                holder.btnDelete = null;
            }

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        String studentName = studentNames.get(position);
        String studentId = studentIds.get(position);
        String subjectName = studentSubjects.get(position);

        holder.tvStudentName.setText(studentName);

        if (position == selectedPosition) {
            holder.layoutRoot.setBackgroundResource(R.drawable.bg_student_selected);
        } else {
            holder.layoutRoot.setBackgroundResource(R.drawable.bg_student_normal);
        }

        holder.tvStudentStatus.setText(subjectName);

        // ================= LOGIC LOAD ẢNH KHÔNG DÙNG THƯ VIỆN NGOÀI =================
        String photoPath = getStudentPhotoPathFromLocal(studentId);

        if (photoPath != null && !photoPath.isEmpty() && new File(photoPath).exists() && holder.imgStudentAvatar != null) {
            holder.imgStudentAvatar.setVisibility(View.VISIBLE);
            holder.tvAvatar.setVisibility(View.INVISIBLE);

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4;
            Bitmap bitmap = BitmapFactory.decodeFile(photoPath, options);
            holder.imgStudentAvatar.setImageBitmap(bitmap);
        } else {
            if (holder.imgStudentAvatar != null) {
                holder.imgStudentAvatar.setVisibility(View.GONE);
            }
            holder.tvAvatar.setVisibility(View.VISIBLE);

            String initials = getInitials(studentName);
            holder.tvAvatar.setText(initials);

            GradientDrawable avatarBg = new GradientDrawable();
            avatarBg.setShape(GradientDrawable.OVAL);
            avatarBg.setColor(AVATAR_COLORS[Math.abs(studentName.hashCode()) % AVATAR_COLORS.length]);
            holder.tvAvatar.setBackground(avatarBg);
        }
        // =========================================================================

        holder.layoutRoot.setOnClickListener(v -> {
            selectedPosition = position;
            if (listener != null) {
                listener.onStudentSelected(studentId);
            }
            notifyDataSetChanged();
        });

        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditClick(studentId);
            }
        });

        // BỔ SUNG: Lắng nghe sự kiện bấm nút Xóa sinh viên
        if (holder.btnDelete != null) {
            holder.btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteClick(studentId);
                }
            });
        }

        return convertView;
    }

    private String getStudentPhotoPathFromLocal(String studentId) {
        String path = null;
        try {
            DatabaseHelper dbHelper = new DatabaseHelper(context);
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

    private static class ViewHolder {
        LinearLayout layoutRoot;
        TextView tvAvatar;
        ImageView imgStudentAvatar;
        TextView tvStudentName;
        TextView tvStudentStatus;
        ImageButton btnEdit;
        ImageButton btnDelete; // Thêm biến đại diện nút xóa trong ViewHolder
    }

    private String getInitials(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        String first = parts[0].substring(0, 1).toUpperCase();
        String last = parts[parts.length - 1].substring(0, 1).toUpperCase();
        return first + last;
    }
}