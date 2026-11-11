package io.github.myoslatal.languageforge.uikit;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

/**
 * 26dp status strip: dotted / plain items separated by 1dp line2 dividers,
 * plus a flexible spacer. Values use tabular numerals.
 */
public class ForgeStatusBar extends LinearLayout {

    private int itemCount;

    public ForgeStatusBar(Context context) {
        this(context, null);
    }

    public ForgeStatusBar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ForgeStatusBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundResource(R.drawable.forge_bg_statusbar);
        setPadding(ForgeUi.dp(context, 10), 0, ForgeUi.dp(context, 10), 0);
        setMinimumHeight(ForgeUi.dp(context, 26));
    }

    public void addItem(CharSequence label, CharSequence value) {
        addItemInternal(label, value, 0);
    }

    public void addDottedItem(CharSequence label, CharSequence value,
                              @ColorRes int dotColorRes) {
        addItemInternal(label, value, dotColorRes);
    }

    public void addSpacer() {
        View spacer = new View(getContext());
        LayoutParams lp = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
        spacer.setLayoutParams(lp);
        addView(spacer);
    }

    public void clearItems() {
        removeAllViews();
        itemCount = 0;
    }

    private void addItemInternal(CharSequence label, CharSequence value,
                                 @ColorRes int dotColorRes) {
        Context context = getContext();
        if (itemCount > 0) {
            View divider = new View(context);
            divider.setBackgroundColor(ContextCompat.getColor(context, R.color.forge_line2));
            addView(divider, new LayoutParams(ForgeUi.dp(context, 1), LayoutParams.MATCH_PARENT));
        }

        LinearLayout item = new LinearLayout(context);
        item.setOrientation(HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPaddingRelative(
                itemCount == 0 ? ForgeUi.dp(context, 2) : ForgeUi.dp(context, 9),
                0, ForgeUi.dp(context, 9), 0);

        if (dotColorRes != 0) {
            View dot = new View(context);
            GradientDrawable dotDrawable = new GradientDrawable();
            dotDrawable.setShape(GradientDrawable.OVAL);
            dotDrawable.setColor(ContextCompat.getColor(context, dotColorRes));
            dot.setBackground(dotDrawable);
            LayoutParams dotLp = new LayoutParams(ForgeUi.dp(context, 5), ForgeUi.dp(context, 5));
            dotLp.setMarginEnd(ForgeUi.dp(context, 5));
            item.addView(dot, dotLp);
        }

        TextView labelView = new TextView(context);
        TextViewCompat.setTextAppearance(labelView, R.style.Forge_Text_Hint);
        labelView.setText(label);
        item.addView(labelView, new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        if (!android.text.TextUtils.isEmpty(value)) {
            TextView valueView = new TextView(context);
            TextViewCompat.setTextAppearance(valueView, R.style.Forge_Text_Hint);
            valueView.setText(value);
            valueView.setTextColor(ContextCompat.getColor(context, R.color.forge_text));
            valueView.setTypeface(valueView.getTypeface(), android.graphics.Typeface.BOLD);
            valueView.setFontFeatureSettings("tnum");
            LayoutParams valueLp = new LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            valueLp.setMarginStart(ForgeUi.dp(context, 5));
            item.addView(valueView, valueLp);
        }

        addView(item, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        itemCount++;
    }
}