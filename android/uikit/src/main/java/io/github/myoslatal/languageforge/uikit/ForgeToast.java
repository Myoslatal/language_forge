package io.github.myoslatal.languageforge.uikit;

import android.app.Activity;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Lightweight in-app toast with the Forge look: text-coloured pill, panel text,
 * 20dp radius, bottom centred and animated up into place.
 *
 * <p>By default it sits 114dp above the window bottom (clearing the fixed bottom
 * navigation) plus the system navigation-bar inset. A custom base margin can be
 * supplied; the navigation-bar inset is always added.</p>
 */
public final class ForgeToast {

    public static final int DEFAULT_BOTTOM_MARGIN_DP = 114;
    public static final long DEFAULT_DURATION_MS = 2300L;

    private static final String TAG = "forge_toast";

    private ForgeToast() {
    }

    public static void show(Activity activity, CharSequence text) {
        show(activity, text, DEFAULT_BOTTOM_MARGIN_DP, DEFAULT_DURATION_MS);
    }

    public static void show(Activity activity, CharSequence text, int bottomMarginDp) {
        show(activity, text, bottomMarginDp, DEFAULT_DURATION_MS);
    }

    public static void show(Activity activity, CharSequence text, int bottomMarginDp,
                            long durationMs) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) {
            return;
        }
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }

        View previous = content.findViewWithTag(TAG);
        if (previous != null) {
            content.removeView(previous);
        }

        int baseMarginPx = dp(activity, bottomMarginDp);
        int navInset = 0;
        WindowInsetsCompat rootInsets =
                ViewCompat.getRootWindowInsets(activity.getWindow().getDecorView());
        if (rootInsets != null) {
            navInset = bottomSafeInset(rootInsets);
        }

        TextView toast = new TextView(activity);
        toast.setTag(TAG);
        toast.setText(text);
        toast.setTextColor(ContextCompat.getColor(activity, R.color.forge_panel));
        toast.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        toast.setGravity(Gravity.CENTER);
        toast.setIncludeFontPadding(false);
        toast.setLineSpacing(0f, 1.25f);
        toast.setMaxLines(3);
        toast.setEllipsize(android.text.TextUtils.TruncateAt.END);
        toast.setMaxWidth((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.8f));
        toast.setBackgroundResource(R.drawable.forge_bg_toast);
        toast.setPadding(dp(activity, 16), dp(activity, 8),
                dp(activity, 16), dp(activity, 8));
        toast.setElevation(dp(activity, 8));
        toast.setAlpha(0f);
        toast.setTranslationY(dp(activity, 12));
        toast.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = baseMarginPx + navInset;
        content.addView(toast, lp);

        final int base = baseMarginPx;
        ViewCompat.setOnApplyWindowInsetsListener(toast, (view, insets) -> {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
            int bottom = base + bottomSafeInset(insets);
            if (params.bottomMargin != bottom) {
                params.bottomMargin = bottom;
                view.setLayoutParams(params);
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(toast);

        toast.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(240L)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        toast.postDelayed(() -> {
            if (toast.getParent() == null) {
                return;
            }
            toast.animate()
                    .alpha(0f)
                    .translationY(dp(activity, 12))
                    .setDuration(220L)
                    .withEndAction(() -> {
                        if (toast.getParent() != null) {
                            content.removeView(toast);
                        }
                    })
                    .start();
        }, Math.max(500L, durationMs));
    }

    public static void cancel(Activity activity) {
        if (activity == null) {
            return;
        }
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content != null) {
            View toast = content.findViewWithTag(TAG);
            if (toast != null) {
                toast.animate().cancel();
                content.removeView(toast);
            }
        }
    }

    private static int bottomSafeInset(WindowInsetsCompat insets) {
        Insets navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
        Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
        return Math.max(navigation.bottom, cutout.bottom);
    }

    private static int dp(Activity activity, float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}