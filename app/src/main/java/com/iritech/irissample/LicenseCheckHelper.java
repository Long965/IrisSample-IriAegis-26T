/*
package com.iritech.irissample;

import android.content.Context;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;

import com.iritech.android.widget.alertdialog.RegisterLicenseDialog;
import com.iritech.iris.DeveloperSettings;
import com.iritech.iris.LicenseInfo;

*/
/**
 * Helper class để kiểm tra và yêu cầu nhập License
 * Dùng chung cho toàn bộ app (Student, Admin enrollment, etc.)
 * 
 * Flow:
 * 1. Check Mock Mode → Bypass license
 * 2. Check license đã nhập chưa → Hiện dialog nếu chưa
 * 3. License OK → Chạy tiếp CaptureActivity
 *//*

public class LicenseCheckHelper {
    
    */
/**
     * Kiểm tra license và khởi chạy CaptureActivity
     * 
     * @param activity Activity gọi helper này
     * @param intent Intent cần khởi chạy (đã setup action và user_id)
     * @param requestCode Request code cho startActivityForResult
     * @return true nếu đã khởi chạy activity/dialog, false nếu có lỗi
     *//*

    public static boolean checkLicenseAndStartCapture(
            FragmentActivity activity, 
            Intent intent, 
            int requestCode) {
        
        // 1. Kiểm tra Mock Mode - bypass license check nếu đang ở dev mode
        DeveloperSettings devSettings = new DeveloperSettings(activity);
        if (devSettings.isMockModeEnabled()) {
            // Mock Mode enabled → bỏ qua kiểm tra license, đi thẳng vào capture
            activity.startActivityForResult(intent, requestCode);
            return true;
        }
        
        // 2. Kiểm tra và khởi tạo LicenseInfo nếu chưa
        LicenseInfo licInfo = LicenseInfo.getInstance();
        if (!licInfo.isInitialized()) {
            licInfo.initialize(activity);
        }
        
        // 3. Kiểm tra license đã nhập chưa
        if (!licInfo.isLicenseExisted()) {
            // Chưa nhập → Hiện dialog yêu cầu nhập Customer ID và License ID
            RegisterLicenseDialog regLicenseDlg = RegisterLicenseDialog.newInstance();
            regLicenseDlg.setActivityAfterRegistered(intent, requestCode);
            regLicenseDlg.show(activity.getSupportFragmentManager(), null);
            return true;
        }
        
        // 4. License OK → Khởi chạy capture activity
        activity.startActivityForResult(intent, requestCode);
        return true;
    }
    
    */
/**
     * Kiểm tra xem license đã được nhập chưa (không hiện dialog)
     * Dùng để kiểm tra trước khi cho phép một số thao tác
     * 
     * @param context Context để khởi tạo LicenseInfo
     * @return true nếu license đã nhập hoặc đang ở Mock Mode
     *//*

    public static boolean isLicenseAvailable(Context context) {
        // Check Mock Mode
        DeveloperSettings devSettings = new DeveloperSettings(context);
        if (devSettings.isMockModeEnabled()) {
            return true;
        }
        
        // Check License
        LicenseInfo licInfo = LicenseInfo.getInstance();
        if (!licInfo.isInitialized()) {
            licInfo.initialize(context);
        }
        
        return licInfo.isLicenseExisted();
    }
    
    */
/**
     * Hiển thị dialog yêu cầu nhập License (không gắn với intent sau đó)
     * Dùng khi muốn bắt user nhập license trước khi làm gì đó
     * 
     * @param activity Activity để hiện dialog
     *//*

    public static void showLicenseDialog(FragmentActivity activity) {
        // Check Mock Mode
        DeveloperSettings devSettings = new DeveloperSettings(activity);
        if (devSettings.isMockModeEnabled()) {
            // Mock Mode → Không cần nhập license
            return;
        }
        
        // Check license
        LicenseInfo licInfo = LicenseInfo.getInstance();
        if (!licInfo.isInitialized()) {
            licInfo.initialize(activity);
        }
        
        if (!licInfo.isLicenseExisted()) {
            RegisterLicenseDialog regLicenseDlg = RegisterLicenseDialog.newInstance();
            regLicenseDlg.show(activity.getSupportFragmentManager(), null);
        }
    }
}
*/
