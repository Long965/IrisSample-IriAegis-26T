package com.iritech.irissample;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;


import androidx.annotation.NonNull;

public class BestImageDialog extends Dialog {
    private ImageView mLeftBestImage;
    private ImageView mRightBestImage;
    private ImageView mUnknownBestImage;
    private TextView mTvResultMessage;
    public BestImageDialog(@NonNull Context context) {
        super(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.best_image_dialog);
        mLeftBestImage = findViewById(R.id.left_best_img);
        mRightBestImage = findViewById(R.id.right_best_img);
        mUnknownBestImage = findViewById(R.id.unknown_best_img);
        Button mOKButton = findViewById(R.id.bt_ok);
        mOKButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        mTvResultMessage = findViewById(R.id.tv_result_msg);
    }

    public void setBestImages(Bitmap left, Bitmap right, Bitmap unknown) {
        if (left != null) {
            mLeftBestImage.setImageBitmap(left);
        } else {
            mLeftBestImage.setVisibility(View.GONE);
        }
        if (right != null) {
            mRightBestImage.setImageBitmap(right);
        } else {
            mRightBestImage.setVisibility(View.GONE);
        }

        if (unknown != null) {
            mUnknownBestImage.setImageBitmap(unknown);
        } else {
            mUnknownBestImage.setVisibility(View.GONE);
        }
    }

    public void setMessage(String msg) {
        if (msg != null) {
            mTvResultMessage.setText(msg);
        }
    }
}
