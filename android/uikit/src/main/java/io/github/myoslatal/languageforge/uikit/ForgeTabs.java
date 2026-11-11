package io.github.myoslatal.languageforge.uikit;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Horizontally scrollable tab strip. The container may draw a 1dp bottom divider
 * (panel tabs) or omit it (inline tabs inside a toolbar).
 */
public class ForgeTabs extends HorizontalScrollView {

    public interface OnTabSelectedListener {
        void onTabSelected(ForgeTabs tabs, int index, CharSequence label);
    }

    private final LinearLayout strip;
    private final List<AppCompatTextView> tabs = new ArrayList<>();
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int selectedIndex = -1;
    private boolean showDivider = true;
    private OnTabSelectedListener listener;

    public ForgeTabs(Context context) {
        this(context, null);
    }

    public ForgeTabs(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ForgeTabs(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setFillViewport(false);
        setHorizontalScrollBarEnabled(false);
        setClipToPadding(false);
        setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent));
        dividerPaint.setColor(ContextCompat.getColor(context, R.color.forge_line));

        strip = new LinearLayout(context);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        addView(strip, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        if (attrs != null) {
            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.ForgeTabs);
            try {
                showDivider = ta.getBoolean(R.styleable.ForgeTabs_forgeShowDivider, true);
            } finally {
                ta.recycle();
            }
        }
        setContainerPadding(ForgeUi.dp(context, 9), ForgeUi.dp(context, 7),
                ForgeUi.dp(context, 9), 0);
    }

    public AppCompatTextView addTab(CharSequence label) {
        AppCompatTextView tab = (AppCompatTextView) LayoutInflater.from(getContext())
                .inflate(R.layout.forge_tab, strip, false);
        tab.setText(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        if (!tabs.isEmpty()) {
            lp.setMarginStart(ForgeUi.dp(getContext(), 2));
        }
        final int index = tabs.size();
        tab.setOnClickListener(v -> select(index));
        tabs.add(tab);
        strip.addView(tab, lp);
        if (selectedIndex == -1) {
            select(0);
        } else {
            tab.setSelected(false);
            tab.setContentDescription(label);
        }
        return tab;
    }

    public void select(int index) {
        if (index < 0 || index >= tabs.size()) {
            return;
        }
        int previous = selectedIndex;
        selectedIndex = index;
        for (int i = 0; i < tabs.size(); i++) {
            AppCompatTextView tab = tabs.get(i);
            boolean active = i == index;
            tab.setSelected(active);
            tab.setContentDescription(tab.getText() + (active ? "，已选中" : ""));
            tab.setTypeface(Typeface.create("sans-serif",
                    active ? Typeface.BOLD : Typeface.NORMAL));
        }
        if (listener != null && previous != index) {
            listener.onTabSelected(this, index, tabs.get(index).getText());
        }
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public int getTabCount() {
        return tabs.size();
    }

    public AppCompatTextView getTabAt(int index) {
        return tabs.get(index);
    }

    public void setShowDivider(boolean showDivider) {
        this.showDivider = showDivider;
        invalidate();
    }

    public boolean isShowDivider() {
        return showDivider;
    }

    /** Inline mode: no container bottom edge and no extra strip padding. */
    public void setInline(boolean inline) {
        this.showDivider = !inline;
        setContainerPadding(inline ? 0 : ForgeUi.dp(getContext(), 9),
                inline ? 0 : ForgeUi.dp(getContext(), 7),
                inline ? 0 : ForgeUi.dp(getContext(), 9), 0);
        invalidate();
    }

    public void setOnTabSelectedListener(OnTabSelectedListener listener) {
        this.listener = listener;
    }

    private void setContainerPadding(int left, int top, int right, int bottom) {
        setPadding(left, top, right, bottom);
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (showDivider) {
            int lineHeight = Math.max(1, ForgeUi.dp(getContext(), 1));
            canvas.drawRect(0, getHeight() - lineHeight, getWidth(), getHeight(), dividerPaint);
        }
    }

}