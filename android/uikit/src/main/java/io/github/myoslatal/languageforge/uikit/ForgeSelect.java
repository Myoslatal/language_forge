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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;

/** Field label + AppCompatSpinner with the Forge input styling. */
public class ForgeSelect extends LinearLayout {

    private final TextView labelView;
    private final Spinner spinner;
    private ArrayAdapter<String> adapter;

    public ForgeSelect(Context context) {
        this(context, null);
    }

    public ForgeSelect(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ForgeSelect(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.forge_select, this, true);
        labelView = findViewById(R.id.forge_field_label);
        spinner = findViewById(R.id.forge_field_spinner);
        spinner.setPopupBackgroundResource(R.drawable.forge_bg_menu);
        spinner.setSaveEnabled(false);

        if (attrs != null) {
            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.ForgeSelect);
            try {
                setLabel(ta.getString(R.styleable.ForgeSelect_forgeLabel));
                int entriesRes = ta.getResourceId(R.styleable.ForgeSelect_forgeEntries, 0);
                if (entriesRes != 0) {
                    setEntries(getResources().getStringArray(entriesRes));
                }
            } finally {
                ta.recycle();
            }
        }
    }

    public Spinner getSpinner() {
        return spinner;
    }

    public TextView getLabelView() {
        return labelView;
    }

    public void setLabel(CharSequence label) {
        labelView.setText(label);
        labelView.setVisibility(TextUtils.isEmpty(label) ? View.GONE : View.VISIBLE);
    }

    public void setEntries(String[] entries) {
        adapter = new ArrayAdapter<>(getContext(), R.layout.forge_spinner_item, entries);
        adapter.setDropDownViewResource(R.layout.forge_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    public void setEntries(java.util.List<String> entries) {
        setEntries(entries.toArray(new String[0]));
    }

    public void setSelection(int position) {
        spinner.setSelection(position);
    }

    public int getSelection() {
        return spinner.getSelectedItemPosition();
    }

    public Object getSelectedItem() {
        return spinner.getSelectedItem();
    }

    public void setOnItemSelectedListener(AdapterView.OnItemSelectedListener listener) {
        spinner.setOnItemSelectedListener(listener);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState state = new SavedState(superState);
        state.position = spinner.getSelectedItemPosition();
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
        if (saved.position >= 0 && adapter != null
                && saved.position < adapter.getCount()) {
            spinner.setSelection(saved.position);
        }
    }

    public static class SavedState extends BaseSavedState {
        int position = -1;

        SavedState(Parcelable superState) {
            super(superState);
        }

        SavedState(Parcel in) {
            super(in);
            position = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(position);
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