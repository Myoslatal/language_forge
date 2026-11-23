# 答完后显示解析（explain）— QA

Date: 2026-11-24 (Asia/Shanghai)
Build: Android debug APK + SDL2 desktop (Xvfb :138, 1280x860)
HTML: NostongueForge_v1.3.1.html unchanged

## HTML 对齐
HTML 在「检查」后显示反馈块：✓正确/再看看 + 正确答案 + q.explain（多行）。本实现为每道题新增 explain 并显示解析面板。

## 数据
- Android ProjectStructure.Question.explain / 桌面 Question.explain；JSON 键 "explain"（所有题型可写，缺省空串）。
- 旧项目缺省不显示；编辑器保存任意题型均写入。

## 编辑器
- Android：滚动区固定「解析（答完后显示）」多行输入；加载回填、保存所有路径写入。
- 桌面：题目编辑页通用 question_explain 多行输入，保存时写入 editQuestion.explain。
- Android 实测：打开迁移题，解析从「et 是常用问候语，可单独使用。」改为末尾加 TEST，保存后 device structure.json 读回带 TEST 的文本。

## 学习端
- Android：底部栏 explainView（浅色卡片）在答对或答错后显示「解析」+ 文本；换题隐藏。
- 桌面：题目内容上方浮动的「解析」窗口（右下），答对/答错均显示，换题隐藏。
- 答错时现在也显示解析（保留原重试逻辑）；choice/listen/type/reading/arrange/cloze/custom 的答错分支均设置显示。

## 实测证据（桌面 /tmp/expdata、/tmp/expdata2）
- choice 答错：/tmp/exp-choice-wrong2.png
- choice 答对：/tmp/exp-choice-correct.png
- arrange：/tmp/exp-arrange-explain.png
- dialog：/tmp/exp-dialog.png
- reading：/tmp/exp-reading.png
- cloze：/tmp/exp-cloze.png
- custom：/tmp/exp-custom.png

## 实测证据（Android，Nortu 迁移课程 project）
- 答错解析（toast 消失后）：/tmp/android-exp-wrong4.png
- 答对解析 + 下一题：/tmp/android-exp-correct.png
- 编辑器解析字段/保存：/tmp/android-exp-editor2.png；JSON 见上文。

## 迁移数据
- tools/migrate_html_project.py 写入 explain；重新生成 migration/Nortu-Intro-html.conlang 与 generated 项目。
- 10 题中 6 题带 HTML explain：choice/arrange/dialog/reading/cloze/custom；其余为空（不显示）。
- migration/HTML_MIGRATION.md 已将 explain 从未迁移改为已迁移。

## 构建/回归
- Android clean assembleDebug SUCCESS；桌面 clean build SUCCESS；桌面 smoke 两次 PASS。
- 现有学习推进/课程解锁/解析无面板的题型（type/card 本夹具无 explain）行为不变。

## 已知边界
- 桌面解析面板为右下浮动窗口，不是内容区内联；可能遮挡部分空白区域（不遮挡主要按钮）。
- 本夹具 type 题没有 explain；type/card 的解析显示逻辑复用同一通用面板。
- 答错时只显示解析文本，未额外渲染「正确答案」行（HTML 会同时显示正确答案；app 仍可重试并显示绿色对项）。
