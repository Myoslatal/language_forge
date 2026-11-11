package io.github.myoslatal.languageforge.uikit;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * 56dp bottom navigation with four (or more) icon+label items. The selected item uses
 * the accent colour on an accentSoft background; tapping selects without navigating.
 */
public class ForgeBottomNav extends LinearLayout {

    public interface OnItemSelectedListener {
        void onItemSelected(ForgeBottomNav nav, int index, CharSequence label);
    }

    private final LinearLayout row;
    private final List<LinearLayout> items = new ArrayList<>();
    private final List<CharSequence> labels = new ArrayList<>();
    private int selectedIndex = -1;
    private OnItemSelectedListener listener;

    public ForgeBottomNav(Context context) {
        this(context, null);
    }

    public ForgeBottomNav(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ForgeBottomNav(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        setBackgroundResource(R.drawable.forge_bg_bottomnav);

        row = new LinearLayout(context);
        row.setId(R.id.forge_bottomnav_row);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        addView(row, new LayoutParams(LayoutParams.MATCH_PARENT,
                ForgeUi.dp(context, 56)));
    }

    public LinearLayout addItem(@DrawableRes int iconRes, CharSequence label) {
        Context context = getContext();
        final int index = items.size();

        LinearLayout item = new LinearLayout(context);
        item.setOrientation(VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        item.setFocusable(true);
        item.setPadding(0, ForgeUi.dp(context, 6), 0, ForgeUi.dp(context, 4));
        Drawable content = ForgeUi.drawable(context, R.drawable.forge_bg_nav_item);
        ColorStateList rippleColor = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.forge_accent_soft));
        item.setBackground(new RippleDrawable(rippleColor, content, null));

        LayoutInflater.from(context).inflate(R.layout.forge_bottom_nav_item, item, true);
        ImageView icon = item.findViewById(R.id.forge_nav_icon);
        icon.setImageResource(iconRes);
        ImageViewCompat.setImageTintList(icon,
                ForgeUi.colorStateList(context, R.color.forge_nav_icon));

        TextView labelView = item.findViewById(R.id.forge_nav_label);
        labelView.setText(label);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        labelView.setTextColor(ForgeUi.colorStateList(context, R.color.forge_nav_text));

        item.setOnClickListener(v -> setSelectedIndex(index));
        item.setContentDescription(label);
        items.add(item);
        labels.add(label);
        row.addView(item, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));

        if (selectedIndex == -1) {
            setSelectedIndex(0);
        }
        return item;
    }

    public int getItemCount() {
        return items.size();
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public View getItemView(int index) {
        return items.get(index);
    }

    public void setSelectedIndex(int index) {
        if (index < 0 || index >= items.size()) {
            return;
        }
        int previous = selectedIndex;
        selectedIndex = index;
        for (int i = 0; i < items.size(); i++) {
            boolean active = i == index;
            items.get(i).setSelected(active);
            items.get(i).setContentDescription(labels.get(i) + (active ? "，已选中" : ""));
        }
        if (listener != null && previous != index) {
            listener.onItemSelected(this, index, labels.get(index));
        }
    }

    public void setOnItemSelectedListener(OnItemSelectedListener listener) {
        this.listener = listener;
    }
}