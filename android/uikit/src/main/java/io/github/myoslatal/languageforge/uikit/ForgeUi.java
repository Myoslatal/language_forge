package io.github.myoslatal.languageforge.uikit;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

/** Small internal helpers shared by the Forge widgets. */
final class ForgeUi {

    private ForgeUi() {
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static ColorStateList colorStateList(Context context, @ColorRes int colorRes) {
        return ContextCompat.getColorStateList(context, colorRes);
    }

    static Drawable drawable(Context context, @DrawableRes int drawableRes) {
        return AppCompatResources.getDrawable(context, drawableRes);
    }

    static void setCompoundIcon(TextView view, @DrawableRes int iconRes,
                                int sizePx, ColorStateList tint) {
        if (iconRes == 0) {
            view.setCompoundDrawablesRelative(null, null, null, null);
            return;
        }
        Drawable drawable = drawable(view.getContext(), iconRes);
        if (drawable == null) {
            view.setCompoundDrawablesRelative(null, null, null, null);
            return;
        }
        DrawableCompat.setTintList(drawable, tint);
        drawable.setBounds(0, 0, sizePx, sizePx);
        view.setCompoundDrawablesRelative(drawable, null, null, null);
        view.setCompoundDrawablePadding(dp(view.getContext(), 4));
    }
}