package com.iritech.irissample;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class AdminListActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_IMPORT_ADMIN_CSV = 3001;

    private ListView listViewAdmins;
    private TextView textViewEmpty;
    private Button buttonAddAdmin;
    private ImageButton buttonImportAdminCsv;
    private DatabaseHelper dbHelper;
    private AdminAdapter adapter;
    private ArrayList<Admin> adminList;
    private String currentUserEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_list);

        dbHelper = new DatabaseHelper(this);

        // Lay email cua Super Admin dang dang nhap
        currentUserEmail = getIntent().getStringExtra("USER_EMAIL");

        listViewAdmins = findViewById(R.id.listViewAdmins);
        textViewEmpty = findViewById(R.id.textViewEmpty);
        buttonAddAdmin = findViewById(R.id.buttonAddAdmin);
        buttonImportAdminCsv = findViewById(R.id.buttonImportAdminCsv);

        adminList = new ArrayList<>();
        adapter = new AdminAdapter();
        listViewAdmins.setAdapter(adapter);

        // Click vào item để xem chi tiết Admin
        listViewAdmins.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                Admin admin = adminList.get(position);
                // Mở AdminDetailActivity để xem/sửa thông tin Admin
                Intent intent = new Intent(AdminListActivity.this, AdminDetailActivity.class);
                intent.putExtra("SUPER_ADMIN_EMAIL", currentUserEmail); // Email Super Admin hiện tại
                intent.putExtra("TARGET_ADMIN_EMAIL", admin.email); // Email Admin được chọn
                startActivity(intent);
            }
        });

        buttonAddAdmin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Chuyen sang RegisterAdminActivity de them Admin moi
                Intent intent = new Intent(AdminListActivity.this, RegisterAdminActivity.class);
                intent.putExtra("SUPER_ADMIN_EMAIL", currentUserEmail); // Truyền email Super Admin để xác thực
                intent.putExtra("MODE", "CREATE_ADMIN"); // Chế độ tạo Admin mới (không phải tạo Super Admin đầu tiên)
                startActivity(intent);
            }
        });

        buttonImportAdminCsv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAdminCsvPicker();
            }
        });

        loadAdminList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Load lai danh sach khi quay ve
        loadAdminList();
    }

    private void loadAdminList() {
        adminList.clear();

        Cursor cursor = dbHelper.getAllAdmins();
        if (cursor != null && cursor.moveToFirst()) {
            do {
                Admin admin = new Admin();
                admin.id = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ADMIN_ID));
                admin.email = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ADMIN_EMAIL));
                admin.fullName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ADMIN_FULL_NAME));
                admin.phone = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ADMIN_PHONE));

                int codeIndex = cursor.getColumnIndex(DatabaseHelper.COL_ADMIN_CODE);
                if (codeIndex != -1) {
                    admin.adminCode = cursor.getString(codeIndex);
                }

                int photoIndex = cursor.getColumnIndex(DatabaseHelper.COL_ADMIN_PHOTO);
                if (photoIndex != -1) {
                    admin.photoPath = cursor.getString(photoIndex);
                }

                adminList.add(admin);
            } while (cursor.moveToNext());
            cursor.close();
        }

        if (adminList.isEmpty()) {
            listViewAdmins.setVisibility(View.GONE);
            textViewEmpty.setVisibility(View.VISIBLE);
        } else {
            listViewAdmins.setVisibility(View.VISIBLE);
            textViewEmpty.setVisibility(View.GONE);
        }

        adapter.notifyDataSetChanged();
    }

    // ============ IMPORT ADMIN CSV ============

    private void openAdminCsvPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/*");
        startActivityForResult(Intent.createChooser(intent, "Chọn file CSV danh sách Admin"), REQUEST_CODE_IMPORT_ADMIN_CSV);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_IMPORT_ADMIN_CSV && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                importAdminsFromCsv(uri);
            }
        }
    }

    /**
     * Import Admin từ file CSV
     * Format: email,full_name,password,phone,admin_code
     * Bắt buộc: email, full_name, password
     * Tùy chọn: phone, admin_code
     */
    private void importAdminsFromCsv(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));

            // Check BOM
            reader.mark(1);
            if (reader.read() != 0xFEFF) {
                reader.reset();
            }

            String line;
            int successCount = 0;
            int errorCount = 0;
            int lineNumber = 0;
            List<String> errors = new ArrayList<>();

            // Skip header
            reader.readLine();
            lineNumber++;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] tokens = line.split(",", -1);

                // Parse các cột
                String email = tokens.length > 0 ? tokens[0].trim() : "";
                String fullName = tokens.length > 1 ? tokens[1].trim() : "";
                String password = tokens.length > 2 ? tokens[2].trim() : "";
                String phone = tokens.length > 3 ? tokens[3].trim() : "";
                String adminCode = tokens.length > 4 ? tokens[4].trim() : "";

                // Validate bắt buộc
                if (email.isEmpty()) {
                    errors.add("Dòng " + lineNumber + ": Thiếu email");
                    errorCount++;
                    continue;
                }
                if (fullName.isEmpty()) {
                    errors.add("Dòng " + lineNumber + ": Thiếu họ tên");
                    errorCount++;
                    continue;
                }
                if (password.isEmpty()) {
                    errors.add("Dòng " + lineNumber + ": Thiếu mật khẩu");
                    errorCount++;
                    continue;
                }

                // Insert Admin (password sẽ được hash trong DatabaseHelper)
                boolean result = dbHelper.insertAdmin(
                        email, password, fullName,
                        null,  // dob
                        phone.isEmpty() ? null : phone,
                        null,  // gender
                        null,  // photoPath
                        null,  // description
                        adminCode.isEmpty() ? null : adminCode);

                if (result) {
                    successCount++;
                    // Gửi email thông báo tài khoản
                    EmailService.sendAccountCreatedEmail(email, fullName, adminCode);
                } else {
                    errors.add("Dòng " + lineNumber + ": Email '" + email + "' đã tồn tại \u2013 bỏ qua");
                    errorCount++;
                }
            }

            reader.close();

            // Hiển thị báo cáo
            showAdminImportReport(successCount, errorCount, errors);
            loadAdminList();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi khi import CSV: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Hiển thị báo cáo kết quả import Admin CSV
     */
    private void showAdminImportReport(int success, int errorCount, List<String> errorList) {
        StringBuilder msg = new StringBuilder();
        msg.append("[OK] ").append(success).append(" Admin đã thêm thành công\n");
        if (errorCount > 0) {
            msg.append("[X] ").append(errorCount).append(" dòng bị lỗi\n");
        }
        msg.append("\n");

        if (!errorList.isEmpty()) {
            msg.append("Chi tiết lỗi:\n");
            for (String error : errorList) {
                msg.append("- ").append(error).append("\n");
            }
        }

        if (success > 0 && errorCount == 0) {
            msg.append("\nTất cả Admin đã được import thành công!");
        }

        new AlertDialog.Builder(this)
            .setTitle("Kết quả Import Admin")
            .setMessage(msg.toString())
            .setPositiveButton("OK", null)
            .show();
    }

    // Xác thực Super Admin bằng Device Lock trước khi xóa Admin
    private void showDeleteConfirmDialog(final Admin admin) {
        BiometricAuthHelper.showBiometricPrompt(
            this,
            "Xác thực Super Admin",
            "Xác thực để xóa: " + admin.fullName,
            currentUserEmail,
            dbHelper,
            new BiometricAuthHelper.AuthCallback() {
                @Override
                public void onAuthSuccess() {
                    // Xác thực thành công -> xóa Admin
                    boolean success = dbHelper.deleteAdmin(admin.id);
                    if (success) {
                        Toast.makeText(AdminListActivity.this,
                                "Đã xóa Admin: " + admin.fullName, Toast.LENGTH_SHORT).show();

                        // Gửi email thông báo
                        EmailService.sendAccountDeletedEmail(admin.email, admin.fullName);

                        loadAdminList();
                    } else {
                        Toast.makeText(AdminListActivity.this,
                                "Lỗi khi xóa Admin", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onAuthFailed() {
                    Toast.makeText(AdminListActivity.this, "Đã hủy xóa", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onAuthError(String errorMessage) {
                    Toast.makeText(AdminListActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                }
            }
        );
    }

    // Adapter cho ListView
    private class AdminAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return adminList.size();
        }

        @Override
        public Object getItem(int position) {
            return adminList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(AdminListActivity.this)
                        .inflate(R.layout.item_admin, parent, false);
            }

            final Admin admin = adminList.get(position);

            TextView textViewFullName = convertView.findViewById(R.id.textViewFullName);
            TextView textViewEmail = convertView.findViewById(R.id.textViewEmail);
            TextView textViewPhone = convertView.findViewById(R.id.textViewPhone);
            TextView textViewInitials = convertView.findViewById(R.id.textViewInitials);
            TextView textViewAdminCode = convertView.findViewById(R.id.textViewAdminCode);
            CardView cardViewPhoto = convertView.findViewById(R.id.cardViewPhoto);
            ImageView imageViewPhoto = convertView.findViewById(R.id.imageViewPhoto);

            textViewFullName.setText(admin.fullName);
            textViewEmail.setText(admin.email);
            textViewPhone.setText(admin.phone != null && !admin.phone.isEmpty() ?
                    admin.phone : "Chua co SDT");

            // Hien thi ma so giang vien
            if (!TextUtils.isEmpty(admin.adminCode)) {
                textViewAdminCode.setText(admin.adminCode);
                textViewAdminCode.setVisibility(View.VISIBLE);
            } else {
                textViewAdminCode.setVisibility(View.GONE);
            }

            // Hien thi avatar: anh hoac chu cai viet tat
            boolean hasPhoto = false;
            if (!TextUtils.isEmpty(admin.photoPath)) {
                File photoFile = new File(admin.photoPath);
                if (photoFile.exists()) {
                    Bitmap bitmap = BitmapFactory.decodeFile(admin.photoPath);
                    if (bitmap != null) {
                        imageViewPhoto.setImageBitmap(bitmap);
                        cardViewPhoto.setVisibility(View.VISIBLE);
                        textViewInitials.setVisibility(View.GONE);
                        hasPhoto = true;
                    }
                }
            }

            if (!hasPhoto) {
                cardViewPhoto.setVisibility(View.GONE);
                textViewInitials.setVisibility(View.VISIBLE);
                textViewInitials.setText(getInitials(admin.fullName));

                // Set color based on position
                int[] colors = {0xFF3BA8A8, 0xFF7E57C2, 0xFFFF8A65, 0xFF43A047, 0xFF039BE5, 0xFFE53935};
                int color = colors[position % colors.length];
                GradientDrawable bg = (GradientDrawable) textViewInitials.getBackground().mutate();
                bg.setColor(color);
            }

            // Xử lý click vào toàn bộ item để xem chi tiết
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Mở AdminDetailActivity để xem/sửa thông tin Admin
                    Intent intent = new Intent(AdminListActivity.this, AdminDetailActivity.class);
                    intent.putExtra("SUPER_ADMIN_EMAIL", currentUserEmail);
                    intent.putExtra("TARGET_ADMIN_EMAIL", admin.email);
                    startActivity(intent);
                }
            });

            return convertView;
        }
    }

    // Class model cho Admin
    private static class Admin {
        String id;
        String email;
        String fullName;
        String phone;
        String adminCode;
        String photoPath;
    }

    /**
     * Lay chu cai viet tat tu ho ten (vi du: "Nguyen Van A" -> "NA")
     */
    private String getInitials(String fullName) {
        if (TextUtils.isEmpty(fullName)) return "?";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }
        String first = parts[0].substring(0, 1).toUpperCase();
        String last = parts[parts.length - 1].substring(0, 1).toUpperCase();
        return first + last;
    }
}
