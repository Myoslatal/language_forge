package io.github.myoslatal.languageforge.uikit;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View.BaseSavedState;
import android.text.TextUtils;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;

/** Field label + AppCompatEditText + optional helper hint. Supports multiline. */
public class ForgeTextField extends LinearLayout {

    private final TextView labelView;
    private final AppCompatEditText editText;
    private final TextView helperView;
    private boolean multiline;

    public ForgeTextField(Context context) {
        this(context, null);
    }

    public ForgeTextField(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ForgeTextField(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.forge_text_field, this, true);
        labelView = findViewById(R.id.forge_field_label);
        editText = findViewById(R.id.forge_field_input);
        helperView = findViewById(R.id.forge_field_helper);
        // The compound view owns its state; child IDs repeat across instances.
        editText.setSaveEnabled(false);

        if (attrs != null) {
            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.ForgeTextField);
            try {
                setLabel(ta.getString(R.styleable.ForgeTextField_forgeLabel));
                setHelper(ta.getString(R.styleable.ForgeTextField_forgeHelper));
                setHint(ta.getString(R.styleable.ForgeTextField_forgeHint));
                String text = ta.getString(R.styleable.ForgeTextField_android_text);
                if (!TextUtils.isEmpty(text)) {
                    editText.setText(text);
                }
                setMultiline(ta.getBoolean(R.styleable.ForgeTextField_forgeMultiline, false));
            } finally {
                ta.recycle();
            }
        }
    }

    public AppCompatEditText getEditText() {
        return editText;
    }

    public TextView getLabelView() {
        return labelView;
    }

    public TextView getHelperView() {
        return helperView;
    }

    public void setLabel(CharSequence label) {
        labelView.setText(label);
        labelView.setVisibility(TextUtils.isEmpty(label) ? GONE : VISIBLE);
    }

    public void setHelper(CharSequence helper) {
        helperView.setText(helper);
        helperView.setVisibility(TextUtils.isEmpty(helper) ? GONE : VISIBLE);
    }

    public void setHint(CharSequence hint) {
        editText.setHint(hint);
    }

    public void setText(CharSequence text) {
        editText.setText(text);
    }

    public CharSequence getText() {
        return editText.getText();
    }

    public boolean isMultiline() {
        return multiline;
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState state = new SavedState(superState);
        CharSequence text = editText.getText();
        state.text = text == null ? null : text.toString();
        state.selectionStart = editText.getSelectionStart();
        state.selectionEnd = editText.getSelectionEnd();
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
        editText.setText(saved.text);
        if (saved.selectionStart >= 0 && saved.selectionEnd >= 0) {
            int length = editText.length();
            editText.setSelection(Math.min(saved.selectionStart, length),
                    Math.min(saved.selectionEnd, length));
        }
    }

    public static class SavedState extends BaseSavedState {
        String text;
        int selectionStart = -1;
        int selectionEnd = -1;

        SavedState(Parcelable superState) {
            super(superState);
        }

        SavedState(Parcel in) {
            super(in);
            text = in.readString();
            selectionStart = in.readInt();
            selectionEnd = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeString(text);
            out.writeInt(selectionStart);
            out.writeInt(selectionEnd);
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

    public void setMultiline(boolean multiline) {
        this.multiline = multiline;
        editText.setSingleLine(!multiline);
        if (multiline) {
            editText.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            editText.setGravity(Gravity.TOP | Gravity.START);
            editText.setMinHeight(ForgeUi.dp(getContext(), 56));
            editText.setMaxLines(8);
            editText.setHorizontallyScrolling(false);
            editText.setLineSpacing(0f, 1.6f);
        } else {
            editText.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            editText.setMinHeight(ForgeUi.dp(getContext(), 32));
        }
    }
}