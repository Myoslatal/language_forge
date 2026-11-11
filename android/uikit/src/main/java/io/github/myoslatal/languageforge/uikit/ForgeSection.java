package io.github.myoslatal.languageforge.uikit;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * Section header (9.5sp bold, letter-spaced, uppercase) plus an optional badge and a
 * content container. This is the grouping primitive used by the gallery.
 */
public class ForgeSection extends LinearLayout {

    private final TextView titleView;
    private final TextView badgeView;
    private final LinearLayout content;
    private boolean redirectChildren;

    public ForgeSection(Context context) {
        this(context, null);
    }

    public ForgeSection(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ForgeSection(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.forge_section, this, true);
        titleView = findViewById(R.id.forge_section_title);
        badgeView = findViewById(R.id.forge_section_badge);
        content = findViewById(R.id.forge_section_content);

        if (attrs != null) {
            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.ForgeSection);
            try {
                setTitle(ta.getString(R.styleable.ForgeSection_forgeTitle));
                setBadge(ta.getString(R.styleable.ForgeSection_forgeBadge));
            } finally {
                ta.recycle();
            }
        }
        redirectChildren = true;
    }

    /** XML children are redirected into the section content container. */
    @Override
    public void addView(View child) {
        if (redirectChildren && content != null) {
            content.addView(child);
        } else {
            super.addView(child);
        }
    }

    @Override
    public void addView(View child, int index) {
        if (redirectChildren && content != null) {
            content.addView(child, index);
        } else {
            super.addView(child, index);
        }
    }

    @Override
    public void addView(View child, ViewGroup.LayoutParams params) {
        if (redirectChildren && content != null) {
            content.addView(child, params);
        } else {
            super.addView(child, params);
        }
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (redirectChildren && content != null) {
            content.addView(child, params);
        } else {
            super.addView(child, index, params);
        }
    }

    public LinearLayout getContent() {
        return content;
    }

    public TextView getTitleView() {
        return titleView;
    }

    public TextView getBadgeView() {
        return badgeView;
    }

    public void setTitle(CharSequence title) {
        titleView.setText(title);
    }

    public void setBadge(CharSequence badge) {
        badgeView.setText(badge);
        badgeView.setVisibility(TextUtils.isEmpty(badge) ? GONE : VISIBLE);
    }
}