package io.github.myoslatal.languageforge;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import io.github.myoslatal.languageforge.uikit.ForgeActivity;
import io.github.myoslatal.languageforge.uikit.ForgeBadge;
import io.github.myoslatal.languageforge.uikit.ForgeButton;
import io.github.myoslatal.languageforge.uikit.ForgeDialog;
import io.github.myoslatal.languageforge.uikit.ForgeMenu;
import io.github.myoslatal.languageforge.uikit.ForgeTextField;
import io.github.myoslatal.languageforge.uikit.ForgeToast;

/** Chapter/unit/course tree editor for one project. */
public class ProjectStructureActivity extends ForgeActivity {

    public static final String EXTRA_FOLDER_NAME = "folderName";
    public static final String EXTRA_PROJECT_NAME = "projectName";

    private File projectDirectory;
    private ProjectStructure structure;
    private LinearLayout chapterList;
    private LinearLayout emptyState;
    private ForgeBadge counts;
    private final Set<String> expandedChapters = new HashSet<>();
    private final Set<String> expandedUnits = new HashSet<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String folderName = getIntent().getStringExtra(EXTRA_FOLDER_NAME);
        String projectName = getIntent().getStringExtra(EXTRA_PROJECT_NAME);
        File base = getExternalFilesDir(null);
        if (folderName == null || folderName.isEmpty() || base == null) {
            toast("项目目录不存在");
            finish();
            return;
        }
        File root = new File(base, "projects");
        File directory = new File(root, folderName);
        if (!isProjectDirectory(root, directory)) {
            toast("项目目录不存在");
            finish();
            return;
        }
        projectDirectory = directory;
        structure = ProjectStructure.load(directory);

        setContentView(R.layout.activity_project_structure);
        applyTopInset(findViewById(R.id.structure_topbar));
        applyBottomInset(findViewById(R.id.structure_scroll));

        bindViews();
        TextView projectNameView = findViewById(R.id.structure_project_name);
        projectNameView.setText(projectName == null || projectName.trim().isEmpty()
                ? folderName : projectName);
        findViewById(R.id.btn_structure_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_new_chapter).setOnClickListener(v -> showCreateChapterDialog());
        render();
    }

    private void bindViews() {
        chapterList = findViewById(R.id.structure_chapter_list);
        emptyState = findViewById(R.id.structure_empty);
        counts = findViewById(R.id.structure_counts);
    }

    private void render() {
        chapterList.removeAllViews();
        int unitCount = structure.unitCount();
        int courseCount = structure.courseCount();
        counts.setText(structure.chapters.size() + " 章 · " + unitCount + " 单元 · "
                + courseCount + " 课程");
        emptyState.setVisibility(structure.chapters.isEmpty() ? View.VISIBLE : View.GONE);
        for (ProjectStructure.Chapter chapter : structure.chapters) {
            addChapterView(chapter);
        }
    }

    private void addChapterView(ProjectStructure.Chapter chapter) {
        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_structure_chapter, chapterList, false);
        TextView name = row.findViewById(R.id.chapter_name);
        ForgeBadge units = row.findViewById(R.id.chapter_units);
        ImageView chevron = row.findViewById(R.id.chapter_chevron);
        LinearLayout body = row.findViewById(R.id.chapter_body);
        TextView empty = row.findViewById(R.id.chapter_empty);
        LinearLayout unitList = row.findViewById(R.id.chapter_unit_list);
        ForgeButton more = row.findViewById(R.id.chapter_more);

        boolean expanded = expandedChapters.contains(chapter.id);
        name.setText(chapter.name);
        units.setText(chapter.units.size() + " 个单元");
        chevron.setImageResource(expanded
                ? R.drawable.forge_ic_chevron_down : R.drawable.forge_ic_chevron_right);
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (expanded) {
            empty.setVisibility(chapter.units.isEmpty() ? View.VISIBLE : View.GONE);
            for (ProjectStructure.Unit unit : chapter.units) {
                addUnitView(chapter, unit, unitList);
            }
        }
        row.setOnClickListener(v -> {
            if (expandedChapters.contains(chapter.id)) {
                expandedChapters.remove(chapter.id);
            } else {
                expandedChapters.add(chapter.id);
            }
            render();
        });
        more.setOnClickListener(v -> showChapterMenu(more, chapter));
        chapterList.addView(row);
    }

    private void addUnitView(ProjectStructure.Chapter chapter, ProjectStructure.Unit unit,
                             LinearLayout container) {
        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_structure_unit, container, false);
        TextView name = row.findViewById(R.id.unit_name);
        ForgeBadge courses = row.findViewById(R.id.unit_courses);
        ImageView chevron = row.findViewById(R.id.unit_chevron);
        LinearLayout body = row.findViewById(R.id.unit_body);
        TextView empty = row.findViewById(R.id.unit_empty);
        LinearLayout courseList = row.findViewById(R.id.unit_course_list);
        ForgeButton more = row.findViewById(R.id.unit_more);

        boolean expanded = expandedUnits.contains(unit.id);
        name.setText(unit.name);
        courses.setText(unit.courses.size() + " 门课程");
        chevron.setImageResource(expanded
                ? R.drawable.forge_ic_chevron_down : R.drawable.forge_ic_chevron_right);
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (expanded) {
            empty.setVisibility(unit.courses.isEmpty() ? View.VISIBLE : View.GONE);
            for (ProjectStructure.Course course : unit.courses) {
                addCourseView(chapter, unit, course, courseList);
            }
        }
        row.setOnClickListener(v -> {
            if (expandedUnits.contains(unit.id)) {
                expandedUnits.remove(unit.id);
            } else {
                expandedUnits.add(unit.id);
            }
            render();
        });
        more.setOnClickListener(v -> showUnitMenu(more, chapter, unit));
        container.addView(row);
    }

    private void addCourseView(ProjectStructure.Chapter chapter, ProjectStructure.Unit unit,
                               ProjectStructure.Course course, LinearLayout container) {
        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_structure_course, container, false);
        TextView name = row.findViewById(R.id.course_name);
        ForgeButton more = row.findViewById(R.id.course_more);
        name.setText(course.name);
        row.setOnClickListener(v -> openCourseEditor(chapter, unit, course));
        more.setOnClickListener(v -> showCourseMenu(more, chapter, unit, course));
        container.addView(row);
    }

    private void showChapterMenu(View anchor, ProjectStructure.Chapter chapter) {
        ForgeMenu menu = new ForgeMenu(this);
        menu.addHeader(chapter.name);
        menu.addItem("新建单元", () -> showCreateUnitDialog(chapter));
        menu.addItem("上移", () -> moveChapter(chapter, -1));
        menu.addItem("下移", () -> moveChapter(chapter, 1));
        menu.addDivider();
        menu.addItem(redDelete(), () -> confirmDeleteChapter(chapter));
        menu.show(anchor);
    }

    private void showUnitMenu(View anchor, ProjectStructure.Chapter chapter,
                              ProjectStructure.Unit unit) {
        ForgeMenu menu = new ForgeMenu(this);
        menu.addHeader(unit.name);
        menu.addItem("新建课程", () -> showCreateCourseDialog(chapter, unit));
        menu.addItem("上移", () -> moveUnit(chapter, unit, -1));
        menu.addItem("下移", () -> moveUnit(chapter, unit, 1));
        menu.addDivider();
        menu.addItem(redDelete(), () -> confirmDeleteUnit(chapter, unit));
        menu.show(anchor);
    }

    private void showCourseMenu(View anchor, ProjectStructure.Chapter chapter,
                                ProjectStructure.Unit unit, ProjectStructure.Course course) {
        ForgeMenu menu = new ForgeMenu(this);
        menu.addHeader(course.name);
        menu.addItem("编辑课程", () -> openCourseEditor(chapter, unit, course));
        menu.addItem("上移", () -> moveCourse(unit, course, -1));
        menu.addItem("下移", () -> moveCourse(unit, course, 1));
        menu.addDivider();
        menu.addItem(redDelete(), () -> confirmDeleteCourse(unit, course));
        menu.show(anchor);
    }

    private void openCourseEditor(ProjectStructure.Chapter chapter,
                                  ProjectStructure.Unit unit, ProjectStructure.Course course) {
        Intent intent = new Intent(this, CourseEditorActivity.class);
        intent.putExtra(CourseEditorActivity.EXTRA_FOLDER_NAME, projectDirectory.getName());
        intent.putExtra(CourseEditorActivity.EXTRA_CHAPTER_ID, chapter.id);
        intent.putExtra(CourseEditorActivity.EXTRA_UNIT_ID, unit.id);
        intent.putExtra(CourseEditorActivity.EXTRA_COURSE_ID, course.id);
        startActivity(intent);
    }

    private CharSequence redDelete() {
        SpannableString delete = new SpannableString("删除");
        delete.setSpan(new ForegroundColorSpan(
                        ContextCompat.getColor(this, R.color.forge_bad)),
                0, delete.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return delete;
    }

    private void showCreateChapterDialog() {
        ForgeDialog dialog = new ForgeDialog(this);
        dialog.setTitle("新建章节");
        ForgeTextField field = new ForgeTextField(this);
        field.setLabel("章节名称");
        field.setHint("例如：第一章 · 基础");
        dialog.setBodyView(field);
        dialog.setNegativeButton("取消", null);
        dialog.setPositiveButton("创建", (d, which) -> {
            ProjectStructure.Chapter chapter = structure.addChapter(
                    ProjectStructure.cleanName(textOf(field), "未命名章节"));
            if (!persist()) {
                return;
            }
            expandedChapters.add(chapter.id);
            render();
        });
        dialog.show();
    }

    private void showCreateUnitDialog(ProjectStructure.Chapter chapter) {
        ForgeDialog dialog = new ForgeDialog(this);
        dialog.setTitle("新建单元");
        ForgeTextField field = new ForgeTextField(this);
        field.setLabel("单元名称");
        field.setHint("例如：第一单元 · 问候");
        dialog.setBodyView(field);
        dialog.setNegativeButton("取消", null);
        dialog.setPositiveButton("创建", (d, which) -> {
            ProjectStructure.Unit unit = structure.addUnit(chapter,
                    ProjectStructure.cleanName(textOf(field), "未命名单元"));
            if (!persist()) {
                return;
            }
            expandedChapters.add(chapter.id);
            render();
        });
        dialog.show();
    }

    private void showCreateCourseDialog(ProjectStructure.Chapter chapter,
                                        ProjectStructure.Unit unit) {
        ForgeDialog dialog = new ForgeDialog(this);
        dialog.setTitle("新建课程");
        ForgeTextField field = new ForgeTextField(this);
        field.setLabel("课程名称");
        field.setHint("例如：第 1 课 · 你好");
        dialog.setBodyView(field);
        dialog.setNegativeButton("取消", null);
        dialog.setPositiveButton("创建", (d, which) -> {
            ProjectStructure.Course course = structure.addCourse(unit,
                    ProjectStructure.cleanName(textOf(field), "未命名课程"));
            if (!persist()) {
                return;
            }
            expandedChapters.add(chapter.id);
            expandedUnits.add(unit.id);
            render();
        });
        dialog.show();
    }

    private void moveChapter(ProjectStructure.Chapter chapter, int delta) {
        int index = structure.chapters.indexOf(chapter);
        if (delta < 0 && index == 0) {
            toast("已经在最前");
            return;
        }
        if (delta > 0 && index == structure.chapters.size() - 1) {
            toast("已经在最后");
            return;
        }
        Collections.swap(structure.chapters, index, index + delta);
        if (!persist()) {
            return;
        }
        render();
    }

    private void moveUnit(ProjectStructure.Chapter chapter, ProjectStructure.Unit unit,
                          int delta) {
        int index = chapter.units.indexOf(unit);
        if (delta < 0 && index == 0) {
            toast("已经在最前");
            return;
        }
        if (delta > 0 && index == chapter.units.size() - 1) {
            toast("已经在最后");
            return;
        }
        Collections.swap(chapter.units, index, index + delta);
        if (!persist()) {
            return;
        }
        render();
    }

    private void moveCourse(ProjectStructure.Unit unit, ProjectStructure.Course course,
                            int delta) {
        int index = unit.courses.indexOf(course);
        if (delta < 0 && index == 0) {
            toast("已经在最前");
            return;
        }
        if (delta > 0 && index == unit.courses.size() - 1) {
            toast("已经在最后");
            return;
        }
        Collections.swap(unit.courses, index, index + delta);
        if (!persist()) {
            return;
        }
        render();
    }

    private void confirmDeleteChapter(ProjectStructure.Chapter chapter) {
        int units = chapter.units.size();
        int courses = ProjectStructure.courseCount(chapter);
        ForgeDialog dialog = new ForgeDialog(this);
        dialog.setTitle("删除章节");
        dialog.setBodyText("确定删除「" + chapter.name + "」吗？\n将同时删除 "
                + units + " 个单元、" + courses + " 门课程。");
        dialog.setNegativeButton("取消", null);
        dialog.setPositiveButton("删除", (d, which) -> {
            structure.chapters.remove(chapter);
            expandedChapters.remove(chapter.id);
            if (!persist()) {
                return;
            }
            render();
            toast("已删除章节「" + chapter.name + "」");
        });
        dialog.show();
    }

    private void confirmDeleteUnit(ProjectStructure.Chapter chapter,
                                   ProjectStructure.Unit unit) {
        int courses = unit.courses.size();
        ForgeDialog dialog = new ForgeDialog(this);
        dialog.setTitle("删除单元");
        dialog.setBodyText("确定删除「" + unit.name + "」吗？\n将同时删除 "
                + courses + " 门课程。");
        dialog.setNegativeButton("取消", null);
        dialog.setPositiveButton("删除", (d, which) -> {
            chapter.units.remove(unit);
            expandedUnits.remove(unit.id);
            if (!persist()) {
                return;
            }
            render();
            toast("已删除单元「" + unit.name + "」");
        });
        dialog.show();
    }

    private void confirmDeleteCourse(ProjectStructure.Unit unit,
                                     ProjectStructure.Course course) {
        ForgeDialog dialog = new ForgeDialog(this);
        dialog.setTitle("删除课程");
        dialog.setBodyText("确定删除「" + course.name + "」吗？");
        dialog.setNegativeButton("取消", null);
        dialog.setPositiveButton("删除", (d, which) -> {
            unit.courses.remove(course);
            if (!persist()) {
                return;
            }
            render();
            toast("已删除课程「" + course.name + "」");
        });
        dialog.show();
    }

    private boolean persist() {
        if (structure.save(projectDirectory)) {
            return true;
        }
        toast("保存项目结构失败");
        structure = ProjectStructure.load(projectDirectory);
        render();
        return false;
    }

    private String textOf(ForgeTextField field) {
        CharSequence value = field.getText();
        return value == null ? "" : value.toString();
    }

    private void toast(CharSequence message) {
        ForgeToast.show(this, message, 24);
    }

    private boolean isProjectDirectory(File root, File directory) {
        if (root == null || directory == null || !directory.isDirectory()) {
            return false;
        }
        try {
            String rootCanonical = root.getCanonicalPath();
            String directoryCanonical = directory.getCanonicalPath();
            File parent = new File(directoryCanonical).getParentFile();
            return parent != null
                    && parent.getCanonicalPath().equals(rootCanonical)
                    && directoryCanonical.startsWith(rootCanonical + File.separator);
        } catch (IOException e) {
            return false;
        }
    }
}