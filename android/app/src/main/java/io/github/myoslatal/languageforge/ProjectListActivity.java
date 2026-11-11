package io.github.myoslatal.languageforge;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.myoslatal.languageforge.uikit.ForgeActivity;
import io.github.myoslatal.languageforge.uikit.ForgeBadge;
import io.github.myoslatal.languageforge.uikit.ForgeButton;
import io.github.myoslatal.languageforge.uikit.ForgeDialog;
import io.github.myoslatal.languageforge.uikit.ForgeMenu;
import io.github.myoslatal.languageforge.uikit.ForgeTextField;
import io.github.myoslatal.languageforge.uikit.ForgeTheme;
import io.github.myoslatal.languageforge.uikit.ForgeToast;

/**
 * Project manager / app launcher. Project metadata lives under the app-specific external
 * files directory; the list is loaded from disk on every Activity creation.
 */
public class ProjectListActivity extends ForgeActivity {

    private static final int REQUEST_EXPORT_TREE = 4201;
    private static final int REQUEST_IMPORT_FILE = 4202;

    private final List<Project> projects = new ArrayList<>();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private ProjectStore store;
    private Project pendingExportProject;
    private String pendingExportName;
    private boolean ioInProgress;

    private LinearLayout pinnedSection;
    private LinearLayout pinnedList;
    private LinearLayout allSection;
    private LinearLayout allList;
    private LinearLayout emptyState;
    private ForgeBadge projectCount;
    private ForgeBadge pinnedCount;
    private ForgeBadge allCount;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_list);

        applyTopInset(findViewById(R.id.topbar));
        applyBottomInset(findViewById(R.id.project_scroll));

        store = new ProjectStore(this);
        bindViews();
        setupTopbar();
        setupNewProjectButtons();
        updateDataPath();
        loadProjects();
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    private void updateDataPath() {
        TextView path = findViewById(R.id.data_path_note);
        path.setText("项目数据：" + store.getRootPath());
    }

    private void loadProjects() {
        projects.clear();
        if (!store.isAvailable()) {
            setNewProjectEnabled(false);
            toast("外部存储不可用，无法读取项目");
            renderProjects();
            return;
        }
        projects.addAll(store.loadProjects());
        setNewProjectEnabled(true);
        renderProjects();
    }

    private void setNewProjectEnabled(boolean enabled) {
        findViewById(R.id.btn_new_project).setEnabled(enabled);
        findViewById(R.id.btn_empty_new).setEnabled(enabled);
        findViewById(R.id.btn_import_project).setEnabled(enabled);
    }

    private void bindViews() {
        pinnedSection = findViewById(R.id.pinned_section);
        pinnedList = findViewById(R.id.pinned_list);
        allSection = findViewById(R.id.all_section);
        allList = findViewById(R.id.all_list);
        emptyState = findViewById(R.id.empty_state);
        projectCount = findViewById(R.id.project_count);
        pinnedCount = findViewById(R.id.pinned_count);
        allCount = findViewById(R.id.all_count);
    }

    private void setupTopbar() {
        findViewById(R.id.btn_theme).setOnClickListener(v -> ForgeTheme.toggle(this));

        View overflow = findViewById(R.id.btn_overflow);
        overflow.setOnClickListener(v -> {
            ForgeMenu menu = new ForgeMenu(this);
            menu.addHeader("Nostongue Forge");
            menu.addItem("组件目录（开发）", R.drawable.forge_ic_menu, null,
                    () -> startActivity(new Intent(this, CatalogActivity.class)));
            menu.addItem("导入项目…", R.drawable.forge_ic_folder, null,
                    this::launchImportPicker);
            menu.addItem("关于", R.drawable.forge_ic_eye, null, this::showAboutDialog);
            menu.show(overflow);
        });
    }

    private void setupNewProjectButtons() {
        View.OnClickListener listener = v -> showNewProjectDialog();
        findViewById(R.id.btn_new_project).setOnClickListener(listener);
        findViewById(R.id.btn_empty_new).setOnClickListener(listener);
        findViewById(R.id.btn_import_project).setOnClickListener(v -> launchImportPicker());
    }

    private void showAboutDialog() {
        ForgeDialog dialog = new ForgeDialog(this);
        dialog.setTitle("关于 Nostongue Forge");
        dialog.setBodyText("Nostongue Forge 是语言关卡制作器的 Android UI Kit 演示。\n\n"
                + "项目元数据保存在 App 专属外部目录：\n" + store.getRootPath()
                + "\n\n编辑器内容尚未落盘。");
        dialog.setPositiveButton("知道了", null);
        dialog.show();
    }

    private void renderProjects() {
        pinnedList.removeAllViews();
        allList.removeAllViews();

        int total = projects.size();
        int pinned = 0;
        for (Project project : projects) {
            if (project.pinned) {
                pinned++;
            }
        }
        int rest = total - pinned;

        projectCount.setText(String.valueOf(total));
        pinnedCount.setText(String.valueOf(pinned));
        allCount.setText(String.valueOf(rest));

        pinnedSection.setVisibility(pinned > 0 ? View.VISIBLE : View.GONE);
        allSection.setVisibility(rest > 0 ? View.VISIBLE : View.GONE);
        emptyState.setVisibility(total == 0 ? View.VISIBLE : View.GONE);

        for (Project project : projects) {
            if (project.pinned) {
                addProjectCard(pinnedList, project);
            }
        }
        for (Project project : projects) {
            if (!project.pinned) {
                addProjectCard(allList, project);
            }
        }
    }

    private void addProjectCard(LinearLayout container, Project project) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_project, container, false);

        TextView name = card.findViewById(R.id.project_name);
        TextView course = card.findViewById(R.id.project_course);
        TextView updated = card.findViewById(R.id.project_updated);
        ForgeBadge lessons = card.findViewById(R.id.project_lessons);
        ForgeBadge questions = card.findViewById(R.id.project_questions);
        ForgeBadge words = card.findViewById(R.id.project_words);
        ForgeButton pin = card.findViewById(R.id.project_pin);
        ForgeButton more = card.findViewById(R.id.project_more);

        name.setText(project.name);
        course.setText(project.courseLine);
        updated.setText(project.updated);
        lessons.setText(project.lessons + " 关");
        questions.setText(project.questions + " 题");
        words.setText(project.words + " 词");
        words.setVisibility(project.words > 0 ? View.VISIBLE : View.GONE);

        pin.setSelected(project.pinned);
        pin.setContentDescription(project.pinned ? "取消置顶" : "置顶");
        pin.setOnClickListener(v -> togglePin(project));
        more.setOnClickListener(v -> showProjectMenu(more, project));
        card.setOnClickListener(v -> showProjectEntryDialog(project));

        container.addView(card);
    }

    private void showProjectEntryDialog(Project project) {
        ForgeDialog dialog = new ForgeDialog(this);
        dialog.setTitle(project.name);
        dialog.setBodyText("选择进入方式。\n\n学习功能暂未实现；可以先编辑项目结构。");
        dialog.setNegativeButton("学习", (d, which) -> toast("学习功能暂未实现"));
        dialog.setPositiveButton("编辑", (d, which) -> {
            Intent intent = new Intent(this, ProjectStructureActivity.class);
            intent.putExtra(ProjectStructureActivity.EXTRA_FOLDER_NAME, project.folderName);
            intent.putExtra(ProjectStructureActivity.EXTRA_PROJECT_NAME, project.name);
            startActivity(intent);
        });
        dialog.show();
    }

    private void showProjectMenu(View anchor, Project project) {
        ForgeMenu menu = new ForgeMenu(this);
        menu.addHeader(project.name);
        menu.addItem("打开项目", () -> toast("打开项目「" + project.name + "」（界面演示）"));
        menu.addItem(project.pinned ? "取消置顶" : "置顶", () -> togglePin(project));
        menu.addItem("导出项目…", () -> showExportDialog(project));
        menu.addDivider();

        SpannableString delete = new SpannableString("删除");
        delete.setSpan(new ForegroundColorSpan(
                        ContextCompat.getColor(this, R.color.forge_bad)),
                0, delete.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        menu.addItem(delete, () -> confirmDelete(project));
        menu.show(anchor);
    }

    private void togglePin(Project project) {
        boolean target = !project.pinned;
        if (!store.setPinned(project, target)) {
            toast("更新置顶失败，请检查项目目录");
            return;
        }
        ProjectStore.sortProjects(projects);
        renderProjects();
        toast(target
                ? "已置顶「" + project.name + "」"
                : "已取消置顶「" + project.name + "」");
    }

    private void showExportDialog(Project project) {
        if (ioInProgress) {
            toast("正在处理文件，请稍候");
            return;
        }
        ForgeDialog dialog = new ForgeDialog(this);
        dialog.setTitle("导出项目");

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        ForgeTextField fileName = new ForgeTextField(this);
        fileName.setLabel("文件名");
        fileName.setText(project.name);
        fileName.setHelper("会自动补 .conlang 后缀");
        body.addView(fileName, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        dialog.setBodyView(body);
        dialog.setNegativeButton("取消", null);
        dialog.setPositiveButton("选择目录", (d, which) -> {
            pendingExportProject = project;
            pendingExportName = ProjectArchive.exportFileName(textOf(fileName), project.name);
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            try {
                startActivityForResult(intent, REQUEST_EXPORT_TREE);
            } catch (ActivityNotFoundException e) {
                pendingExportProject = null;
                pendingExportName = null;
                toast("没有可用的文件选择器");
            }
        });
        dialog.show();
    }

    private void launchImportPicker() {
        if (ioInProgress) {
            toast("正在处理文件，请稍候");
            return;
        }
        if (!store.isAvailable()) {
            toast("外部存储不可用，无法导入");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/zip", "application/x-zip-compressed",
                "application/octet-stream", "*/*"
        });
        try {
            startActivityForResult(intent, REQUEST_IMPORT_FILE);
        } catch (ActivityNotFoundException e) {
            toast("没有可用的文件选择器");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EXPORT_TREE) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                Uri treeUri = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(treeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (Exception ignored) {
                    // The returned grant is still usable for this operation.
                }
                startExport(treeUri);
            } else {
                pendingExportProject = null;
                pendingExportName = null;
            }
        } else if (requestCode == REQUEST_IMPORT_FILE) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                String name = queryDisplayName(uri);
                if (name == null
                        || !name.toLowerCase(Locale.ROOT).endsWith(".conlang")) {
                    toast("请选择 .conlang 文件");
                    return;
                }
                startImport(uri, name);
            }
        }
    }

    private void startExport(Uri treeUri) {
        Project project = pendingExportProject;
        String fileName = pendingExportName;
        pendingExportProject = null;
        pendingExportName = null;
        if (project == null || fileName == null) {
            return;
        }
        ioInProgress = true;
        toast("正在导出 " + fileName);
        ioExecutor.execute(() -> {
            Uri documentUri = null;
            String error = null;
            try {
                Uri parent = DocumentsContract.buildDocumentUriUsingTree(treeUri,
                        DocumentsContract.getTreeDocumentId(treeUri));
                documentUri = DocumentsContract.createDocument(getContentResolver(),
                        parent, "application/octet-stream", fileName);
                if (documentUri == null) {
                    throw new Exception("无法创建 .conlang 文件");
                }
                try (OutputStream out = getContentResolver()
                        .openOutputStream(documentUri, "w")) {
                    if (out == null) {
                        throw new Exception("无法写入文件");
                    }
                    ProjectArchive.exportProject(project.directory, out);
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? "未知错误" : e.getMessage();
                if (documentUri != null) {
                    try {
                        DocumentsContract.deleteDocument(getContentResolver(), documentUri);
                    } catch (Exception ignored) {
                        // Best-effort cleanup of the partial document.
                    }
                }
            }
            final boolean success = error == null;
            final String failure = error;
            runOnUiThread(() -> {
                ioInProgress = false;
                toast(success ? "已导出 " + fileName : "导出失败：" + failure);
            });
        });
    }

    private void startImport(Uri uri, String displayName) {
        ioInProgress = true;
        toast("正在导入…");
        ioExecutor.execute(() -> {
            String importedName = null;
            String error = null;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    throw new Exception("无法读取文件");
                }
                ProjectArchive.ImportResult result =
                        ProjectArchive.importProject(store, in, displayName);
                importedName = result.name;
            } catch (Exception e) {
                error = e.getMessage() == null ? "未知错误" : e.getMessage();
            }
            final String name = importedName;
            final String failure = error;
            runOnUiThread(() -> {
                ioInProgress = false;
                if (name != null) {
                    loadProjects();
                    toast("已导入「" + name + "」");
                } else {
                    toast("导入失败：" + failure);
                }
            });
        });
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && !name.isEmpty()) {
                        return name;
                    }
                }
            }
        } catch (Exception ignored) {
            // Fall back to the URI segment below.
        }
        String last = uri.getLastPathSegment();
        if (last == null) {
            return null;
        }
        int slash = last.lastIndexOf('/');
        return slash >= 0 ? last.substring(slash + 1) : last;
    }

    private void showNewProjectDialog() {
        if (!store.isAvailable()) {
            toast("外部存储不可用，无法创建项目");
            return;
        }
        ForgeDialog dialog = new ForgeDialog(this);
        dialog.setTitle("新建项目");

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        ForgeTextField name = new ForgeTextField(this);
        name.setLabel("项目名称");
        name.setHint("例如：Nortu 自由语 · 入门");
        body.addView(name, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ForgeTextField course = new ForgeTextField(this);
        course.setLabel("课程名（可选）");
        course.setHint("例如：Nortu 自由语 · art-nortu");
        LinearLayout.LayoutParams courseLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        courseLp.topMargin = dp(10);
        body.addView(course, courseLp);

        dialog.setBodyView(body);
        dialog.setNegativeButton("取消", null);
        dialog.setPositiveButton("创建", (d, which) -> {
            String projectName = textOf(name).trim();
            if (projectName.isEmpty()) {
                projectName = "未命名项目";
            }
            String courseLine = textOf(course).trim();
            if (courseLine.isEmpty()) {
                courseLine = "—";
            }
            Project project = store.createProject(projectName, courseLine);
            if (project == null) {
                toast("创建失败：无法写入项目目录");
                return;
            }
            projects.add(project);
            ProjectStore.sortProjects(projects);
            renderProjects();
            toast("已创建「" + project.name + "」：" + project.folderName);
        });
        dialog.show();
    }

    private void confirmDelete(Project project) {
        ForgeDialog dialog = new ForgeDialog(this);
        dialog.setTitle("删除项目");
        dialog.setBodyText("确定删除「" + project.name + "」吗？此操作不可撤销。");
        dialog.setNegativeButton("取消", null);
        dialog.setPositiveButton("删除", (d, which) -> {
            if (!store.deleteProject(project)) {
                toast("删除失败：无法移除项目目录");
                return;
            }
            projects.remove(project);
            renderProjects();
            toast("已删除「" + project.name + "」：" + project.folderName);
        });
        dialog.show();
    }

    private String textOf(ForgeTextField field) {
        CharSequence value = field.getText();
        return value == null ? "" : value.toString();
    }

    private void toast(CharSequence message) {
        // No fixed bottom navigation on this screen; keep the toast above the nav inset.
        ForgeToast.show(this, message, 24);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}