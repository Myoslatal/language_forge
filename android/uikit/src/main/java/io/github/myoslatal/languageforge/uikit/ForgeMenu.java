package io.github.myoslatal.languageforge.uikit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.core.widget.TextViewCompat;

/**
 * Anchored popup menu matching the HTML editor menu: 206-280dp wide, 8dp radius,
 * 7dp x 9dp items, uppercase header, right-aligned keyboard shortcuts, dividers and
 * optional check marks. Tapping outside (or back) dismisses it.
 *
 * <p>The menu flips above the anchor when there is not enough room below, clamps
 * horizontally to the window, and caps its height at 70% of the visible frame with
 * scrolling for long menus.</p>
 */
public class ForgeMenu {

    private final Context context;
    private final ScrollView scroller;
    private final LinearLayout content;
    private final PopupWindow popup;
    private final int minWidth;
    private final int maxWidth;

    public ForgeMenu(Context context) {
        this.context = context;
        minWidth = ForgeUi.dp(context, 206);
        maxWidth = ForgeUi.dp(context, 280);

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ForgeUi.dp(context, 5), ForgeUi.dp(context, 5),
                ForgeUi.dp(context, 5), ForgeUi.dp(context, 5));
        content.setMinimumWidth(minWidth);

        scroller = new ScrollView(context);
        scroller.setFillViewport(false);
        scroller.setBackgroundResource(R.drawable.forge_bg_menu);
        scroller.setClipToOutline(true);
        scroller.setVerticalScrollBarEnabled(true);
        scroller.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroller.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        popup = new PopupWindow(scroller, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        popup.setElevation(ForgeUi.dp(context, 12));
    }

    public ForgeMenu addHeader(CharSequence text) {
        TextView header = new TextView(context);
        TextViewCompat.setTextAppearance(header, R.style.Forge_Text_MenuHeader);
        header.setText(text);
        header.setPadding(ForgeUi.dp(context, 9), ForgeUi.dp(context, 6),
                ForgeUi.dp(context, 9), ForgeUi.dp(context, 4));
        content.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return this;
    }

    public ForgeMenu addItem(CharSequence title, Runnable action) {
        return addItem(title, 0, null, false, false, action);
    }

    public ForgeMenu addItem(CharSequence title, CharSequence shortcut, Runnable action) {
        return addItem(title, 0, shortcut, false, false, action);
    }

    public ForgeMenu addItem(CharSequence title, @DrawableRes int iconRes,
                             CharSequence shortcut, Runnable action) {
        return addItem(title, iconRes, shortcut, false, false, action);
    }

    public ForgeMenu addCheckableItem(CharSequence title, boolean checked, Runnable action) {
        return addItem(title, 0, null, true, checked, action);
    }

    private ForgeMenu addItem(CharSequence title, @DrawableRes int iconRes,
                              CharSequence shortcut, boolean checkable, boolean checked,
                              Runnable action) {
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(ForgeUi.dp(context, 9), ForgeUi.dp(context, 7),
                ForgeUi.dp(context, 9), ForgeUi.dp(context, 7));
        item.setMinimumHeight(ForgeUi.dp(context, 32));
        item.setBackgroundResource(R.drawable.forge_bg_menu_item);
        item.setClickable(true);
        item.setFocusable(true);

        if (checkable) {
            TextView tick = new TextView(context);
            tick.setText("✓");
            tick.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            tick.setTextColor(ContextCompat.getColor(context, R.color.forge_accent));
            tick.setGravity(Gravity.CENTER);
            tick.setVisibility(checked ? View.VISIBLE : View.INVISIBLE);
            LinearLayout.LayoutParams tickLp = new LinearLayout.LayoutParams(
                    ForgeUi.dp(context, 12), ViewGroup.LayoutParams.WRAP_CONTENT);
            tickLp.setMarginEnd(ForgeUi.dp(context, 7));
            item.addView(tick, tickLp);
        }

        if (iconRes != 0) {
            ImageView icon = new ImageView(context);
            icon.setImageResource(iconRes);
            ImageViewCompat.setImageTintList(icon,
                    ForgeUi.colorStateList(context, R.color.forge_muted));
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                    ForgeUi.dp(context, 15), ForgeUi.dp(context, 15));
            iconLp.setMarginEnd(ForgeUi.dp(context, 8));
            item.addView(icon, iconLp);
        }

        TextView titleView = new TextView(context);
        TextViewCompat.setTextAppearance(titleView, R.style.Forge_Text_MenuItem);
        titleView.setText(title);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        item.addView(titleView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (shortcut != null && shortcut.length() > 0) {
            TextView shortcutView = new TextView(context);
            TextViewCompat.setTextAppearance(shortcutView, R.style.Forge_Text_Kbd);
            shortcutView.setText(shortcut);
            shortcutView.setTextColor(ContextCompat.getColor(context, R.color.forge_faint));
            LinearLayout.LayoutParams shortcutLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            shortcutLp.setMarginStart(ForgeUi.dp(context, 12));
            item.addView(shortcutView, shortcutLp);
        }

        item.setOnClickListener(v -> {
            dismiss();
            if (action != null) {
                action.run();
            }
        });
        content.addView(item, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return this;
    }

    public ForgeMenu addDivider() {
        View divider = new View(context);
        divider.setBackgroundColor(ContextCompat.getColor(context, R.color.forge_line2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ForgeUi.dp(context, 1));
        lp.setMargins(ForgeUi.dp(context, 2), ForgeUi.dp(context, 4),
                ForgeUi.dp(context, 2), ForgeUi.dp(context, 4));
        content.addView(divider, lp);
        return this;
    }

    public void show(View anchor) {
        if (anchor == null || !anchor.isAttachedToWindow()) {
            return;
        }
        if (popup.isShowing()) {
            popup.dismiss();
        }

        Rect frame = new Rect();
        anchor.getWindowVisibleDisplayFrame(frame);
        if (frame.width() <= 0 || frame.height() <= 0) {
            frame.set(0, 0,
                    context.getResources().getDisplayMetrics().widthPixels,
                    context.getResources().getDisplayMetrics().heightPixels);
        }

        // getWindowVisibleDisplayFrame already excludes the navigation bar; make sure a
        // bottom cutout larger than the nav bar, or left/right/top cutouts, cannot be
        // covered by the popup.
        WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(anchor);
        if (rootInsets != null) {
            Insets navigation = rootInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets cutout = rootInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
            int safeBottom = Math.max(navigation.bottom, cutout.bottom);
            frame.bottom -= Math.max(0, safeBottom - navigation.bottom);
            frame.top = Math.max(frame.top, cutout.top);
            frame.left = Math.max(frame.left, cutout.left);
            frame.right = Math.min(frame.right,
                    context.getResources().getDisplayMetrics().widthPixels - cutout.right);
            if (frame.width() <= 0 || frame.height() <= 0) {
                return;
            }
        }

        int margin = ForgeUi.dp(context, 4);
        int availableWidth = frame.width() - margin * 2;
        if (availableWidth <= 0) {
            return;
        }

        content.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int desiredWidth = Math.max(minWidth, content.getMeasuredWidth());
        int width = Math.min(Math.min(desiredWidth, maxWidth), availableWidth);
        width = Math.max(1, width);

        content.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int desiredHeight = content.getMeasuredHeight();
        int maxHeight = Math.max(1, (int) (frame.height() * 0.70f));

        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int anchorLeft = location[0];
        int anchorTop = location[1];
        int anchorBottom = anchorTop + anchor.getHeight();

        int spaceBelow = frame.bottom - margin - anchorBottom;
        int spaceAbove = anchorTop - margin - frame.top;
        int preferredHeight = Math.min(desiredHeight, maxHeight);

        boolean showAbove;
        if (spaceBelow >= preferredHeight) {
            showAbove = false;
        } else if (spaceAbove >= preferredHeight) {
            showAbove = true;
        } else {
            showAbove = spaceAbove > spaceBelow;
        }

        int availableHeight = Math.max(0, showAbove ? spaceAbove : spaceBelow);
        int height = Math.min(preferredHeight, availableHeight);
        if (height < ForgeUi.dp(context, 48)) {
            int otherHeight = Math.max(0, showAbove ? spaceBelow : spaceAbove);
            if (otherHeight > availableHeight) {
                showAbove = !showAbove;
                availableHeight = otherHeight;
                height = Math.min(preferredHeight, availableHeight);
            }
        }
        if (height <= 0) {
            return;
        }

        int x = Math.max(frame.left + margin,
                Math.min(anchorLeft, frame.right - margin - width));
        int y;
        if (showAbove) {
            y = anchorTop - margin - height;
        } else {
            y = anchorBottom + margin;
        }
        y = Math.max(frame.top + margin, y);
        y = Math.min(y, frame.bottom - margin - height);

        popup.setWidth(width);
        popup.setHeight(height);
        popup.showAtLocation(anchor, Gravity.TOP | Gravity.START, x, y);
    }

    public void dismiss() {
        if (popup.isShowing()) {
            popup.dismiss();
        }
    }

    public boolean isShowing() {
        return popup.isShowing();
    }

    @Nullable
    public PopupWindow getPopupWindow() {
        return popup;
    }
}