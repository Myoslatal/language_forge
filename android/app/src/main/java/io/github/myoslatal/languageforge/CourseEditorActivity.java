package io.github.myoslatal.languageforge;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Locale;

import io.github.myoslatal.languageforge.uikit.ForgeActivity;
import io.github.myoslatal.languageforge.uikit.ForgeBadge;
import io.github.myoslatal.languageforge.uikit.ForgeButton;
import io.github.myoslatal.languageforge.uikit.ForgeDialog;
import io.github.myoslatal.languageforge.uikit.ForgeMenu;
import io.github.myoslatal.languageforge.uikit.ForgeTextField;
import io.github.myoslatal.languageforge.uikit.ForgeToast;

/** Question list editor for one course; question content editing is a later step. */
public class CourseEditorActivity extends ForgeActivity {

    public static final String EXTRA_FOLDER_NAME = "folderName";
    public static final String EXTRA_CHAPTER_ID = "chapterId";
    public static final String EXTRA_UNIT_ID = "unitId";
    public static final String EXTRA_COURSE_ID = "courseId";

    private File projectDirectory;
    private String chapterId;
    private String unitId;
    private String courseId;
    private ProjectStructure structure;
    private ProjectStructure.Chapter chapter;
    private ProjectStructure.Unit unit;
    private ProjectStructure.Course course;
    private LinearLayout questionList;
    private LinearLayout emptyState;
    private ForgeBadge count;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String folderName = getIntent().getStringExtra(EXTRA_FOLDER_NAME);
        chapterId = getIntent().getStringExtra(EXTRA_CHAPTER_ID);
        unitId = getIntent().getStringExtra(EXTRA_UNIT_ID);
        courseId = getIntent().getStringExtra(EXTRA_COURSE_ID);
        File base = getExternalFilesDir(null);
        if (folderName == null || folderName.isEmpty() || base == null
                || chapterId == null || unitId == null || courseId == null) {
            toast("课程不存在");
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
        if (!resolveCourse()) {
            toast("课程不存在");
            finish();
            return;
        }

        setContentView(R.layout.activity_course_editor);
        applyTopInset(findViewById(R.id.course_editor_topbar));
        applyBottomInset(findViewById(R.id.course_scroll));
        bindViews();
        ((TextView) findViewById(R.id.course_editor_name)).setText(course.name);
        ((TextView) findViewById(R.id.course_editor_subtitle)).setText(
                chapter.name + " · " + unit.name);
        findViewById(R.id.btn_course_back).setOnClickListener(v -> finish());
        View.OnClickListener newQuestion = v -> showCreateQuestionDialog();
        findViewById(R.id.btn_new_question).setOnClickListener(newQuestion);
        findViewById(R.id.btn_empty_question).setOnClickListener(newQuestion);
        findViewById(R.id.btn_course_resources).setOnClickListener(v -> openResources());
        render();
    }

    private void openResources() {
        Intent intent = new Intent(this, ResourceManagementActivity.class);
        intent.putExtra(ResourceManagementActivity.EXTRA_FOLDER_NAME,
                projectDirectory.getName());
        intent.putExtra(ResourceManagementActivity.EXTRA_CHAPTER_NAME, chapter.name);
        intent.putExtra(ResourceManagementActivity.EXTRA_UNIT_NAME, unit.name);
        intent.putExtra(ResourceManagementActivity.EXTRA_COURSE_NAME, course.name);
        startActivity(intent);
    }

    private void bindViews() {
        questionList = findViewById(R.id.question_list);
        emptyState = findViewById(R.id.questions_empty);
        count = findViewById(R.id.course_question_count);
    }

    private boolean resolveCourse() {
        chapter = null;
        unit = null;
        course = null;
        for (ProjectStructure.Chapter candidateChapter : structure.chapters) {
            if (!candidateChapter.id.equals(chapterId)) {
                continue;
            }
            chapter = candidateChapter;
            for (ProjectStructure.Unit candidateUnit : candidateChapter.units) {
                if (!candidateUnit.id.equals(unitId)) {
                    continue;
                }
                unit = candidateUnit;
                for (ProjectStructure.Course candidateCourse : candidateUnit.courses) {
                    if (candidateCourse.id.equals(courseId)) {
                        course = candidateCourse;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void render() {
        questionList.removeAllViews();
        count.setText(course.questions.size() + " 道题目");
        emptyState.setVisibility(course.questions.isEmpty() ? View.VISIBLE : View.GONE);
        for (int i = 0; i < course.questions.size(); i++) {
            addQuestionRow(i, course.questions.get(i));
        }
    }

    private void addQuestionRow(int index, ProjectStructure.Question question) {
        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_question, questionList, false);
        TextView number = row.findViewById(R.id.question_index);
        TextView name = row.findViewById(R.id.question_name);
        ForgeButton more = row.findViewById(R.id.question_more);

        number.setText(String.format(Locale.ROOT, "%02d", index + 1));
        name.setText(question.name);
        row.setOnClickListener(v -> toast("题目编辑后续实现"));
        more.setOnClickListener(v -> showQuestionMenu(more, question));
        questionList.addView(row);
    }

    private void showQuestionMenu(View anchor, ProjectStructure.Question question) {
        ForgeMenu menu = new ForgeMenu(this);
        menu.addHeader(question.name);
        menu.addItem("上移", () -> moveQuestion(question, -1));
        menu.addItem("下移", () -> moveQuestion(question, 1));
        menu.addDivider();
        SpannableString delete = new SpannableString("删除");
        delete.setSpan(new ForegroundColorSpan(
                        ContextCompat.getColor(this, R.color.forge_bad)),
                0, delete.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        menu.addItem(delete, () -> confirmDeleteQuestion(question));
        menu.show(anchor);
    }

    private void showCreateQuestionDialog() {
        ForgeDialog dialog = new ForgeDialog(this);
        dialog.setTitle("新建题目");
        ForgeTextField field = new ForgeTextField(this);
        field.setLabel("题目名称");
        field.setHint("例如：第 1 题 · 听音选词");
        dialog.setBodyView(field);
        dialog.setNegativeButton("取消", null);
        dialog.setPositiveButton("创建", (d, which) -> {
            structure.addQuestion(course,
                    ProjectStructure.cleanName(textOf(field), "未命名题目"));
            if (!persist()) {
                return;
            }
            render();
        });
        dialog.show();
    }

    private void moveQuestion(ProjectStructure.Question question, int delta) {
        int index = course.questions.indexOf(question);
        if (delta < 0 && index == 0) {
            toast("已经在最前");
            return;
        }
        if (delta > 0 && index == course.questions.size() - 1) {
            toast("已经在最后");
            return;
        }
        Collections.swap(course.questions, index, index + delta);
        if (!persist()) {
            return;
        }
        render();
    }

    private void confirmDeleteQuestion(ProjectStructure.Question question) {
        ForgeDialog dialog = new ForgeDialog(this);
        dialog.setTitle("删除题目");
        dialog.setBodyText("确定删除「" + question.name + "」吗？");
        dialog.setNegativeButton("取消", null);
        dialog.setPositiveButton("删除", (d, which) -> {
            course.questions.remove(question);
            if (!persist()) {
                return;
            }
            render();
            toast("已删除题目「" + question.name + "」");
        });
        dialog.show();
    }

    private boolean persist() {
        if (structure.save(projectDirectory)) {
            return true;
        }
        toast("保存题目失败");
        structure = ProjectStructure.load(projectDirectory);
        if (!resolveCourse()) {
            toast("课程不存在");
            finish();
            return false;
        }
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