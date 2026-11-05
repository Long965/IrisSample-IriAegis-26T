package com.iritech.irissample;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/*import com.iritech.android.widget.alertdialog.BestImageDialog;
import com.iritech.android.widget.alertdialog.SettingDialog;
import com.iritech.android.widget.alertdialog.RegisterLicenseDialog;
import com.iritech.iris.CaptureActivity;
import com.iritech.iris.Constants;
import com.iritech.iris.LicenseInfo;
import com.iritech.iris.Utilities;
import com.iritech.iris.DeveloperSettings;
import com.iritech.mqel704.GemResult;
import com.iritech.mqel704.ImageData;
import com.iritech.mqel704.ImageFormat;
import com.iritech.mqel704.ImageKind;*/

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    private static final int PERMISSIONS_REQUEST_CAMERA = 2;
    private static final int PERMISSIONS_READ_PHONE_STATE = 3;
    private static final int PERMISSIONS_ACCESS_NETWORK_STATE = 4;
    private static final int REQUEST_IRIS_PERMISSIONS = 5;

    private Intent pendingIrisIntent;
    private int pendingRequestCode;

    String enrollImgPath = (Environment.getExternalStorageDirectory().toString() + File.separator
            + "iritech" + File.separator + "enroll");
    String verifyImgPath = (Environment.getExternalStorageDirectory().toString() + File.separator
            + "iritech" + File.separator + "verify");
    String mUserId;
    private int REQUEST_CODE_IDENTIFY = 1111;
    private int REQUEST_CODE_CAPTURE = 1112;
    private int REQUEST_CODE_ENROLL = 1113;
    private int REQUEST_CODE_VERIFY = 1114;
    private int REQUEST_CODE_UNENROLL = 1115;
    private EditText editUserId;
    PowerManager.WakeLock wl;

    private int mResultCode;
    private String mAction;
    private int mRequestCode;

    Button btnMonHoc;
    Button btnQuanLyAdmin;
    androidx.cardview.widget.CardView cardMonHoc;
    androidx.cardview.widget.CardView cardQuanLyAdmin;
    androidx.cardview.widget.CardView cardMockCheckin;
    androidx.cardview.widget.CardView cardSinhVien;

    private DatabaseHelper dbHelper;

    private String currentUserEmail;
    private String currentUserRole;
    private String currentUserName;

    private static class Subject {
        String id;
        String name;
        Subject(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static class StudentAttendance {
        String studentId;
        String fullName;
        String attendanceStatus;
        StudentAttendance(String studentId, String fullName, String attendanceStatus) {
            this.studentId = studentId;
            this.fullName = fullName;
            this.attendanceStatus = attendanceStatus;
        }
        @Override
        public String toString() {
            return studentId + " - " + fullName + " - " +
                    (attendanceStatus != null ? attendanceStatus : "Chưa điểm danh");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //CaptureActivity.setUSBActivity(this);
        dbHelper = new DatabaseHelper(this);

        currentUserEmail = getIntent().getStringExtra("USER_EMAIL");
        currentUserRole  = getIntent().getStringExtra("USER_ROLE");
        currentUserName  = getIntent().getStringExtra("USER_NAME");

        checkFirstLoginAndShowDialog();

        try {
            PackageInfo pInfo = this.getPackageManager().getPackageInfo(this.getPackageName(), 0);
            String version = pInfo.versionName;
            TextView tvVersion = findViewById(R.id.tv_version);
            tvVersion.setText("Phiên bản: " + version);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        cardMonHoc      = findViewById(R.id.cardMonHoc);
        cardQuanLyAdmin = findViewById(R.id.cardQuanLyAdmin);
        cardMockCheckin = findViewById(R.id.cardMockCheckin);
        cardSinhVien    = findViewById(R.id.cardSinhVien);

        btnMonHoc      = findViewById(R.id.btnMonHoc);
        btnQuanLyAdmin = findViewById(R.id.btnQuanLyAdmin);

        // Môn học
        cardMonHoc.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SubjectListActivity.class);
            intent.putExtra("USER_EMAIL", currentUserEmail);
            startActivity(intent);
        });

        btnMonHoc.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SubjectListActivity.class);
            intent.putExtra("USER_EMAIL", currentUserEmail);
            startActivity(intent);
        });

        // Mock Checkin
        cardMockCheckin.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MockCheckinActivity.class);
            startActivity(intent);
        });

        // Quản lý Admin (chỉ Super Admin)
        if ("SUPER_ADMIN".equals(currentUserRole)) {
            cardQuanLyAdmin.setVisibility(View.VISIBLE);
            cardQuanLyAdmin.setOnClickListener(v ->
                    authenticateDeviceLockForAdminManagement());
        }

        // Mock Checkin Card - chỉ hiện khi Mock Mode bật
        //DeveloperSettings devSettings = new DeveloperSettings(this);
        //if (devSettings.isMockModeEnabled()) {
        //    cardMockCheckin.setVisibility(View.VISIBLE);
        //} else {
        //    cardMockCheckin.setVisibility(View.GONE);
        //}

        // ---- SINH VIÊN: mở StudentListActivity, không cần subject_id ----
        cardSinhVien.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, StudentListActivity.class);
            intent.putExtra("USER_EMAIL", currentUserEmail);
            intent.putExtra("USER_ROLE", currentUserRole);
            // subject_id = null → StudentListActivity sẽ load tất cả sinh viên
            startActivity(intent);
        });

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK |
                PowerManager.ACQUIRE_CAUSES_WAKEUP |
                PowerManager.ON_AFTER_RELEASE, "UbioXiris:MainActivity");
        wl.acquire();

        loadConfigs();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        checkPermissions(this);
    }

    private void checkFirstLoginAndShowDialog() {
        if (dbHelper.isFirstLogin(currentUserEmail)) {
            boolean hasAvatar = false;
            Cursor cursor = dbHelper.getAdminByEmail(currentUserEmail);
            if (cursor != null && cursor.moveToFirst()) {
                int photoIndex = cursor.getColumnIndex(DatabaseHelper.COL_ADMIN_PHOTO);
                if (photoIndex != -1) {
                    String photoPath = cursor.getString(photoIndex);
                    hasAvatar = photoPath != null && !photoPath.isEmpty();
                }
                cursor.close();
            }

            // --- ĐOẠN THAY ĐỔI LOGIC Ở ĐÂY ---
            // Nếu Admin (bất kể SUPER_ADMIN hay ADMIN thường) ĐÃ CÓ AVATAR rồi,
            // thì tự động đánh dấu hoàn thành login lần đầu và thoát ra, không hiện Dialog nữa.
            if (hasAvatar) {
                dbHelper.markFirstLoginComplete(currentUserEmail);
                return;
            }
            // ---------------------------------

            String title, message;
            if ("SUPER_ADMIN".equals(currentUserRole)) {
                title   = "Chào mừng Super Admin!";
                message = "Bạn đang đăng nhập lần đầu tiên.\n\n" +
                        "Vui lòng cập nhật Avatar của bạn ngay bây giờ để sử dụng đầy đủ tính năng của hệ thống.";
            } else {
                title   = "Chào mừng Admin!";
                message = "Bạn đang đăng nhập lần đầu tiên.\n\n" +
                        "Vui lòng thực hiện các bước sau:\n" +
                        "1. Cập nhật Avatar\n" +
                        "2. Đổi mật khẩu (password do Super Admin tạo)\n\n" +
                        "Để đảm bảo bảo mật và sử dụng đầy đủ tính năng.";
            }

            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton("Cập nhật ngay", (dialog, which) -> {
                        Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
                        intent.putExtra("USER_EMAIL", currentUserEmail);
                        intent.putExtra("IS_FIRST_LOGIN", true);
                        startActivity(intent);
                    })
                    .setNegativeButton("✕", (dialog, which) -> dialog.dismiss())
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        }
    }

    private void checkAndRequestLicense() {
        /*DeveloperSettings devSettings = new DeveloperSettings(this);
        if (devSettings.isMockModeEnabled()) return;

        LicenseInfo licInfo = LicenseInfo.getInstance();
        if (!licInfo.isInitialized()) licInfo.initialize(this);

        if (!licInfo.isLicenseExisted()) {
            new AlertDialog.Builder(this)
                    .setTitle("Cấu hình License")
                    .setMessage("Để sử dụng tính năng nhận diện mống mắt, bạn cần nhập Customer ID và License ID.\n\n" +
                            "Bạn có thể:\n• Nhập ngay bây giờ\n• Hoặc bật Mock Mode (Dev) trong Settings để test không cần license")
                    .setCancelable(false)
                    .setPositiveButton("Nhập License", (dialog, which) ->
                            LicenseCheckHelper.showLicenseDialog(MainActivity.this))
                    .setNegativeButton("Bật Mock Mode", (dialog, which) -> {
                        new DeveloperSettings(MainActivity.this).setMockModeEnabled(true);
                        Toast.makeText(MainActivity.this,
                                "Đã bật Mock Mode - Không cần license để test",
                                Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                    })
                    .setNeutralButton("Để sau", (dialog, which) -> {
                        Toast.makeText(MainActivity.this,
                                "Bạn có thể nhập License sau trong Settings",
                                Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                    })
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .show();
        }*/
    }

    @Override
    protected void onResume() {
        super.onResume();
        /*if (mResultCode == GemResult.IDDK_UVC_DEVICE_ACCESS_DENIED) {
            startCaptureActivity(mAction, mRequestCode);
        }*/
    }

    private void loadConfigs() {
        JSONParser jsonParser = new JSONParser();
        try (FileReader reader = new FileReader("/sdcard/iritech/irissample.json")) {
            Object obj = jsonParser.parse(reader);
            JSONObject jsonObject = new JSONObject(obj.toString());
            JSONArray permissionList = jsonObject.getJSONArray("permissions");
            for (int i = 0; i < permissionList.length(); i++) {
                JSONObject permission = permissionList.getJSONObject(i);
                String permissionName = permission.getString("name");
                int permissionCode = permission.getInt("code");
                if (ContextCompat.checkSelfPermission(this, permissionName) !=
                        PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{permissionName}, permissionCode);
                }
            }
        } catch (Exception var) {
            var.printStackTrace();
        }
    }

    private void startCaptureActivity(String actionType, int requestCode) {
        /*mUserId = editUserId.getText().toString().trim();
        if (mUserId.isEmpty() && (actionType.equals(Constants.ACTION_VERIFY)
                || actionType.equals(Constants.ACTION_ENROLL))) {
            BestImageDialog dialog = new BestImageDialog(this);
            dialog.setTitle("Thông tin!");
            dialog.show();
            dialog.setBestImages(null, null, null);
            dialog.setMessage("  Vui lòng nhập User ID  ");
            return;
        }
        Intent intent = new Intent(getApplicationContext(), CaptureActivity.class);
        intent.setAction(actionType);
        intent.putExtra(Constants.EXTRA_USER_ID, mUserId);
        checkIrisPermissionsAndStart(intent, requestCode);*/
    }

    private void checkIrisPermissionsAndStart(Intent intent, int requestCode) {
        boolean hasWrite = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        boolean hasCamera = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;

        if (hasWrite && hasCamera) {
            //LicenseCheckHelper.checkLicenseAndStartCapture(this, intent, requestCode);
            return;
        }

        pendingIrisIntent   = intent;
        pendingRequestCode  = requestCode;
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.CAMERA
                },
                REQUEST_IRIS_PERMISSIONS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data != null) {
            //mResultCode = data.getIntExtra(Constants.EXTRA_RESULT_CODE, -1);
        }
        /*if (mResultCode != GemResult.IDDK_UVC_DEVICE_ACCESS_DENIED) {
            if (resultCode == RESULT_OK) {
                processResult(requestCode, data);
            } else {
                Toast.makeText(getApplicationContext(),
                        "Hoạt động chụp ảnh thất bại", Toast.LENGTH_LONG).show();
            }
        }*/
    }

    private void processResult(int requestCode, Intent data) {
        /*if (requestCode == REQUEST_CODE_CAPTURE) {
            processCaptureResult(data, verifyImgPath, mUserId, "best");
        } else if (requestCode == REQUEST_CODE_ENROLL) {
            processCaptureResult(data, enrollImgPath, mUserId, "best");
        } else {
            Bitmap leftBm = null, rightBm = null, unknownBm = null;
            int resultCode = data.getIntExtra(Constants.EXTRA_RESULT_CODE, -1);
            String msg = data.getStringExtra(Constants.EXTRA_RESULT_MSG);
            msg += (resultCode != GemResult.IDDK_OK ? " (" + resultCode + ")" : "");
            if (requestCode == REQUEST_CODE_VERIFY) {
                if (resultCode == 0) {
                    boolean matchingResult = data.getBooleanExtra(
                            Constants.EXTRA_MATCHING_RESULT, false);
                    msg = matchingResult ? "Xác minh thành công" : "Xác minh thất bại! Không khớp";
                }
            } else if (requestCode == REQUEST_CODE_IDENTIFY) {
                if (resultCode == 0) {
                    int resultCount = data.getIntExtra(Constants.EXTRA_MATCHING_COUNT, 0);
                    String resultItems = data.getStringExtra(Constants.EXTRA_MATCHING_ITEMS);
                    msg = "Khớp với " + resultCount + " người dùng: " + resultItems;
                }
            }
            BestImageDialog dialog = new BestImageDialog(this);
            dialog.setTitle("Thông tin!");
            dialog.show();
            dialog.setBestImages(leftBm, rightBm, unknownBm);
            dialog.setMessage(msg);
        }*/
    }

    private void processCaptureResult(Intent data, String folderPath,
                                      String prefix, String filePurpose) {
        /*String imageExt    = ".bmp";
        String templateExt = ".tpl";
        Bitmap leftBm = null, rightBm = null, unknownBm = null;

        int resultCode = data.getIntExtra(Constants.EXTRA_RESULT_CODE, 0);
        String resultMsg = data.getStringExtra(Constants.EXTRA_RESULT_MSG);

        if (resultCode == 0) {
            resultMsg = "Thành công! Dữ liệu đã chụp được lưu tại: \n" + folderPath;
            byte[] leftTemplateBuffer  = data.getByteArrayExtra(Constants.EXTRA_LEFT_TEMPLATE);
            byte[] rightTemplateBuffer = data.getByteArrayExtra(Constants.EXTRA_RIGHT_TEMPLATE);

            Calendar c = Calendar.getInstance();
            prefix = prefix + String.format(Locale.US, "_%02d%02d%02d_%02d%02d%02d_",
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH),
                    c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND));

            saveFile(folderPath, prefix, filePurpose + "L" + templateExt, leftTemplateBuffer);
            saveFile(folderPath, prefix, filePurpose + "R" + templateExt, rightTemplateBuffer);

            ImageData leftImage    = new ImageData();
            ImageData rightImage   = new ImageData();
            ImageData unknownImage = new ImageData();
            byte[] bmpLeft = null, bmpRight = null, bmpUnknown = null;

            CaptureActivity.getResultImages(leftImage, rightImage, unknownImage);
            if (leftImage.getData() != null) {
                bmpLeft = Utilities.convertRawImageToBitmap(leftImage.getData(),
                        leftImage.getWidth(), leftImage.getHeight());
                saveFile(folderPath, prefix, filePurpose + "L" + imageExt, bmpLeft);
            }
            if (rightImage.getData() != null) {
                bmpRight = Utilities.convertRawImageToBitmap(rightImage.getData(),
                        rightImage.getWidth(), rightImage.getHeight());
                saveFile(folderPath, prefix, filePurpose + "R" + imageExt, bmpRight);
            }
            if (unknownImage.getData() != null) {
                bmpUnknown = Utilities.convertRawImageToBitmap(unknownImage.getData(),
                        unknownImage.getWidth(), unknownImage.getHeight());
                saveFile(folderPath, prefix, filePurpose + "U" + imageExt, bmpUnknown);
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inMutable = true;
            if (bmpLeft    != null) leftBm    = BitmapFactory.decodeByteArray(bmpLeft,    0, bmpLeft.length,    options);
            if (bmpRight   != null) rightBm   = BitmapFactory.decodeByteArray(bmpRight,   0, bmpRight.length,   options);
            if (bmpUnknown != null) unknownBm = BitmapFactory.decodeByteArray(bmpUnknown, 0, bmpUnknown.length, options);
        } else {
            resultMsg += " (" + resultCode + ")";
        }

        BestImageDialog dialog = new BestImageDialog(this);
        dialog.setTitle("Thông tin!");
        dialog.show();
        dialog.setBestImages(leftBm, rightBm, unknownBm);
        dialog.setMessage(resultMsg);*/
    }

    private void saveFile(String filePath, String prefix, String fileName, byte[] byteArray) {
        if (byteArray == null) return;
        String filePathName = filePath + File.separator + prefix + fileName;
        File folder = new File(filePath);
        File file   = new File(filePathName);
        try {
            if (!folder.exists()) folder.mkdirs();
            if (!file.exists())   file.createNewFile();
            FileOutputStream bos = new FileOutputStream(file);
            bos.write(byteArray);
            bos.flush();
            bos.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                new AlertDialog.Builder(this)
                        .setTitle("Thông tin!")
                        .setMessage("Vui lòng cho phép quyền ghi vào bộ nhớ ngoài.")
                        .setPositiveButton(android.R.string.ok, (d, w) ->
                                ActivityCompat.requestPermissions(this,
                                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                        PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE))
                        .show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
            }
        } else {
            initFolder();
        }
    }

    private boolean checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA)) {
                new AlertDialog.Builder(this)
                        .setTitle("Thông tin!")
                        .setMessage("Vui lòng cho phép quyền sử dụng camera.")
                        .setPositiveButton(android.R.string.ok, (d, w) ->
                                ActivityCompat.requestPermissions(this,
                                        new String[]{Manifest.permission.CAMERA},
                                        PERMISSIONS_REQUEST_CAMERA))
                        .show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        PERMISSIONS_REQUEST_CAMERA);
            }
            return false;
        }
        return true;
    }

    private void initFolder() {
        File enrollFolder = new File(enrollImgPath);
        if (!enrollFolder.exists()) enrollFolder.mkdirs();
        File verifyFolder = new File(verifyImgPath);
        if (!verifyFolder.exists()) verifyFolder.mkdirs();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_IRIS_PERMISSIONS) {
            boolean allGranted = grantResults.length > 0;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
            }
            if (allGranted && pendingIrisIntent != null) {
                //LicenseCheckHelper.checkLicenseAndStartCapture(
                 //       this, pendingIrisIntent, pendingRequestCode);
                pendingIrisIntent = null;
            } else {
                Toast.makeText(this,
                        "Cần cấp quyền Camera và bộ nhớ để sử dụng tính năng mống mắt.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CAMERA:
                if (!(grantResults.length > 0 &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    checkCameraPermission();
                }
                break;
            case PERMISSIONS_READ_PHONE_STATE:
                checkStorage(this);
                break;
            case PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE:
                if (grantResults.length > 0 &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initFolder();
                    checkNetWork(this);
                } else {
                    checkStoragePermission();
                }
                break;
        }
    }

    public static void writeWakeLock() {
        String path = "/sys/power/wake_lock";
        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new FileWriter(path));
            bw.write("dont_sleep");
            bw.newLine();
            bw.flush();
        } catch (IOException e) {
            Log.d("writeWakeLock", "IOException : " + e);
        } finally {
            if (bw != null) {
                try { bw.close(); } catch (IOException e) {
                    Log.d("writeWakeLock", "IOException : " + e);
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        MenuItem backupMenuItem = menu.findItem(R.id.action_backup_restore);
        if (backupMenuItem != null) {
            backupMenuItem.setVisible("SUPER_ADMIN".equals(currentUserRole));
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_profile) {
            Intent intent = new Intent(this, ProfileActivity.class);
            intent.putExtra("USER_EMAIL", currentUserEmail);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_backup_restore) {
            Intent intent = new Intent(this, BackupRestoreActivity.class);
            intent.putExtra("USER_EMAIL", currentUserEmail);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_settings) {
//            SettingDialog dialog = new SettingDialog(this);
 //           dialog.show();
            return true;
        } else if (id == R.id.action_logout) {
            Toast.makeText(this, "Đã đăng xuất", Toast.LENGTH_SHORT).show();
            SharedPreferences preferences = getSharedPreferences("user_prefs", MODE_PRIVATE);
            preferences.edit().clear().apply();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static void checkPermissions(final Context context) {
        if (ContextCompat.checkSelfPermission(context,
                Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale((Activity) context,
                    Manifest.permission.READ_PHONE_STATE)) {
                new AlertDialog.Builder(context)
                        .setTitle("Thông tin!")
                        .setMessage("Cần cho phép quyền đọc trạng thái điện thoại.")
                        .setPositiveButton(android.R.string.ok, (d, w) ->
                                ActivityCompat.requestPermissions((Activity) context,
                                        new String[]{Manifest.permission.READ_PHONE_STATE},
                                        PERMISSIONS_READ_PHONE_STATE))
                        .show();
            } else {
                ActivityCompat.requestPermissions((Activity) context,
                        new String[]{Manifest.permission.READ_PHONE_STATE},
                        PERMISSIONS_READ_PHONE_STATE);
            }
        } else {
            checkStorage(context);
        }
    }

    private static void checkStorage(final Context context) {
        if (ContextCompat.checkSelfPermission(context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale((Activity) context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                new AlertDialog.Builder(context)
                        .setTitle("Thông tin!")
                        .setMessage("Cần cho phép quyền truy cập bộ nhớ ngoài.")
                        .setPositiveButton(android.R.string.ok, (d, w) ->
                                ActivityCompat.requestPermissions((Activity) context,
                                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                        PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE))
                        .show();
            } else {
                ActivityCompat.requestPermissions((Activity) context,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
            }
        } else {
            checkNetWork(context);
        }
    }

    private static void checkNetWork(final Context context) {
        if (ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale((Activity) context,
                    Manifest.permission.ACCESS_NETWORK_STATE)) {
                new AlertDialog.Builder(context)
                        .setTitle("Thông tin!")
                        .setMessage("Cần cho phép quyền truy cập trạng thái mạng.")
                        .setPositiveButton(android.R.string.ok, (d, w) ->
                                ActivityCompat.requestPermissions((Activity) context,
                                        new String[]{Manifest.permission.ACCESS_NETWORK_STATE},
                                        PERMISSIONS_ACCESS_NETWORK_STATE))
                        .show();
            } else {
                ActivityCompat.requestPermissions((Activity) context,
                        new String[]{Manifest.permission.ACCESS_NETWORK_STATE},
                        PERMISSIONS_ACCESS_NETWORK_STATE);
            }
        }
    }

    private void authenticateDeviceLockForAdminManagement() {
        BiometricAuthHelper.showBiometricPrompt(
                this,
                "Xác thực để quản lý Admin",
                "Sử dụng vân tay, khuôn mặt hoặc mã PIN thiết bị",
                currentUserEmail,
                dbHelper,
                new BiometricAuthHelper.AuthCallback() {
                    @Override public void onAuthSuccess() {
                        Toast.makeText(MainActivity.this,
                                "Xác thực thành công!", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(MainActivity.this, AdminListActivity.class);
                        intent.putExtra("USER_EMAIL", currentUserEmail);
                        startActivity(intent);
                    }
                    @Override public void onAuthFailed() {
                        Toast.makeText(MainActivity.this,
                                "Xác thực bị hủy", Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onAuthError(String errorMessage) {
                        Toast.makeText(MainActivity.this,
                                errorMessage, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    @Override
    protected void onDestroy() {
        if (wl != null && wl.isHeld()) wl.release();
        dbHelper.close();
        super.onDestroy();
    }
}