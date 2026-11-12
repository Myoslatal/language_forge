package io.github.myoslatal.languageforge;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.io.File;

import io.github.myoslatal.languageforge.uikit.ForgeActivity;
import io.github.myoslatal.languageforge.uikit.ForgeBadge;
import io.github.myoslatal.languageforge.uikit.ForgeButton;
import io.github.myoslatal.languageforge.uikit.ForgeDialog;
import io.github.myoslatal.languageforge.uikit.ForgeMenu;
import io.github.myoslatal.languageforge.uikit.ForgeToast;

/** Project-level part-of-speech list: create/edit/delete name/description/code. */
public class PosManagementActivity extends ForgeActivity {

    public static final String EXTRA_FOLDER_NAME = "folderName";

    private File projectDirectory;
    private PosStore store;
    private LinearLayout list;
    private LinearLayout emptyState;
    private ForgeBadge count;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String folderName = getIntent().getStringExtra(EXTRA_FOLDER_NAME);
        projectDirectory = PosStore.resolveProjectDirectory(this, folderName);
        if (projectDirectory == null) {
            toast("项目目录不存在");
            finish();
            return;
        }
        store = new PosStore(projectDirectory);

        setContentView(R.layout.activity_pos_management);
        applyTopInset(findViewById(R.id.pos_topbar));
        applyBottomInset(findViewById(R.id.pos_scroll));
        list = findViewById(R.id.pos_list);
        emptyState = findViewById(R.id.pos_empty);
        count = findViewById(R.id.pos_count);
        ((TextView) findViewById(R.id.pos_project))
                .setText(PosStore.readProjectName(projectDirectory));

        findViewById(R.id.btn_pos_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_pos_new).setOnClickListener(v -> openEditor(""));
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store != null) {
            store.load();
            render();
        }
    }

    private void render() {
        list.removeAllViews();
        count.setText(store.entries.size() + " 个");
        emptyState.setVisibility(store.entries.isEmpty() ? View.VISIBLE : View.GONE);
        for (PosEntry entry : store.entries) {
            addRow(entry);
        }
    }

    private void addRow(PosEntry entry) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_pos, list, false);
        TextView name = row.findViewById(R.id.pos_name);
        TextView description = row.findViewById(R.id.pos_description);
        ForgeBadge code = row.findViewById(R.id.pos_code_badge);
        ForgeButton more = row.findViewById(R.id.pos_more);
        name.setText(entry.name);
        description.setText(entry.description.isEmpty() ? "无描述" : entry.description);
        code.setText(entry.code.isEmpty() ? "未设置" : entry.code);
        row.setOnClickListener(v -> openEditor(entry.id));
        more.setOnClickListener(v -> showMenu(more, entry));
        list.addView(row);
    }

    private void showMenu(View anchor, PosEntry entry) {
        ForgeMenu menu = new ForgeMenu(this);
        menu.addHeader(entry.name);
        menu.addItem("编辑", () -> openEditor(entry.id));
        menu.addDivider();
        menu.addItem("删除", () -> confirmDelete(entry));
        menu.show(anchor);
    }

    private void confirmDelete(PosEntry entry) {
        ForgeDialog dialog = new ForgeDialog(this);
        dialog.setTitle("删除词性");
        dialog.setBodyText("确定删除「" + entry.name + "」吗？");
        dialog.setNegativeButton("取消", null);
        dialog.setPositiveButton("删除", (d, which) -> {
            if (store.delete(entry.id)) {
                render();
                toast("已删除词性");
            } else {
                toast("删除失败");
            }
        });
        dialog.show();
    }

    private void openEditor(String id) {
        Intent intent = new Intent(this, PosEditorActivity.class);
        intent.putExtra(PosEditorActivity.EXTRA_FOLDER_NAME,
                projectDirectory.getName());
        intent.putExtra(PosEditorActivity.EXTRA_ID, id);
        startActivity(intent);
    }

    private void toast(CharSequence message) {
        ForgeToast.show(this, message, 24);
    }
}
