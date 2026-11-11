package io.github.myoslatal.languageforge.uikit;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;

/**
 * The single Forge button, with the seven variants used by the HTML editor:
 * default, primary, ghost, on, mini, miniPrimary and danger.
 */
public class ForgeButton extends AppCompatButton {

    public static final int VARIANT_DEFAULT = 0;
    public static final int VARIANT_PRIMARY = 1;
    public static final int VARIANT_GHOST = 2;
    public static final int VARIANT_ON = 3;
    public static final int VARIANT_MINI = 4;
    public static final int VARIANT_MINI_PRIMARY = 5;
    public static final int VARIANT_DANGER = 6;

    private int variant = VARIANT_DEFAULT;
    private int iconRes;
    private int iconSizePx;
    private boolean initialised;

    public ForgeButton(Context context) {
        this(context, null);
    }

    public ForgeButton(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ForgeButton(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        iconSizePx = ForgeUi.dp(context, 15);
        setStateListAnimator(null);
        if (attrs != null) {
            TypedArray ta = context.obtainStyledAttributes(
                    attrs, R.styleable.ForgeButton, defStyleAttr, 0);
            try {
                variant = variantFromName(
                        ta.getString(R.styleable.ForgeButton_forgeVariant));
                iconRes = ta.getResourceId(R.styleable.ForgeButton_forgeIcon, 0);
                iconSizePx = ta.getDimensionPixelSize(
                        R.styleable.ForgeButton_forgeIconSize, iconSizePx);
            } finally {
                ta.recycle();
            }
        }
        initialised = true;
        applyVariant();
    }

    private static int variantFromName(String name) {
        if (name == null) {
            return VARIANT_DEFAULT;
        }
        switch (name) {
            case "primary":
                return VARIANT_PRIMARY;
            case "ghost":
                return VARIANT_GHOST;
            case "on":
                return VARIANT_ON;
            case "mini":
                return VARIANT_MINI;
            case "miniPrimary":
                return VARIANT_MINI_PRIMARY;
            case "danger":
                return VARIANT_DANGER;
            case "default":
            case "normal":
            default:
                return VARIANT_DEFAULT;
        }
    }

    public int getVariant() {
        return variant;
    }

    public void setVariant(int variant) {
        this.variant = variant;
        applyVariant();
    }

    public void setIcon(@DrawableRes int iconRes) {
        this.iconRes = iconRes;
        applyVariant();
    }

    public int getIcon() {
        return iconRes;
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        if (initialised) {
            applyVariant();
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.4f);
    }

    private void applyVariant() {
        Context context = getContext();
        int backgroundRes;
        int textColorRes;
        float textSizeSp;
        int minHeightDp;
        int padH;
        int padV;

        switch (variant) {
            case VARIANT_PRIMARY:
                backgroundRes = R.drawable.forge_bg_button_primary;
                textColorRes = R.color.forge_button_text_primary;
                textSizeSp = 12f;
                minHeightDp = 28;
                padH = 10;
                padV = 6;
                break;
            case VARIANT_GHOST:
                backgroundRes = R.drawable.forge_bg_button_ghost;
                textColorRes = R.color.forge_button_text_muted;
                textSizeSp = 12f;
                minHeightDp = 28;
                padH = 10;
                padV = 6;
                break;
            case VARIANT_ON:
                backgroundRes = R.drawable.forge_bg_button_selected;
                textColorRes = R.color.forge_button_text_accent;
                textSizeSp = 12f;
                minHeightDp = 28;
                padH = 10;
                padV = 6;
                break;
            case VARIANT_MINI:
                backgroundRes = R.drawable.forge_bg_button_mini;
                textColorRes = R.color.forge_button_text_muted;
                textSizeSp = 11f;
                minHeightDp = 20;
                padH = 6;
                padV = 3;
                break;
            case VARIANT_MINI_PRIMARY:
                backgroundRes = R.drawable.forge_bg_button_mini_primary;
                textColorRes = R.color.forge_button_text_accent;
                textSizeSp = 11f;
                minHeightDp = 20;
                padH = 6;
                padV = 3;
                break;
            case VARIANT_DANGER:
                backgroundRes = R.drawable.forge_bg_button;
                textColorRes = R.color.forge_button_text_danger;
                textSizeSp = 12f;
                minHeightDp = 28;
                padH = 10;
                padV = 6;
                break;
            case VARIANT_DEFAULT:
            default:
                backgroundRes = R.drawable.forge_bg_button;
                textColorRes = R.color.forge_button_text;
                textSizeSp = 12f;
                minHeightDp = 28;
                padH = 10;
                padV = 6;
                break;
        }

        boolean iconOnly = iconRes != 0 && TextUtils.isEmpty(getText());
        setBackgroundResource(backgroundRes);
        setGravity(Gravity.CENTER);
        setIncludeFontPadding(false);
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.END);
        setAllCaps(false);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        setMinHeight(ForgeUi.dp(context, minHeightDp));
        setMinWidth(iconOnly ? ForgeUi.dp(context, 28) : 0);

        ColorStateList textColors = ForgeUi.colorStateList(context, textColorRes);
        setTextColor(textColors);

        boolean bold = variant == VARIANT_PRIMARY
                || variant == VARIANT_ON
                || variant == VARIANT_MINI_PRIMARY
                || (isSelected() && variant != VARIANT_MINI);
        setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));

        int horizontal = ForgeUi.dp(context, iconOnly ? 6 : padH);
        int vertical = ForgeUi.dp(context, iconOnly ? 6 : padV);
        setPadding(horizontal, vertical, horizontal, vertical);

        ForgeUi.setCompoundIcon(this, iconRes, iconSizePx, textColors);
    }
}