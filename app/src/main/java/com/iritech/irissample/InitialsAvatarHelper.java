package com.iritech.irissample;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.TextUtils;

/**
 * Utility class to generate initials avatar bitmaps
 */
public class InitialsAvatarHelper {

    private static final int[] COLORS = {
        0xFF3BA8A8, // Teal
        0xFF7E57C2, // Purple
        0xFFFF8A65, // Orange
        0xFF43A047, // Green
        0xFF039BE5, // Blue
        0xFFE53935  // Red
    };

    /**
     * Generate a circular Bitmap with initials text
     */
    public static Bitmap generateAvatar(String fullName, int size) {
        String initials = getInitials(fullName);
        int color = getColorForName(fullName);

        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Draw circle background
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(color);
        float radius = size / 2f;
        canvas.drawCircle(radius, radius, radius, bgPaint);

        // Draw initials text
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(size * 0.4f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        Rect textBounds = new Rect();
        textPaint.getTextBounds(initials, 0, initials.length(), textBounds);
        float y = radius - textBounds.exactCenterY();

        canvas.drawText(initials, radius, y, textPaint);

        return bitmap;
    }

    /**
     * Extract initials from full name (e.g., "Nguyen Van A" -> "NA")
     */
    public static String getInitials(String fullName) {
        if (TextUtils.isEmpty(fullName)) return "?";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }
        String first = parts[0].substring(0, 1).toUpperCase();
        String last = parts[parts.length - 1].substring(0, 1).toUpperCase();
        return first + last;
    }

    /**
     * Get a consistent color based on the name
     */
    private static int getColorForName(String name) {
        if (TextUtils.isEmpty(name)) return COLORS[0];
        int hash = Math.abs(name.hashCode());
        return COLORS[hash % COLORS.length];
    }
}
