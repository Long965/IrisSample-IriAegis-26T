package com.iritech.irissample.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.iritech.irissample.R;
import com.iritech.irissample.model.AttendanceRecord;

import java.util.List;

/**
 * Adapter để hiển thị danh sách điểm danh trong RecyclerView.
 *
 * Chế độ "Theo ngày": Mã SV | Họ tên | Trạng thái | Ngày | Giờ
 * - Đúng giờ: chữ màu xanh (mặc định)
 * - Đi muộn: chữ màu cam, nổi bật
 * - Vắng mặt: chỉ hiện Mã SV + Họ tên, ẩn ngày/giờ
 *
 * Chế độ "Theo kì": Mã SV | Họ tên | Trạng thái | Số buổi (x/N buổi)
 * - Sinh viên vắng từ 2 buổi trở lên trong kì sẽ được làm nổi bật (tên đỏ + icon cảnh báo)
 *   khi đang xem tab Vắng mặt.
 */
public class AttendanceRecordAdapter extends RecyclerView.Adapter<AttendanceRecordAdapter.ViewHolder> {

    private static final int COLOR_ON_TIME = 0xFF00796B; // xanh lá đậm (teal)
    private static final int COLOR_LATE = 0xFFFB8C00;    // cam nổi bật
    private static final int COLOR_ABSENT = 0xFFD32F2F;  // đỏ

    // Ngưỡng số buổi vắng để làm nổi bật sinh viên trong chế độ Theo kì
    private static final int ABSENT_HIGHLIGHT_THRESHOLD = 2;

    private Context context;
    private List<AttendanceRecord> recordList;
    private OnItemClickListener listener;

    // Trạng thái (tab) đang được xem: ON_TIME / LATE / ABSENT.
    // Dùng để hiển thị đúng số buổi và nhãn trạng thái trong chế độ Theo kì.
    private String currentDisplayStatus = AttendanceRecord.STATUS_ON_TIME;

    public interface OnItemClickListener {
        void onItemClick(AttendanceRecord record);
    }

    public AttendanceRecordAdapter(Context context, List<AttendanceRecord> recordList) {
        this.context = context;
        this.recordList = recordList;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    /**
     * Gọi hàm này mỗi khi tab trạng thái (Đúng giờ / Đi muộn / Vắng mặt) thay đổi,
     * để adapter biết hiển thị đúng số buổi tương ứng trong chế độ Theo kì.
     */
    public void setDisplayStatus(String status) {
        this.currentDisplayStatus = status;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_attendance_record, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AttendanceRecord record = recordList.get(position);
        holder.textStudentId.setText(record.getStudentId());

        if (record.isSemesterAggregate()) {
            bindSemesterAggregateRow(holder, record);
        } else {
            bindDailyRow(holder, record);
        }

        // Alternate row background
        if (position % 2 == 0) {
            holder.itemView.setBackgroundColor(context.getResources().getColor(android.R.color.white));
        } else {
            holder.itemView.setBackgroundColor(0xFFF5F5F5); // Light gray
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(record);
            }
        });
    }

    /**
     * Hiển thị dòng cho chế độ Theo kì: ẩn ngày/giờ, hiện "x/N buổi",
     * làm nổi bật sinh viên vắng nhiều buổi khi đang xem tab Vắng mặt.
     */
    private void bindSemesterAggregateRow(ViewHolder holder, AttendanceRecord record) {
        holder.textDate.setVisibility(View.GONE);
        holder.textTime.setVisibility(View.GONE);

        int statusColor;
        String statusLabel;
        if (AttendanceRecord.STATUS_LATE.equals(currentDisplayStatus)) {
            statusColor = COLOR_LATE;
            statusLabel = "Đi muộn";
        } else if (AttendanceRecord.STATUS_ABSENT.equals(currentDisplayStatus)) {
            statusColor = COLOR_ABSENT;
            statusLabel = "Vắng mặt";
        } else {
            statusColor = COLOR_ON_TIME;
            statusLabel = "Đúng giờ";
        }

        holder.textStatus.setText(statusLabel);
        holder.textStatus.setTextColor(statusColor);

        if (holder.textSessionFraction != null) {
            holder.textSessionFraction.setVisibility(View.VISIBLE);
            holder.textSessionFraction.setText(record.getSessionFractionText(currentDisplayStatus));
            holder.textSessionFraction.setTextColor(statusColor);
        }

        boolean isFlagged = AttendanceRecord.STATUS_ABSENT.equals(currentDisplayStatus)
                && record.getAbsentSessionCount() >= ABSENT_HIGHLIGHT_THRESHOLD;

        if (isFlagged) {
            holder.textFullName.setText(record.getFullName() + "  ⚠");
            holder.textFullName.setTextColor(COLOR_ABSENT);
        } else {
            holder.textFullName.setText(record.getFullName());
            holder.textFullName.setTextColor(ContextCompat.getColor(context, R.color.colorTextPrimary));
        }
    }

    /**
     * Hiển thị dòng cho chế độ Theo ngày: giữ nguyên logic cũ (có ngày/giờ).
     */
    private void bindDailyRow(ViewHolder holder, AttendanceRecord record) {
        holder.textFullName.setText(record.getFullName());
        holder.textFullName.setTextColor(ContextCompat.getColor(context, R.color.colorTextPrimary));

        if (holder.textSessionFraction != null) {
            holder.textSessionFraction.setVisibility(View.GONE);
        }

        if (record.isOnTime()) {
            holder.textStatus.setText("Đúng giờ");
            holder.textStatus.setTextColor(COLOR_ON_TIME);
            holder.textDate.setVisibility(View.VISIBLE);
            holder.textTime.setVisibility(View.VISIBLE);
            holder.textDate.setText(record.getShortDate());
            holder.textTime.setText(record.getShortTime());
            holder.textDate.setTextColor(COLOR_ON_TIME);
            holder.textTime.setTextColor(COLOR_ON_TIME);
        } else if (record.isLate()) {
            holder.textStatus.setText("Đi muộn");
            holder.textStatus.setTextColor(COLOR_LATE);
            holder.textDate.setVisibility(View.VISIBLE);
            holder.textTime.setVisibility(View.VISIBLE);
            holder.textDate.setText(record.getShortDate());
            holder.textTime.setText(record.getShortTime());
            holder.textDate.setTextColor(COLOR_LATE);
            holder.textTime.setTextColor(COLOR_LATE);
        } else {
            // Vắng mặt: chỉ hiện mã SV + tên, ẩn ngày/giờ
            holder.textStatus.setText("Vắng mặt");
            holder.textStatus.setTextColor(COLOR_ABSENT);
            holder.textDate.setVisibility(View.GONE);
            holder.textTime.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return recordList != null ? recordList.size() : 0;
    }

    /**
     * Cập nhật danh sách mới
     */
    public void updateList(List<AttendanceRecord> newList) {
        this.recordList = newList;
        notifyDataSetChanged();
    }

    /**
     * Lấy số lượng sinh viên đã điểm danh (đúng giờ hoặc đi muộn)
     */
    public int getAttendedCount() {
        int count = 0;
        if (recordList != null) {
            for (AttendanceRecord record : recordList) {
                if (record.isAttended()) {
                    count++;
                }
            }
        }
        return count;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textStudentId;
        TextView textFullName;
        TextView textStatus;
        TextView textDate;
        TextView textTime;
        TextView textSessionFraction;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textStudentId = itemView.findViewById(R.id.textStudentId);
            textFullName = itemView.findViewById(R.id.textFullName);
            textStatus = itemView.findViewById(R.id.textStatus);
            textDate = itemView.findViewById(R.id.textDate);
            textTime = itemView.findViewById(R.id.textTime);
            textSessionFraction = itemView.findViewById(R.id.textSessionFraction);
        }
    }
}