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