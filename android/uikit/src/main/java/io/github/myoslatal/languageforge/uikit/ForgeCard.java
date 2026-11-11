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

/** Card with a panel2 header (10sp uppercase title) and an 11dp body. */
public class ForgeCard extends LinearLayout {

    private final LinearLayout header;
    private final TextView titleView;
    private final TextView actionView;
    private final LinearLayout body;
    private boolean redirectChildren;

    public ForgeCard(Context context) {
        this(context, null);
    }

    public ForgeCard(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ForgeCard(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        setBackgroundResource(R.drawable.forge_bg_card);
        setClipToOutline(true);
        setPadding(ForgeUi.dp(context, 1), ForgeUi.dp(context, 1),
                ForgeUi.dp(context, 1), ForgeUi.dp(context, 1));

        LayoutInflater.from(context).inflate(R.layout.forge_card, this, true);
        header = findViewById(R.id.forge_card_header);
        titleView = findViewById(R.id.forge_card_title);
        actionView = findViewById(R.id.forge_card_action);
        body = findViewById(R.id.forge_card_body);

        if (attrs != null) {
            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.ForgeCard);
            try {
                setTitle(ta.getString(R.styleable.ForgeCard_forgeTitle));
                setAction(ta.getString(R.styleable.ForgeCard_forgeAction));
            } finally {
                ta.recycle();
            }
        }
        redirectChildren = true;
    }

    /**
     * XML children of a ForgeCard belong in the card body. The compound view inflates its
     * header/body first, then redirects the layout's children into the body so callers can
     * use a plain {@code <ForgeCard> ...children... </ForgeCard>} in XML.
     */
    @Override
    public void addView(View child) {
        if (redirectChildren && body != null) {
            body.addView(child);
        } else {
            super.addView(child);
        }
    }

    @Override
    public void addView(View child, int index) {
        if (redirectChildren && body != null) {
            body.addView(child, index);
        } else {
            super.addView(child, index);
        }
    }

    @Override
    public void addView(View child, ViewGroup.LayoutParams params) {
        if (redirectChildren && body != null) {
            body.addView(child, params);
        } else {
            super.addView(child, params);
        }
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (redirectChildren && body != null) {
            body.addView(child, params);
        } else {
            super.addView(child, index, params);
        }
    }

    public LinearLayout getBody() {
        return body;
    }

    public LinearLayout getHeader() {
        return header;
    }

    public TextView getTitleView() {
        return titleView;
    }

    public TextView getActionView() {
        return actionView;
    }

    public void setTitle(CharSequence title) {
        titleView.setText(title);
        header.setVisibility(TextUtils.isEmpty(title) ? View.GONE : View.VISIBLE);
    }

    public void setAction(CharSequence action) {
        actionView.setText(action);
        actionView.setVisibility(TextUtils.isEmpty(action) ? View.GONE : View.VISIBLE);
    }

    public void setOnActionClickListener(OnClickListener listener) {
        actionView.setOnClickListener(listener);
    }
}