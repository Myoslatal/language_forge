package io.github.myoslatal.languageforge.uikit;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View.BaseSavedState;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;

/** Whole-row label + SwitchCompat, with the accent checked colour. */
public class ForgeSwitchRow extends LinearLayout {

    private final TextView labelView;
    private final SwitchCompat switchView;

    public ForgeSwitchRow(Context context) {
        this(context, null);
    }

    public ForgeSwitchRow(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ForgeSwitchRow(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(HORIZONTAL);
        setGravity(android.view.Gravity.CENTER_VERTICAL);
        setPadding(0, ForgeUi.dp(context, 4), 0, ForgeUi.dp(context, 4));
        LayoutInflater.from(context).inflate(R.layout.forge_switch_row, this, true);
        labelView = findViewById(R.id.forge_field_label);
        switchView = findViewById(R.id.forge_switch);
        switchView.setSaveEnabled(false);

        if (attrs != null) {
            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.ForgeSwitchRow);
            try {
                setLabel(ta.getString(R.styleable.ForgeSwitchRow_forgeLabel));
                switchView.setChecked(ta.getBoolean(R.styleable.ForgeSwitchRow_android_checked, false));
            } finally {
                ta.recycle();
            }
        }

        setClickable(true);
        setFocusable(true);
        setOnClickListener(v -> switchView.toggle());
    }

    public TextView getLabelView() {
        return labelView;
    }

    public SwitchCompat getSwitch() {
        return switchView;
    }

    public void setLabel(CharSequence label) {
        labelView.setText(label);
        labelView.setVisibility(TextUtils.isEmpty(label) ? View.GONE : View.VISIBLE);
    }

    public boolean isChecked() {
        return switchView.isChecked();
    }

    public void setChecked(boolean checked) {
        switchView.setChecked(checked);
    }

    public void setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener listener) {
        switchView.setOnCheckedChangeListener(listener);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState state = new SavedState(superState);
        state.checked = switchView.isChecked();
        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState saved = (SavedState) state;
        super.onRestoreInstanceState(saved.getSuperState());
        switchView.setChecked(saved.checked);
    }

    public static class SavedState extends BaseSavedState {
        boolean checked;

        SavedState(Parcelable superState) {
            super(superState);
        }

        SavedState(Parcel in) {
            super(in);
            checked = in.readInt() != 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(checked ? 1 : 0);
        }

        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}