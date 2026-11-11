package io.github.myoslatal.languageforge.uikit;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.widget.TextViewCompat;

/**
 * Forge modal dialog: 42% scrim, 9dp panel, panel2 header/footer, scrollable body.
 * Width is min(screen - 20dp, 680dp); the body is capped so the whole dialog stays
 * under ~88% of the screen height.
 */
public class ForgeDialog extends Dialog {

    private final LinearLayout root;
    private final LinearLayout header;
    private final TextView titleView;
    private final ForgeButton closeButton;
    private final MaxHeightScrollView scrollView;
    private final LinearLayout body;
    private final LinearLayout footer;
    private ForgeButton positiveButton;
    private ForgeButton negativeButton;
    private DialogInterface.OnClickListener positiveListener;
    private DialogInterface.OnClickListener negativeListener;

    public ForgeDialog(Context context) {
        super(context);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setCanceledOnTouchOutside(true);

        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.forge_bg_dialog);
        root.setClipToOutline(true);
        root.setPadding(dp(1), dp(1), dp(1), dp(1));

        header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundResource(R.drawable.forge_bg_dialog_header);
        header.setPadding(dp(14), dp(11), dp(11), dp(11));

        titleView = new TextView(context);
        TextViewCompat.setTextAppearance(titleView, R.style.Forge_Text_DialogTitle);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(titleView, titleLp);

        closeButton = new ForgeButton(context);
        closeButton.setVariant(ForgeButton.VARIANT_GHOST);
        closeButton.setIcon(R.drawable.forge_ic_close);
        closeButton.setContentDescription("关闭");
        closeButton.setOnClickListener(v -> dismiss());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeLp.setMarginStart(dp(8));
        header.addView(closeButton, closeLp);

        scrollView = new MaxHeightScrollView(context);
        scrollView.setFillViewport(false);
        scrollView.setClipToPadding(false);
        scrollView.setMaxHeight((int) (context.getResources().getDisplayMetrics().heightPixels
                * 0.6f));

        body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(14), dp(14), dp(14));
        scrollView.addView(body, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        footer.setBackgroundResource(R.drawable.forge_bg_dialog_footer);
        footer.setPadding(dp(14), dp(10), dp(14), dp(10));
        footer.setVisibility(View.GONE);

        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.42f);
        }
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
        if (titleView != null) {
            titleView.setText(title);
        }
    }

    public LinearLayout getBody() {
        return body;
    }

    public TextView getTitleView() {
        return titleView;
    }

    public ForgeDialog setBodyText(CharSequence text) {
        TextView textView = new TextView(getContext());
        TextViewCompat.setTextAppearance(textView, R.style.Forge_Text_DialogBody);
        textView.setText(text);
        setBodyView(textView);
        return this;
    }

    public ForgeDialog setBodyView(View view) {
        body.removeAllViews();
        body.addView(view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return this;
    }

    public ForgeDialog setPositiveButton(CharSequence label,
                                         DialogInterface.OnClickListener listener) {
        if (positiveButton == null) {
            positiveButton = new ForgeButton(getContext());
            positiveButton.setVariant(ForgeButton.VARIANT_PRIMARY);
            positiveButton.setOnClickListener(v -> {
                if (positiveListener != null) {
                    positiveListener.onClick(this, DialogInterface.BUTTON_POSITIVE);
                }
                dismiss();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(dp(8));
            footer.addView(positiveButton, lp);
        }
        positiveListener = listener;
        positiveButton.setText(label);
        updateFooter();
        return this;
    }

    public ForgeDialog setNegativeButton(CharSequence label,
                                         DialogInterface.OnClickListener listener) {
        if (negativeButton == null) {
            negativeButton = new ForgeButton(getContext());
            negativeButton.setVariant(ForgeButton.VARIANT_DEFAULT);
            negativeButton.setOnClickListener(v -> {
                if (negativeListener != null) {
                    negativeListener.onClick(this, DialogInterface.BUTTON_NEGATIVE);
                }
                dismiss();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            footer.addView(negativeButton, 0, lp);
        }
        negativeListener = listener;
        negativeButton.setText(label);
        updateFooter();
        return this;
    }

    private void updateFooter() {
        boolean visible = positiveButton != null || negativeButton != null;
        footer.setVisibility(visible ? View.VISIBLE : View.GONE);
        updateBodyMaxHeight();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Window window = getWindow();
        if (window == null) {
            return;
        }
        int screenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
        int width = Math.min(screenWidth - dp(20), dp(680));
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.width = width;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.gravity = Gravity.CENTER;
        lp.dimAmount = 0.42f;
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setAttributes(lp);
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        root.post(this::updateBodyMaxHeight);
    }

    private void updateBodyMaxHeight() {
        if (scrollView == null || root == null) {
            return;
        }
        int screenHeight = getContext().getResources().getDisplayMetrics().heightPixels;
        int maxTotal = (int) (screenHeight * 0.88f);
        int fixed = header.getHeight();
        if (footer.getVisibility() == View.VISIBLE) {
            fixed += footer.getHeight();
        }
        fixed += root.getPaddingTop() + root.getPaddingBottom();
        if (fixed <= 0) {
            // Heights are not measured yet; use a conservative estimate.
            fixed = dp(52) + dp(56) + dp(2);
        }
        int maxBody = Math.max(dp(120), maxTotal - fixed);
        scrollView.setMaxHeight(maxBody);
    }

    private int dp(float value) {
        return ForgeUi.dp(getContext(), value);
    }

    /** ScrollView with a hard maximum height, so the dialog can never exceed 88vh. */
    private static final class MaxHeightScrollView extends ScrollView {
        private int maxHeight = Integer.MAX_VALUE;

        MaxHeightScrollView(Context context) {
            super(context);
        }

        void setMaxHeight(int maxHeight) {
            this.maxHeight = maxHeight;
            requestLayout();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int cappedSpec = maxHeight > 0
                    ? MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
                    : heightMeasureSpec;
            super.onMeasure(widthMeasureSpec, cappedSpec);
            if (maxHeight > 0 && getMeasuredHeight() > maxHeight) {
                setMeasuredDimension(getMeasuredWidth(), maxHeight);
            }
        }
    }
}
