package io.github.myoslatal.languageforge;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;

import io.github.myoslatal.languageforge.uikit.ForgeActivity;

/** Crop a bitmap into a cache PNG; returns path + size. */
public class ImageCropActivity extends ForgeActivity {

    public static final String EXTRA_SOURCE_PATH = "sourcePath";
    public static final String EXTRA_RESULT_PATH = "resultPath";
    public static final String EXTRA_WIDTH = "width";
    public static final String EXTRA_HEIGHT = "height";

    private ImageCropView cropView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String source = getIntent().getStringExtra(EXTRA_SOURCE_PATH);
        Bitmap bitmap = MediaFiles.decodeFile(source == null ? null : new File(source),
                MediaFiles.MAX_DECODE_DIMENSION);
        if (bitmap == null) {
            Toast.makeText(this, "图片读取失败", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setContentView(R.layout.activity_image_crop);
        applyTopInset(findViewById(R.id.crop_topbar));
        applyBottomInset(findViewById(R.id.crop_root));
        cropView = findViewById(R.id.crop_view);
        cropView.setBitmap(bitmap);
        findViewById(R.id.btn_crop_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_crop_done).setOnClickListener(v -> finishCrop());
    }

    private void finishCrop() {
        Bitmap cropped = cropView.getCroppedBitmap();
        if (cropped == null || cropped.getWidth() <= 0 || cropped.getHeight() <= 0) {
            Toast.makeText(this, "裁剪区域无效", Toast.LENGTH_SHORT).show();
            return;
        }
        File output = MediaFiles.cacheFile(this, "crop");
        if (output == null) {
            Toast.makeText(this, "无法创建裁剪文件", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            MediaFiles.writePng(cropped, output);
        } catch (IOException e) {
            MediaFiles.deleteQuietly(output);
            Toast.makeText(this, "裁剪保存失败", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent result = new Intent();
        result.putExtra(EXTRA_RESULT_PATH, output.getAbsolutePath());
        result.putExtra(EXTRA_WIDTH, cropped.getWidth());
        result.putExtra(EXTRA_HEIGHT, cropped.getHeight());
        setResult(RESULT_OK, result);
        finish();
    }
}
