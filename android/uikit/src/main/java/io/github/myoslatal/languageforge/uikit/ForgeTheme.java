package io.github.myoslatal.languageforge.uikit;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Light / dark theme state for the Forge UI kit.
 *
 * <p>The chosen mode is persisted in SharedPreferences. A launch Intent may carry
 * {@code --ez dark true|false} (see {@link #EXTRA_DARK}); that value is used for the
 * current launch only so screenshot tooling can force a mode without touching prefs.</p>
 */
public final class ForgeTheme {

    public static final String EXTRA_DARK = "dark";

    private static final String PREFS_NAME = "forge_theme";
    private static final String KEY_DARK = "dark";

    private ForgeTheme() {
    }

    /** Applies the launch override when present, otherwise the persisted preference. */
    public static void apply(Context context, Intent launchIntent) {
        boolean dark;
        if (launchIntent != null && launchIntent.hasExtra(EXTRA_DARK)) {
            dark = launchIntent.getBooleanExtra(EXTRA_DARK, false);
        } else {
            dark = isDarkStored(context);
        }
        AppCompatDelegate.setDefaultNightMode(
                dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    public static boolean isDark(Context context) {
        int mode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static boolean isDarkStored(Context context) {
        return prefs(context).getBoolean(KEY_DARK, false);
    }

    /** Persists the user's choice and asks AppCompat to recreate visible activities. */
    public static void setDark(Context context, boolean dark) {
        prefs(context).edit().putBoolean(KEY_DARK, dark).apply();
        AppCompatDelegate.setDefaultNightMode(
                dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    public static void toggle(Context context) {
        setDark(context, !isDark(context));
    }

    public static String label(Context context) {
        return isDark(context) ? "深色" : "浅色";
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
