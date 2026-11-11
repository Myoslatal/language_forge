package io.github.myoslatal.languageforge;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.Locale;

import io.github.myoslatal.languageforge.uikit.ForgeActivity;
import io.github.myoslatal.languageforge.uikit.ForgeBadge;
import io.github.myoslatal.languageforge.uikit.ForgeButton;
import io.github.myoslatal.languageforge.uikit.ForgeDialog;
import io.github.myoslatal.languageforge.uikit.ForgeMenu;
import io.github.myoslatal.languageforge.uikit.ForgeToast;

/** Project font import/select/delete; only vocabulary content applies the active typeface. */
public class FontManagementActivity extends ForgeActivity {

    public static final String EXTRA_FOLDER_NAME = "folderName";
    private static final int REQUEST_IMPORT_FONT = 4601;

    private File projectDirectory;
    private FontStore store;
    private LinearLayout list;
    private LinearLayout emptyState;
    private ForgeBadge count;
    private ForgeBadge defaultCurrent;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String folderName = getIntent().getStringExtra(EXTRA_FOLDER_NAME);
        projectDirectory = FontStore.resolveProjectDirectory(this, folderName);
        if (projectDirectory == null) {
            toast("项目目录不存在");
            finish();
            return;
        }
        store = new FontStore(projectDirectory);

        setContentView(R.layout.activity_font_management);
        applyTopInset(findViewById(R.id.font_topbar));
        applyBottomInset(findViewById(R.id.font_scroll));
        list = findViewById(R.id.font_list);
        emptyState = findViewById(R.id.font_empty);
        count = findViewById(R.id.font_count);
        defaultCurrent = findViewById(R.id.font_default_current);
        ((TextView) findViewById(R.id.font_project))
                .setText(FontStore.readProjectName(projectDirectory));

        findViewById(R.id.btn_font_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_import_font).setOnClickListener(v -> pickFont());
        findViewById(R.id.font_default_row).setOnClickListener(v -> setActive(""));
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
        count.setText(store.fonts.size() + " 个");
        defaultCurrent.setVisibility(store.activeId.isEmpty() ? View.VISIBLE : View.GONE);
        emptyState.setVisibility(store.fonts.isEmpty() ? View.VISIBLE : View.GONE);
        for (ProjectFont font : store.fonts) {
            addFontRow(font);
        }
    }

    private void addFontRow(ProjectFont font) {
        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_font, list, false);
        TextView name = row.findViewById(R.id.font_name);
        TextView meta = row.findViewById(R.id.font_meta);
        ForgeBadge current = row.findViewById(R.id.font_current);
        ForgeButton more = row.findViewById(R.id.font_more);

        File file = store.resolveFile(font.file);
        String fileName = file == null ? font.file : file.getName();
        long bytes = file == null ? 0L : file.length();
        name.setText(font.name);
        meta.setText(fileName + " · " + formatSize(bytes));
        current.setVisibility(font.id.equals(store.activeId) ? View.VISIBLE : View.GONE);
        row.setOnClickListener(v -> {
            if (!font.id.equals(store.activeId)) {
                setActive(font.id);
            }
        });
        more.setOnClickListener(v -> showFontMenu(more, font));
        list.addView(row);
    }

    private void showFontMenu(View anchor, ProjectFont font) {
        ForgeMenu menu = new ForgeMenu(this);
        menu.addHeader(font.name);
        if (!font.id.equals(store.activeId)) {
            menu.addItem("设为当前", () -> setActive(font.id));
        }
        menu.addDivider();
        menu.addItem("删除", () -> confirmDelete(font));
        menu.show(anchor);
    }

    private void setActive(String id) {
        if (!id.isEmpty()) {
            ProjectFont font = store.findById(id);
            File file = font == null ? null : store.resolveFile(font.file);
            if (file == null || !file.isFile()) {
                toast("字体文件不存在");
                return;
            }
            if (!FontStore.isValidFontFile(file)) {
                toast("字体无法加载");
                return;
            }
            try {
                Typeface.createFromFile(file);
            } catch (Exception e) {
                toast("字体无法加载");
                return;
            }
        }
        if (!store.setActive(id)) {
            toast("保存字体设置失败");
            return;
        }
        render();
        toast("当前字体：" + store.activeFontName());
    }

    private void confirmDelete(ProjectFont font) {
        ForgeDialog dialog = new ForgeDialog(this);
        dialog.setTitle("删除字体");
        dialog.setBodyText("确定删除「" + font.name + "」吗？"
                + (font.id.equals(store.activeId) ? " 当前字体将回退为系统默认。" : ""));
        dialog.setNegativeButton("取消", null);
        dialog.setPositiveButton("删除", (d, which) -> {
            if (store.deleteFont(font)) {
                render();
                toast("已删除字体");
            } else {
                toast("删除失败");
            }
        });
        dialog.show();
    }

    private void pickFont() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_IMPORT_FONT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT_FONT || resultCode != Activity.RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        String displayName = readDisplayName(uri);
        String lower = displayName.toLowerCase(Locale.ROOT);
        String extension = lower.contains(".")
                ? lower.substring(lower.lastIndexOf('.')) : "";
        if (FontStore.isUnsupportedWebFont(extension)) {
            toast("暂不支持 WOFF/WOFF2，请使用 TTF/OTF/TTC");
            return;
        }
        if (!FontStore.isSupportedExtension(extension)) {
            toast("仅支持 TTF/OTF/TTC 字体文件");
            return;
        }
        File cache = null;
        try {
            cache = MediaFiles.copyUriToCache(this, getContentResolver(), uri);
            ProjectFont font = store.importFont(cache, displayName);
            if (font == null) {
                toast("字体无法加载，已拒绝");
            } else {
                render();
                toast("已导入字体：" + font.name);
            }
        } catch (Exception e) {
            toast("字体导入失败");
        } finally {
            MediaFiles.deleteQuietly(cache);
        }
    }

    private String readDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.trim().isEmpty()) {
                    return name.trim();
                }
            }
        } catch (Exception ignored) {
        }
        String path = uri.getLastPathSegment();
        return path == null ? "font.ttf" : path;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024f);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024f * 1024f));
    }

    private void toast(CharSequence message) {
        ForgeToast.show(this, message, 24);
    }
}