package io.github.myoslatal.languageforge.uikit;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Base activity for the Forge UI: edge-to-edge window, light/dark system bar icons and
 * reusable inset helpers for fixed top bars / bottom navigation.
 */
public abstract class ForgeActivity extends AppCompatActivity {

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Only treat the launch Intent as a screenshot override on the first creation;
        // a theme-toggle recreation must honour the persisted preference instead.
        ForgeTheme.apply(this, savedInstanceState == null ? getIntent() : null);
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.setStatusBarContrastEnforced(false);
        window.setNavigationBarContrastEnforced(false);

        // minSdk 29: extend through short edges of a notch/cutout and let the inset
        // helpers below reserve the safe area.
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        window.setAttributes(attributes);
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncSystemBarIcons();
    }

    /** Keeps status/navigation bar icon brightness in sync with the current theme. */
    protected void syncSystemBarIcons() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        boolean dark = ForgeTheme.isDark(this);
        controller.setAppearanceLightStatusBars(!dark);
        controller.setAppearanceLightNavigationBars(!dark);
    }

    private View imeContent;
    private int imeHeight;
    private int statusBarTop;
    private boolean imeVisible;

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        installImeTranslation();
    }

    private void installImeTranslation() {
        View content = findViewById(android.R.id.content);
        if (content == null || content == imeContent) return;
        imeContent = content;
        imeHeight = 0;
        statusBarTop = 0;
        imeVisible = false;
        content.setTranslationY(0f);
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()
                    | WindowInsetsCompat.Type.displayCutout());
            imeHeight = Math.max(0, ime.bottom);
            statusBarTop = Math.max(0, bars.top);
            imeVisible = imeHeight > 0;
            updateImeTranslation(view);
            return windowInsets;
        });
        // The keyboard stays open while the user moves between fields. Recompute the
        // minimal shift on focus changes instead of keeping the previous field's offset.
        content.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) ->
                content.post(() -> updateImeTranslation(content)));
        ViewCompat.requestApplyInsets(content);
    }

    /**
     * Shift only as far as needed to keep the focused input above the keyboard. The focus
     * rectangle is measured without the current translation so repeated insets/focus events
     * cannot accumulate offsets. If the focused field is already visible, the content root
     * stays at zero even though the IME is open.
     */
    private void updateImeTranslation(View content) {
        if (content != imeContent || !imeVisible || imeHeight <= 0) {
            if (content != null) content.setTranslationY(0f);
            return;
        }
        View focus = getCurrentFocus();
        if (focus == null || focus.getRootView() != content.getRootView()) {
            content.setTranslationY(0f);
            return;
        }
        View root = content.getRootView();
        int[] rootLocation = new int[2];
        root.getLocationOnScreen(rootLocation);
        int imeTop = rootLocation[1] + root.getHeight() - imeHeight;
        int[] focusLocation = new int[2];
        focus.getLocationOnScreen(focusLocation);
        float translation = content.getTranslationY();
        float baseTop = focusLocation[1] - translation;
        float baseBottom = baseTop + focus.getHeight();
        float density = getResources().getDisplayMetrics().density;
        float margin = 8f * density;
        float target = imeTop - margin - baseBottom;
        if (target < 0f) {
            target = Math.max(target, -imeHeight);
        } else {
            target = 0f;
        }
        float safeTop = rootLocation[1] + statusBarTop + margin;
        if (baseTop + target < safeTop) target = safeTop - baseTop;
        if (target > 0f) target = 0f;
        if (target < -imeHeight) target = -imeHeight;
        content.setTranslationY(target);
    }

    /** Adds the status bar inset to the view's top padding (keeps its other padding). */
    public void applyTopInset(View view) {
        applyInsets(view, true, false);
    }

    /** Adds the navigation bar inset to the view's bottom padding. */
    public void applyBottomInset(View view) {
        applyInsets(view, false, true);
    }

    /** Adds both system bar insets to the view. */
    public void applyTopBottomInsets(View view) {
        applyInsets(view, true, true);
    }

    private void applyInsets(final View view, final boolean top, final boolean bottom) {
        int[] base = (int[]) view.getTag(R.id.forge_inset_base_padding);
        if (base == null) {
            base = new int[]{
                    view.getPaddingLeft(),
                    view.getPaddingTop(),
                    view.getPaddingRight(),
                    view.getPaddingBottom()
            };
            view.setTag(R.id.forge_inset_base_padding, base);
        }
        final int[] original = base;
        ViewCompat.setOnApplyWindowInsetsListener(view, (target, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            target.setPadding(
                    original[0],
                    original[1] + (top ? bars.top : 0),
                    original[2],
                    original[3] + (bottom ? bars.bottom : 0));
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(view);
    }
}