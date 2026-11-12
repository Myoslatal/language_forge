package io.github.myoslatal.languageforge;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.io.File;

import io.github.myoslatal.languageforge.uikit.ForgeActivity;
import io.github.myoslatal.languageforge.uikit.ForgeTextField;
import io.github.myoslatal.languageforge.uikit.ForgeToast;

/** Create/edit one POS entry: name (required), description, code. */
public class PosEditorActivity extends ForgeActivity {

    public static final String EXTRA_FOLDER_NAME = "folderName";
    public static final String EXTRA_ID = "id";

    private PosStore store;
    private String editingId;
    private ForgeTextField nameField;
    private ForgeTextField descriptionField;
    private ForgeTextField codeField;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String folderName = getIntent().getStringExtra(EXTRA_FOLDER_NAME);
        File projectDirectory = PosStore.resolveProjectDirectory(this, folderName);
        if (projectDirectory == null) {
            toast("项目目录不存在");
            finish();
            return;
        }
        store = new PosStore(projectDirectory);
        editingId = getIntent().getStringExtra(EXTRA_ID);
        if (editingId == null) {
            editingId = "";
        }

        setContentView(R.layout.activity_pos_editor);
        applyTopInset(findViewById(R.id.pos_editor_topbar));
        applyBottomInset(findViewById(R.id.pos_editor_scroll));
        nameField = findViewById(R.id.pos_field_name);
        descriptionField = findViewById(R.id.pos_field_description);
        codeField = findViewById(R.id.pos_field_code);

        boolean editing = !editingId.isEmpty();
        ((TextView) findViewById(R.id.pos_editor_title))
                .setText(editing ? "编辑词性" : "新建词性");
        if (editing) {
            PosEntry entry = store.findById(editingId);
            if (entry == null) {
                toast("词性不存在");
                finish();
                return;
            }
            nameField.setText(entry.name);
            descriptionField.setText(entry.description);
            codeField.setText(entry.code);
        }

        findViewById(R.id.btn_pos_editor_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_pos_editor_save).setOnClickListener(v -> save(editing));
    }

    private void save(boolean editing) {
        String name = textOf(nameField);
        String description = textOf(descriptionField);
        String code = textOf(codeField);
        if (name.isEmpty()) {
            toast("名称不能为空");
            return;
        }
        boolean ok;
        if (editing) {
            ok = store.update(editingId, name, description, code);
        } else {
            ok = store.create(name, description, code) != null;
        }
        if (!ok) {
            toast("保存词性失败");
            return;
        }
        toast("词性已保存");
        finish();
    }

    private String textOf(ForgeTextField field) {
        CharSequence value = field.getText();
        return value == null ? "" : value.toString().trim();
    }

    private void toast(CharSequence message) {
        ForgeToast.show(this, message, 24);
    }
}
