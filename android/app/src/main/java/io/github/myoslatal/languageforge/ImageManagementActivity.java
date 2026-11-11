package io.github.myoslatal.languageforge;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.myoslatal.languageforge.uikit.ForgeActivity;
import io.github.myoslatal.languageforge.uikit.ForgeBadge;
import io.github.myoslatal.languageforge.uikit.ForgeButton;
import io.github.myoslatal.languageforge.uikit.ForgeDialog;
import io.github.myoslatal.languageforge.uikit.ForgeMenu;
import io.github.myoslatal.languageforge.uikit.ForgeTextField;
import io.github.myoslatal.languageforge.uikit.ForgeToast;

/** Project image registry: import, code, references, delete. */
public class ImageManagementActivity extends ForgeActivity {

    public static final String EXTRA_FOLDER_NAME = "folderName";
    private static final int REQUEST_IMPORT_IMAGE = 4701;

    private File projectDirectory;
    private ImageStore store;
    private LinearLayout list;
    private LinearLayout emptyState;
    private ForgeBadge count;
    private ForgeTextField search;
    private String query = "";
    private Map<String, List<String>> references = new HashMap<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String folderName = getIntent().getStringExtra(EXTRA_FOLDER_NAME);
        projectDirectory = ImageStore.resolveProjectDirectory(this, folderName);
        if (projectDirectory == null) {
            toast("项目目录不存在");
            finish();
            return;
        }
        store = new ImageStore(projectDirectory);

        setContentView(R.layout.activity_image_management);
        applyTopInset(findViewById(R.id.image_topbar));
        applyBottomInset(findViewById(R.id.image_scroll));
        list = findViewById(R.id.image_list);
        emptyState = findViewById(R.id.image_empty);
        count = findViewById(R.id.image_count);
        search = findViewById(R.id.image_search);
        ((TextView) findViewById(R.id.image_project))
                .setText(ImageStore.readProjectName(projectDirectory));

        findViewById(R.id.btn_image_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_import_image_asset).setOnClickListener(v -> pickImage());
        search.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s == null ? "" : s.toString();
                render();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
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

    private void reloadReferences() {
        references = new HashMap<>();
        VocabularyStore vocabulary = new VocabularyStore(projectDirectory);
        for (VocabularyEntry entry : vocabulary.entries) {
            if (!VocabularyEntry.TYPE_IMAGE.equals(entry.type)
                    || entry.image == null || entry.image.isEmpty()) {
                continue;
            }
            references.computeIfAbsent(entry.image, key -> new ArrayList<>())
                    .add(vocabulary.displayName(entry));
        }
    }

    private void render() {
        reloadReferences();
        list.removeAllViews();
        List<Item> items = new ArrayList<>();
        for (ProjectImage image : store.images) {
            items.add(new Item(image, store.resolveFile(image.file), true));
        }
        for (File file : store.unregisteredFiles()) {
            items.add(new Item(null, file, false));
        }
        int shown = 0;
        for (Item item : items) {
            if (!matches(item, query)) {
                continue;
            }
            addRow(item);
            shown++;
        }
        count.setText(items.size() + " 张");
        emptyState.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
    }

    private boolean matches(Item item, String rawQuery) {
        String q = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return true;
        }
        String name = item.name().toLowerCase(Locale.ROOT);
        String code = item.code().toLowerCase(Locale.ROOT);
        String file = item.fileName().toLowerCase(Locale.ROOT);
        return name.contains(q) || code.contains(q) || file.contains(q);
    }

    private void addRow(Item item) {
        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_image_asset, list, false);
        ImageView thumb = row.findViewById(R.id.image_thumb);
        TextView name = row.findViewById(R.id.image_name);
        TextView meta = row.findViewById(R.id.image_meta);
        TextView code = row.findViewById(R.id.image_code);
        TextView refs = row.findViewById(R.id.image_refs);
        ForgeButton more = row.findViewById(R.id.image_more);

        Bitmap bitmap = item.file == null ? null
                : MediaFiles.decodeFile(item.file, 256);
        if (bitmap != null) {
            thumb.setImageBitmap(bitmap);
        } else {
            thumb.setImageDrawable(null);
        }
        name.setText(item.name());
        String dimensions = item.width() > 0 && item.height() > 0
                ? item.width() + " × " + item.height() : "尺寸未知";
        String prefix = item.registered ? "" : "未登记/来自词汇 · ";
        meta.setText(prefix + item.fileName() + " · " + dimensions);
        code.setText(item.code().isEmpty() ? "未设置编码" : "编码：" + item.code());

        List<String> names = references.get(item.relativePath());
        if (names == null || names.isEmpty()) {
            refs.setText("未被引用");
        } else {
            String joined = String.join("、", names);
            refs.setText("被 " + names.size() + " 个词汇引用：" + joined);
        }
        row.setOnClickListener(v -> showEditCode(item));
        more.setOnClickListener(v -> showMenu(more, item));
        list.addView(row);
    }

    private void showMenu(View anchor, Item item) {
        ForgeMenu menu = new ForgeMenu(this);
        menu.addHeader(item.name());
        menu.addItem("设置编码", () -> showEditCode(item));
        menu.addDivider();
        menu.addItem("删除", () -> confirmDelete(item));
        menu.show(anchor);
    }

    private void showEditCode(Item item) {
        if (item.file == null || !item.file.isFile()) {
            toast("图片文件不存在");
            return;
        }
        ForgeDialog dialog = new ForgeDialog(this);
        dialog.setTitle("设置编码");
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        ImageView preview = new ImageView(this);
        Bitmap bitmap = MediaFiles.decodeFile(item.file, 512);
        preview.setImageBitmap(bitmap);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackgroundColor(0xFFF1F3F6);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(160));
        body.addView(preview, previewLp);

        ForgeTextField codeField = new ForgeTextField(this);
        codeField.setLabel("图片编码");
        codeField.setHint("可留空");
        codeField.setText(item.code());
        LinearLayout.LayoutParams codeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        codeLp.topMargin = dp(8);
        body.addView(codeField, codeLp);

        dialog.setBodyView(body);
        dialog.setNegativeButton("取消", null);
        dialog.setPositiveButton("保存", (d, which) -> {
            ProjectImage target = item.image;
            if (target == null) {
                target = store.registerFile(item.file);
            }
            if (target == null) {
                toast("图片登记失败");
                return;
            }
            if (!store.setCode(target, textOf(codeField))) {
                toast("保存编码失败");
                return;
            }
            render();
            toast("编码已保存");
        });
        dialog.show();
    }

    private void confirmDelete(Item item) {
        List<String> names = references.get(item.relativePath());
        if (names != null && !names.isEmpty()) {
            toast("被 " + names.size() + " 个词汇引用，请先移除引用");
            return;
        }
        ForgeDialog dialog = new ForgeDialog(this);
        dialog.setTitle("删除图片");
        dialog.setBodyText("确定删除「" + item.name() + "」吗？");
        dialog.setNegativeButton("取消", null);
        dialog.setPositiveButton("删除", (d, which) -> {
            if (item.image != null) {
                if (!store.deleteImage(item.image)) {
                    toast("删除失败");
                    return;
                }
            } else if (item.file != null) {
                MediaFiles.deleteQuietly(item.file);
            }
            render();
            toast("已删除图片");
        });
        dialog.show();
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_IMPORT_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT_IMAGE || resultCode != Activity.RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        String displayName = readDisplayName(uri);
        String extension = ImageStore.extensionOf(displayName);
        if (!ImageStore.isSupportedExtension(extension)) {
            toast("仅支持 PNG/JPG/JPEG/WebP/GIF");
            return;
        }
        File cache = null;
        try {
            cache = MediaFiles.copyUriToCache(this, getContentResolver(), uri);
            ProjectImage image = store.importImage(cache, displayName);
            if (image == null) {
                toast("图片无法解码，已拒绝");
            } else {
                render();
                toast("已导入图片：" + image.name);
            }
        } catch (Exception e) {
            toast("图片导入失败");
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
        return path == null ? "image.png" : path;
    }

    private String textOf(ForgeTextField field) {
        CharSequence value = field.getText();
        return value == null ? "" : value.toString();
    }

    private void toast(CharSequence message) {
        ForgeToast.show(this, message, 24);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class Item {
        final ProjectImage image;
        final File file;
        final boolean registered;

        Item(ProjectImage image, File file, boolean registered) {
            this.image = image;
            this.file = file;
            this.registered = registered;
        }

        String name() {
            if (image != null) {
                return image.name;
            }
            return file == null ? "图片" : file.getName();
        }

        String code() {
            return image == null ? "" : image.code;
        }

        String relativePath() {
            if (image != null) {
                return image.file;
            }
            return file == null ? "" : "resources/images/" + file.getName();
        }

        String fileName() {
            return file == null ? "" : file.getName();
        }

        int width() {
            return image == null ? 0 : image.width;
        }

        int height() {
            return image == null ? 0 : image.height;
        }
    }
}