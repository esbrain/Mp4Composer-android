package com.daasuu.sample;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PictureDrawable;
import android.os.Build;
import android.os.Bundle;
//import android.support.v7.app.AppCompatActivity;
import android.os.Environment;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.FontAssetDelegate;
import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieCompositionFactory;
import com.airbnb.lottie.LottieDrawable;
import com.airbnb.lottie.LottieResult;
import com.airbnb.lottie.TextDelegate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 88888;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        findViewById(R.id.button).setOnClickListener(v -> {
            if (checkPermission()) {
                BasicUsageActivity.startActivity(MainActivity.this);
            }
        });
        findViewById(R.id.button2).setOnClickListener(v -> {
            if (checkPermission()) {
                MovieListActivity.startActivity(MainActivity.this);
            }
        });
        findViewById(R.id.button3).setOnClickListener(v -> {
            // Lottieの画像を書き出す。
            LottieResult res = LottieCompositionFactory.fromAssetSync(this,"compose.zip");
            if(res.getException() != null) {
                Log.d("main", "Lottie error", res.getException());
                return;
            }
            LottieDrawable drawable = new LottieDrawable();
            if(res.getValue() == null) {
                Log.d("main", "Lottie composition is null");
                return;
            }
            LottieComposition comp = (LottieComposition)res.getValue();
            drawable.setComposition(comp);
            Context me = this;
            drawable.setFontAssetDelegate(new FontAssetDelegate() {
                @Override
                public Typeface fetchFont(String fontFamily) {
                    return Typeface.createFromAsset(me.getResources().getAssets(), "SourceHanSans-Medium.otf");
                }
            });
            TextDelegate textDelegate = new TextDelegate(drawable);
            textDelegate.setText("NAME", "ABC");
            drawable.setTextDelegate(textDelegate);
            ImageView fakeImage = new ImageView(this);
            fakeImage.setBackground(drawable);
            drawable.setFrame(30);
            Bitmap bmp = toBitmap((Drawable)drawable);
            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "pic.jpg");
            Log.d("main", file.getAbsolutePath());
            saveBitmap(file, bmp);

        });
    }

    private Bitmap toBitmap(Drawable drawable) {
        Bitmap bmp = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        drawable.draw(canvas);
        return bmp;
    }

    private void saveBitmap(File out, Bitmap bmp) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(out);
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, fos);
            fos.close();
        } catch (IOException e) {
            Log.e("main", e.getMessage());
        } finally {
            try {
                if(fos != null) {
                    fos.close();
                }
            } catch(IOException e) {
                ;
            }
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        checkPermission();

    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        // request permission if it has not been grunted.
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
            return false;
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(MainActivity.this, "permission has been grunted.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "[WARN] permission is not grunted.", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

}
