package io.github.myoslatal.languageforge;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import io.github.myoslatal.languageforge.uikit.ForgeBadge;
import io.github.myoslatal.languageforge.uikit.ForgeBottomNav;
import io.github.myoslatal.languageforge.uikit.ForgeCard;
import io.github.myoslatal.languageforge.uikit.ForgeCheckRow;
import io.github.myoslatal.languageforge.uikit.ForgeChip;
import io.github.myoslatal.languageforge.uikit.ForgeDialog;
import io.github.myoslatal.languageforge.uikit.ForgeMenu;
import io.github.myoslatal.languageforge.uikit.ForgeStatusBar;
import io.github.myoslatal.languageforge.uikit.ForgeTabs;
import io.github.myoslatal.languageforge.uikit.ForgeTextField;
import io.github.myoslatal.languageforge.uikit.ForgeTheme;
import io.github.myoslatal.languageforge.uikit.ForgeToast;

/**
 * Component catalog for the Nostongue Forge Android UI kit (development harness only).
 *
 * <p>Non-launcher screen; opened from the project manager's overflow menu.</p>
 *
 * <p>Mobile-first, single scroll column. The top bar and bottom navigation are fixed;
 * both handle their own system-bar insets through {@link io.github.myoslatal.languageforge.uikit.ForgeActivity}.
 * Launch with {@code --ez dark true} to force the dark theme for screenshots.</p>
 */
public class CatalogActivity extends io.github.myoslatal.languageforge.uikit.ForgeActivity {

    private ScrollView contentScroll;
    private ForgeBottomNav bottomNav;
    private int savedScrollY;
    private int savedNavIndex = 1;
    private int savedPanelTab;
    private int savedInlineTab;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        contentScroll = findViewById(R.id.content_scroll);
        bottomNav = findViewById(R.id.bottom_nav);

        if (savedInstanceState != null) {
            savedScrollY = savedInstanceState.getInt("scroll_y", 0);
            savedNavIndex = savedInstanceState.getInt("nav_index", 1);
            savedPanelTab = savedInstanceState.getInt("tab_panel", 0);
            savedInlineTab = savedInstanceState.getInt("tab_inline", 0);
        }

        applyTopInset(findViewById(R.id.topbar));
        applyBottomInset(bottomNav);

        setupTopbar();
        setupTabs();
        setupInputs();
        setupMarkers();
        setupCard();
        setupOverlays();
        setupLists();
        setupStatusBar();
        setupBottomNav();

        contentScroll.post(() -> contentScroll.scrollTo(0, savedScrollY));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (contentScroll != null) {
            outState.putInt("scroll_y", contentScroll.getScrollY());
        }
        if (bottomNav != null) {
            outState.putInt("nav_index", bottomNav.getSelectedIndex());
        }
        ForgeTabs panel = findViewById(R.id.tabs_panel);
        ForgeTabs inline = findViewById(R.id.tabs_inline);
        outState.putInt("tab_panel", Math.max(0, panel.getSelectedIndex()));
        outState.putInt("tab_inline", Math.max(0, inline.getSelectedIndex()));
    }

    private void setupTopbar() {
        demoClick(R.id.btn_undo, "撤销（演示）");
        demoClick(R.id.btn_redo, "重做（演示）");
        demoClick(R.id.btn_open, "打开项目（演示）");
        demoClick(R.id.btn_save, "保存项目（演示）");
        demoClick(R.id.btn_export, "导出（演示）");
        demoClick(R.id.project_chip, "项目重命名（演示）");
        findViewById(R.id.btn_theme).setOnClickListener(v -> ForgeTheme.toggle(this));
    }

    private void setupTabs() {
        ForgeTabs panel = findViewById(R.id.tabs_panel);
        panel.addTab("编辑题目");
        panel.addTab("试做本关");
        panel.addTab("项目设置");
        panel.select(Math.min(savedPanelTab, panel.getTabCount() - 1));

        ForgeTabs inline = findViewById(R.id.tabs_inline);
        inline.setInline(true);
        inline.addTab("编辑题目");
        inline.addTab("试做本关");
        inline.select(Math.min(savedInlineTab, inline.getTabCount() - 1));

        panel.setOnTabSelectedListener(
                (tabs, index, label) -> toast("标签页：" + label));
        inline.setOnTabSelectedListener(
                (tabs, index, label) -> toast("内嵌标签：" + label));
    }

    private void setupInputs() {
        SeekBar volume = findViewById(R.id.seek_volume);
        ForgeBadge volumeValue = findViewById(R.id.volume_value);
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                volumeValue.setText(progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        ForgeTextField search = findViewById(R.id.field_search);
        AppCompatEditText editText = search.getEditText();
        Drawable searchIcon = AppCompatResources.getDrawable(this, R.drawable.forge_ic_search);
        if (searchIcon != null) {
            int size = dp(15);
            searchIcon.setBounds(0, 0, size, size);
            editText.setCompoundDrawablesRelative(searchIcon, null, null, null);
            editText.setCompoundDrawablePadding(dp(6));
            editText.setSupportCompoundDrawablesTintList(
                    ContextCompat.getColorStateList(this, R.color.forge_faint));
        }
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        editText.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    }

    private void setupMarkers() {
        findViewById(R.id.btn_selected_demo).setSelected(true);
        ForgeChip greeting = findViewById(R.id.chip_greeting);
        greeting.setOnCloseClickListener(chip -> {
            toast("移除标签：" + chip.getText());
            ViewParent parent = chip.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(chip);
            }
        });
    }

    private void setupCard() {
        ForgeCard card = findViewById(R.id.card_stem);
        card.setOnActionClickListener(v -> toast("单字段字体设置（演示）"));
    }

    private void setupOverlays() {
        findViewById(R.id.btn_overlay_toast).setOnClickListener(
                v -> ForgeToast.show(this, "已保存项目 · ForgeToast 演示"));
        findViewById(R.id.btn_overlay_dialog).setOnClickListener(v -> showDemoDialog());
        View menuButton = findViewById(R.id.btn_overlay_menu);
        menuButton.setOnClickListener(v -> showDemoMenu(menuButton));
    }

    private void showDemoDialog() {
        ForgeDialog dialog = new ForgeDialog(this);
        dialog.setTitle("示例对话框");

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        TextView intro = new TextView(this);
        TextViewCompat.setTextAppearance(intro, R.style.Forge_Text_DialogBody);
        intro.setText("这是 ForgeDialog 示例：panel2 header / footer、可滚动 body、42% scrim。");
        body.addView(intro, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ForgeTextField fileName = new ForgeTextField(this);
        fileName.setLabel("文件名");
        fileName.setText("Nortu 自由语 · 入门");
        fileName.setHelper("输入示例文本");
        LinearLayout.LayoutParams fileLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        fileLp.topMargin = dp(12);
        body.addView(fileName, fileLp);

        ForgeCheckRow openAfter = new ForgeCheckRow(this);
        openAfter.setLabel("导出后自动打开");
        openAfter.setChecked(true);
        LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        checkLp.topMargin = dp(10);
        body.addView(openAfter, checkLp);

        dialog.setBodyView(body);
        dialog.setNegativeButton("取消", null);
        dialog.setPositiveButton("导出", (d, which) ->
                ForgeToast.show(this, "已导出播放器（演示）"));
        dialog.show();
    }

    private void showDemoMenu(View anchor) {
        ForgeMenu menu = new ForgeMenu(this);
        menu.addHeader("文件");
        menu.addItem("打开项目", R.drawable.forge_ic_folder, "Ctrl+O",
                () -> ForgeToast.show(this, "打开项目（演示）"));
        menu.addItem("保存项目", R.drawable.forge_ic_save, "Ctrl+S",
                () -> ForgeToast.show(this, "保存项目（演示）"));
        menu.addDivider();
        menu.addCheckableItem("播放反馈动画", true,
                () -> ForgeToast.show(this, "切换反馈动画（演示）"));
        menu.addItem("导出播放器", R.drawable.forge_ic_export, "Ctrl+E",
                () -> ForgeToast.show(this, "导出播放器（演示）"));
        menu.show(anchor);
    }

    private void setupLists() {
        LinearLayout units = findViewById(R.id.list_units);
        addUnit(units, "01", "第 1 关 · 问候与身份", "10 题", true);
        addUnit(units, "02", "第 2 关 · 日常寒暄", "8 题", false);
        addUnit(units, "03", "第 3 关 · 把词形和释义配对", "4 题", false);

        LinearLayout questions = findViewById(R.id.list_questions);
        addQuestion(questions, "A", "你好", "选择正确翻译", true, false);
        addQuestion(questions, "B", "谢谢", "选择正确翻译", false, false);
        addQuestion(questions, "C", "再见", "差一点 · 声调", false, true);
        addQuestion(questions, "D", "nız 是什么意思？", "选择正确翻译", false, false);

        LinearLayout dict = findViewById(R.id.list_dict);
        addDict(dict, "nız", "人 者 person · 人格意义上的「人」", "n", "nı z", true);
        addDict(dict, "ursur", "回应 回复 答复 respond reply", "v", "a ru sa ru", false);
        addDict(dict, "ousos", "多样的 diverse 多元的 各种都有的", "o", "u so s", false);
    }

    private void setupStatusBar() {
        ForgeStatusBar statusBar = findViewById(R.id.status_bar_demo);
        statusBar.addDottedItem("尚未保存", "", R.color.forge_faint);
        statusBar.addItem("关卡", "3");
        statusBar.addItem("题目", "12");
        statusBar.addItem("词库", "5314");
        statusBar.addDottedItem("校验", "0", R.color.forge_ok);
        statusBar.addSpacer();
        statusBar.addItem("字体", "2");
    }

    private void setupBottomNav() {
        bottomNav.addItem(R.drawable.forge_ic_menu, "结构");
        bottomNav.addItem(R.drawable.forge_ic_edit, "编辑");
        bottomNav.addItem(R.drawable.forge_ic_eye, "预览");
        bottomNav.addItem(R.drawable.forge_ic_sliders, "设置");
        bottomNav.setSelectedIndex(Math.min(savedNavIndex, bottomNav.getItemCount() - 1));
        bottomNav.setOnItemSelectedListener(
                (nav, index, label) -> toast("底部导航：" + label));
    }

    private void addUnit(LinearLayout parent, String index, String title, String count,
                         boolean selected) {
        View row = LayoutInflater.from(this).inflate(R.layout.row_unit, parent, false);
        ((TextView) row.findViewById(R.id.unit_index)).setText(index);
        ((TextView) row.findViewById(R.id.unit_title)).setText(title);
        ((TextView) row.findViewById(R.id.unit_count)).setText(count);
        row.setSelected(selected);
        parent.addView(row);
    }

    private void addQuestion(LinearLayout parent, String icon, String title, String sub,
                             boolean selected, boolean warning) {
        View row = LayoutInflater.from(this).inflate(R.layout.row_question, parent, false);
        ((TextView) row.findViewById(R.id.question_icon)).setText(icon);
        ((TextView) row.findViewById(R.id.question_title)).setText(title);
        ((TextView) row.findViewById(R.id.question_sub)).setText(sub);
        row.findViewById(R.id.question_warn)
                .setVisibility(warning ? View.VISIBLE : View.GONE);
        row.setSelected(selected);
        parent.addView(row);

        View divider = new View(this);
        divider.setBackgroundColor(ContextCompat.getColor(this, R.color.forge_line2));
        parent.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
    }

    private void addDict(LinearLayout parent, String word, String definition,
                         String type, String pronunciation, boolean selected) {
        View row = LayoutInflater.from(this).inflate(R.layout.row_dict, parent, false);
        ((TextView) row.findViewById(R.id.dict_word)).setText(word);
        ((TextView) row.findViewById(R.id.dict_def)).setText(definition);
        ((TextView) row.findViewById(R.id.dict_type)).setText(type);
        ((TextView) row.findViewById(R.id.dict_pron)).setText(pronunciation);
        CheckBox checkBox = row.findViewById(R.id.dict_check);
        checkBox.setChecked(selected);
        row.setSelected(selected);
        parent.addView(row);
    }

    private void demoClick(int id, String message) {
        View view = findViewById(id);
        if (view != null) {
            view.setOnClickListener(v -> toast(message));
        }
    }

    private void toast(CharSequence message) {
        ForgeToast.show(this, message);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}