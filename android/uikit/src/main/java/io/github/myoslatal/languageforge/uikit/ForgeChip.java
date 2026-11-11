package io.github.myoslatal.languageforge.uikit;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

/** Rounded 12dp tag chip; optionally closable (tap the trailing ×). */
public class ForgeChip extends AppCompatTextView {

    public interface OnCloseClickListener {
        void onClose(ForgeChip chip);
    }

    private boolean closable;
    private boolean activeState;
    private OnCloseClickListener closeListener;

    public ForgeChip(Context context) {
        this(context, null);
    }

    public ForgeChip(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ForgeChip(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setGravity(Gravity.CENTER);
        setIncludeFontPadding(false);
        setSingleLine(true);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        setPaddingRelative(ForgeUi.dp(context, 7), ForgeUi.dp(context, 2),
                ForgeUi.dp(context, 7), ForgeUi.dp(context, 2));

        boolean active = false;
        if (attrs != null) {
            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.ForgeChip);
            try {
                closable = ta.getBoolean(R.styleable.ForgeChip_forgeClosable, false);
                active = ta.getBoolean(R.styleable.ForgeChip_forgeActive, false);
            } finally {
                ta.recycle();
            }
        }
        setActive(active);
        setClosable(closable);
    }

    public boolean isActive() {
        return activeState;
    }

    public void setActive(boolean active) {
        activeState = active;
        setBackgroundResource(active
                ? R.drawable.forge_bg_chip_active
                : R.drawable.forge_bg_chip);
        setTextColor(ContextCompat.getColor(getContext(),
                active ? R.color.forge_accent : R.color.forge_muted));
    }

    public boolean isClosable() {
        return closable;
    }

    public void setClosable(boolean closable) {
        this.closable = closable;
        setClickable(closable);
        setFocusable(closable);
        if (closable) {
            Drawable close = ForgeUi.drawable(getContext(), R.drawable.forge_ic_close);
            if (close != null) {
                int size = ForgeUi.dp(getContext(), 10);
                close.setBounds(0, 0, size, size);
                setCompoundDrawablesRelative(null, null, close, null);
                setCompoundDrawablePadding(ForgeUi.dp(getContext(), 4));
                setSupportCompoundDrawablesTintList(
                        ForgeUi.colorStateList(getContext(), R.color.forge_button_text_muted));
                setPaddingRelative(ForgeUi.dp(getContext(), 7), ForgeUi.dp(getContext(), 2),
                        ForgeUi.dp(getContext(), 5), ForgeUi.dp(getContext(), 2));
            }
        } else {
            setCompoundDrawablesRelative(null, null, null, null);
            setPaddingRelative(ForgeUi.dp(getContext(), 7), ForgeUi.dp(getContext(), 2),
                    ForgeUi.dp(getContext(), 7), ForgeUi.dp(getContext(), 2));
        }
        setOnTouchListener(closable ? this::onTouchEventInternal : null);
    }

    public void setOnCloseClickListener(OnCloseClickListener listener) {
        this.closeListener = listener;
    }

    private boolean onTouchEventInternal(android.view.View view, MotionEvent event) {
        if (!closable || closeListener == null) {
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            Drawable end = getCompoundDrawablesRelative()[2];
            if (end != null) {
                int hitStart = getWidth() - getPaddingEnd()
                        - end.getBounds().width() - ForgeUi.dp(getContext(), 3);
                if (event.getX() >= hitStart) {
                    closeListener.onClose(this);
                    return true;
                }
            }
        }
        return false;
    }
}