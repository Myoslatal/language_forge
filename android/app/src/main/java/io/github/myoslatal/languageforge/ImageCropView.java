package io.github.myoslatal.languageforge;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/** Bitmap crop overlay: draggable frame with four corner handles. */
public class ImageCropView extends View {

    private static final int MODE_NONE = 0;
    private static final int MODE_INSIDE = 1;
    private static final int MODE_TL = 2;
    private static final int MODE_TR = 3;
    private static final int MODE_BR = 4;
    private static final int MODE_BL = 5;

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG
            | Paint.FILTER_BITMAP_FLAG);
    private final Paint maskPaint = new Paint();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF imageRect = new RectF();
    private final RectF cropRect = new RectF();
    private Bitmap bitmap;
    private int mode = MODE_NONE;
    private float lastX;
    private float lastY;
    private float minSize;
    private float touchRadius;

    public ImageCropView(Context context) {
        this(context, null);
    }

    public ImageCropView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        minSize = dp(48);
        touchRadius = dp(28);
        maskPaint.setColor(0x99000000);
        borderPaint.setColor(ContextCompat.getColor(context, R.color.forge_accent));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(2));
        handlePaint.setColor(Color.WHITE);
        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setShadowLayer(dp(2), 0, dp(1), 0x66000000);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        cropRect.setEmpty();
        requestLayout();
        invalidate();
    }

    public Bitmap getCroppedBitmap() {
        if (bitmap == null || imageRect.isEmpty() || cropRect.isEmpty()) {
            return null;
        }
        float scaleX = bitmap.getWidth() / imageRect.width();
        float scaleY = bitmap.getHeight() / imageRect.height();
        int left = Math.round((cropRect.left - imageRect.left) * scaleX);
        int top = Math.round((cropRect.top - imageRect.top) * scaleY);
        int right = Math.round((cropRect.right - imageRect.left) * scaleX);
        int bottom = Math.round((cropRect.bottom - imageRect.top) * scaleY);
        left = Math.max(0, Math.min(bitmap.getWidth() - 1, left));
        top = Math.max(0, Math.min(bitmap.getHeight() - 1, top));
        right = Math.max(left + 1, Math.min(bitmap.getWidth(), right));
        bottom = Math.max(top + 1, Math.min(bitmap.getHeight(), bottom));
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutImage();
    }

    private void layoutImage() {
        if (bitmap == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        float scale = Math.min((float) getWidth() / bitmap.getWidth(),
                (float) getHeight() / bitmap.getHeight());
        float width = bitmap.getWidth() * scale;
        float height = bitmap.getHeight() * scale;
        float left = (getWidth() - width) / 2f;
        float top = (getHeight() - height) / 2f;
        imageRect.set(left, top, left + width, top + height);
        if (cropRect.isEmpty()) {
            float inset = Math.min(width, height) * 0.12f;
            cropRect.set(imageRect.left + inset, imageRect.top + inset,
                    imageRect.right - inset, imageRect.bottom - inset);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null) {
            return;
        }
        canvas.drawBitmap(bitmap, null, imageRect, bitmapPaint);

        canvas.drawRect(0, 0, getWidth(), cropRect.top, maskPaint);
        canvas.drawRect(0, cropRect.bottom, getWidth(), getHeight(), maskPaint);
        canvas.drawRect(0, cropRect.top, cropRect.left, cropRect.bottom, maskPaint);
        canvas.drawRect(cropRect.right, cropRect.top, getWidth(), cropRect.bottom, maskPaint);

        canvas.drawRect(cropRect, borderPaint);
        float r = dp(7);
        canvas.drawCircle(cropRect.left, cropRect.top, r, handlePaint);
        canvas.drawCircle(cropRect.right, cropRect.top, r, handlePaint);
        canvas.drawCircle(cropRect.right, cropRect.bottom, r, handlePaint);
        canvas.drawCircle(cropRect.left, cropRect.bottom, r, handlePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (bitmap == null) {
            return false;
        }
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mode = hitMode(x, y);
                if (mode == MODE_NONE) {
                    return false;
                }
                lastX = x;
                lastY = y;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mode == MODE_NONE) {
                    return false;
                }
                move(x, y);
                lastX = x;
                lastY = y;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mode = MODE_NONE;
                return true;
            default:
                return false;
        }
    }

    private int hitMode(float x, float y) {
        if (near(x, y, cropRect.left, cropRect.top)) {
            return MODE_TL;
        }
        if (near(x, y, cropRect.right, cropRect.top)) {
            return MODE_TR;
        }
        if (near(x, y, cropRect.right, cropRect.bottom)) {
            return MODE_BR;
        }
        if (near(x, y, cropRect.left, cropRect.bottom)) {
            return MODE_BL;
        }
        if (cropRect.contains(x, y)) {
            return MODE_INSIDE;
        }
        return MODE_NONE;
    }

    private boolean near(float x, float y, float tx, float ty) {
        float dx = x - tx;
        float dy = y - ty;
        return dx * dx + dy * dy <= touchRadius * touchRadius;
    }

    private void move(float x, float y) {
        if (mode == MODE_INSIDE) {
            float dx = x - lastX;
            float dy = y - lastY;
            if (cropRect.left + dx < imageRect.left) {
                dx = imageRect.left - cropRect.left;
            }
            if (cropRect.right + dx > imageRect.right) {
                dx = imageRect.right - cropRect.right;
            }
            if (cropRect.top + dy < imageRect.top) {
                dy = imageRect.top - cropRect.top;
            }
            if (cropRect.bottom + dy > imageRect.bottom) {
                dy = imageRect.bottom - cropRect.bottom;
            }
            cropRect.offset(dx, dy);
            return;
        }
        if (mode == MODE_TL || mode == MODE_BL) {
            cropRect.left = clamp(x, imageRect.left, cropRect.right - minSize);
        }
        if (mode == MODE_TR || mode == MODE_BR) {
            cropRect.right = clamp(x, cropRect.left + minSize, imageRect.right);
        }
        if (mode == MODE_TL || mode == MODE_TR) {
            cropRect.top = clamp(y, imageRect.top, cropRect.bottom - minSize);
        }
        if (mode == MODE_BL || mode == MODE_BR) {
            cropRect.bottom = clamp(y, cropRect.top + minSize, imageRect.bottom);
        }
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
