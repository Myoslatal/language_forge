package io.github.myoslatal.languageforge.uikit;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

/** Mono 9.5sp badge with neutral / warn / ok / accent tones. */
public class ForgeBadge extends AppCompatTextView {

    public static final int TONE_NEUTRAL = 0;
    public static final int TONE_WARN = 1;
    public static final int TONE_OK = 2;
    public static final int TONE_ACCENT = 3;

    private int tone = TONE_NEUTRAL;

    public ForgeBadge(Context context) {
        this(context, null);
    }

    public ForgeBadge(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ForgeBadge(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setGravity(Gravity.CENTER);
        setIncludeFontPadding(false);
        setSingleLine(true);
        setPadding(ForgeUi.dp(context, 5), ForgeUi.dp(context, 2),
                ForgeUi.dp(context, 5), ForgeUi.dp(context, 2));
        TextViewCompat.setTextAppearance(this, R.style.Forge_Text_Badge);

        if (attrs != null) {
            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.ForgeBadge);
            try {
                tone = ta.getInt(R.styleable.ForgeBadge_forgeTone, TONE_NEUTRAL);
            } finally {
                ta.recycle();
            }
        }
        applyTone();
    }

    public int getTone() {
        return tone;
    }

    public void setTone(int tone) {
        this.tone = tone;
        applyTone();
    }

    private void applyTone() {
        int backgroundRes;
        int textColorRes;
        switch (tone) {
            case TONE_WARN:
                backgroundRes = R.drawable.forge_bg_badge_warn;
                textColorRes = R.color.forge_warn;
                break;
            case TONE_OK:
                backgroundRes = R.drawable.forge_bg_badge_ok;
                textColorRes = R.color.forge_ok;
                break;
            case TONE_ACCENT:
                backgroundRes = R.drawable.forge_bg_badge_accent;
                textColorRes = R.color.forge_accent;
                break;
            case TONE_NEUTRAL:
            default:
                backgroundRes = R.drawable.forge_bg_badge;
                textColorRes = R.color.forge_muted;
                break;
        }
        setBackgroundResource(backgroundRes);
        setTextColor(ContextCompat.getColor(getContext(), textColorRes));
    }
}
