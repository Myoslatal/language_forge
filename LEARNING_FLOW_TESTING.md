# 学习流程：默认第一题 + 顺序推进 — QA

Date: 2026-11-23 (Asia/Shanghai)
Build: Android debug APK + SDL2 desktop (Xvfb :138, 1280x860)
HTML: NostongueForge_v1.3.1.html unchanged

## 需求
学习不再手动选择题目：进入学习默认从第一题开始；做完一题按顺序进入下一题；最后一题完成后进入本课完成页；再次进入从第一题开始。

## 行为
- 单课程：进入学习直接显示第 1 题，不再出现课程/题目列表。
- 多课程：只显示课程列表（必须选课程），点击课程直接进入该课程第 1 题。
- 空课程：显示「该课程还没有题目」+ 返回。
- 推进：每题「下一题」按 questions 顺序进入下一题；最后一题显示「完成」并进入完成页；完成页可返回（Android 单课程返回项目列表 / 多课程返回课程列表；桌面返回项目结构）。
- 再次进入学习：重新从第 1 题开始，答题状态重置。
- Android 题目副标题显示「第 x / n 题」。

## 证据
Android（3 题夹具：choice-img-1 → card-img-1 → cloze-1）：
- 直接第一题（第 1 / 3 题）：/tmp/android-seq-first.png
- 下一题进入卡片（第 2 / 3 题）：/tmp/android-seq-cloze.png
- cloze 完成 → 完成页：/tmp/android-seq-correct.png、/tmp/android-seq-complete.png
- 返回项目列表后再次进入从第 1 题开始：/tmp/android-seq-reenter2.png

桌面（同一 3 题夹具）：
- 直接第一题：/tmp/desk-seq-first.png
- 下一题进入卡片：/tmp/desk-seq-card.png
- 完成页：/tmp/desk-seq-complete4.png
- 再次进入从第 1 题开始：/tmp/desk-seq-again2.png
- 多课程：显示课程列表，点击课程直接第一题：/tmp/desk-multi-courses.png、/tmp/desk-multi-first.png

## 代码改动
- Android LearningActivity：showCourses 单课程直接 startCourse（第一题）；多课程点课程直接第一题；新增 startCourse/showComplete/hasMultipleCourses；nextQuestion 末尾进入完成页；goBack 从题目返回课程列表或退出；移除旧的题目列表（showQuestions）。
- 桌面 renderLearning：单课程自动设 learningQuestionId=第一题；多课程列表点击后直接第一题；删除旧「选择题目」分支；新增 learningComplete/finishLearning 统一完成状态；listen/choice/type/match/arrange/card/reading/cloze/custom 的最后一题均调用 finishLearning；完成页返回项目结构。
- 所有题型复用统一的清题状态与下一题循环（arrangePool/clozeFilled/match 等按题目 id 重置）。

## 构建/回归
- Android clean assembleDebug SUCCESS；桌面 clean build SUCCESS；桌面 smoke 两次 PASS。
- 14 题全量夹具恢复并验证：进入学习直接显示第 1 题（听音选择）。

## 已知边界
- 多课程目前必须保留课程列表（没有「记住上次课程」）；单课程才会完全跳过列表。
- 桌面学习页副标题未显示「第 x / n 题」（Android 已显示），推进逻辑一致。
- 音频播放、完成页样式未做额外改动。
