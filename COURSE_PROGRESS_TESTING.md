# 课程解锁 + 课程级进度 — QA

Date: 2026-11-23 (Asia/Shanghai)
Build: Android debug APK + SDL2 desktop (Xvfb :138, 1280x860)
HTML: NostongueForge_v1.3.1.html unchanged

## 规则
- 课程按当前顺序线性解锁：第一课默认解锁；完成一课后解锁当前顺序中的下一课；已完成课程保持解锁。
- 只保存课程级进度（已解锁/已完成课程 id），不保存题目进度；再次进入学习直接进入第一个「已解锁且未完成」课程的第 1 题。
- 课程 id 创建时即为 UUID（Android ProjectStructure.addCourse / 桌面 newUuid），进度按 id 保存，重命名/排序/增删课程不重生成 id。
- progress.json 与 structure.json 同目录：{"version":1,"unlockedCourseIds":[...],"completedCourseIds":[...]}，写临时文件再 rename。

## 行为验证（桌面 /tmp/progtest，3 课夹具）
- 无 progress.json 时进入学习 → 直接第一课第 1 题（修复了初版「全部完成」页误显示）。
- 做题中按返回 → 模态「退出将丢失当前课程的题目进度，确定退出？」；继续留下保持；确认退出回到项目结构（修复了初版退到课程列表）。
- 完成第一课 → progress.json 写入 C1 completed + C2 unlocked；完成页显示「下一课：第二课」。
- 课程列表：C1 已完成、C2 可进入、C3 🔒 未解锁；点击 C3 toast「完成上一课程后解锁」。
- 完成第二课 → C3 解锁；kill 重启 App 后进入学习直接 C3 第 1 题（持久化）。
- 课程 id 稳定性：重命名 C2、删除 C3、末尾新增 C4 后，C1/C2 仍已完成，新 C4 按前一课完成状态解锁；progress.json 中已删除 C3 的 id 残留但被忽略。
- 证据：/tmp/prog-first2.png、/tmp/prog-exit-confirm.png、/tmp/prog-complete-c1.png、/tmp/prog-lock-list.png、/tmp/prog-lock-toast.png、/tmp/prog-complete-c2.png、/tmp/prog-resume-c3.png、/tmp/prog-stable-entry.png、/tmp/prog-exit-confirmed.png。

## 行为验证（Android，Three-abc 夹具）
- 清空进度后进入学习 → 第一课第 1 题；返回弹出确认对话框（继续留下/退出）。
- 完成第一课 → 设备 progress.json 写入 unlocked [C1,C2] / completed [C1]；完成页「下一课：第二课」「返回」。
- 返回进入课程列表：C1 已完成、C2 可进入、C3 🔒 未解锁；点 C3 toast「完成上一课程后解锁」。
- 完成第二课 → progress.json 解锁 C3；force-stop 重启后进入学习直接 C3 第 1 题。
- 完成 C3 → 全部完成页「全部课程完成」+ 课程列表/返回。
- 证据：/tmp/android-prog-first2.png、/tmp/android-prog-exit.png、/tmp/android-prog-complete-c1.png、/tmp/android-prog-lock-list.png、/tmp/android-prog-lock-toast.png、/tmp/android-prog-c2.png、/tmp/android-prog-c3-restart.png、/tmp/android-prog-all-done.png。

## 实现说明
- Android 新增 CourseProgress（读写/规范化/current/next/complete/save，原子写）；LearningActivity 入口直接 current、课程列表、完成页写进度、返回确认、全部完成页；退出确认后 finish 回项目列表。
- 桌面 App 增加进度字段与 loadCourseProgress/saveCourseProgress/currentCourse/completeCurrentCourse；完成页写入并解锁；课程列表/锁定提示；退出确认；修复无进度文件时误判全部完成、确认退出未真正退出、以及按「前一课已完成」派生解锁（新增到已完成课之后的课程自动解锁）。
- 构造/读取支持：缺文件/损坏进度 → 空集合 + 第一课解锁；未知 id 保留忽略；全部完成时进入完成页；有未解锁课程但无当前课程时显示课程列表（避免误显示全部完成）。

## 构建/回归
- Android clean assembleDebug SUCCESS；桌面 clean build SUCCESS；桌面 smoke 两次 PASS。
- 14 题回归夹具未改动；学习顺序推进行为保持。

## 已知边界
- 中途退出 Android 直接 finish 回项目列表（不再停在课程列表）；如需「退出到课程列表」可后续调整。
- 课程列表入口在完成页/全部完成页（学习入口直接进入当前课程，不显示列表）。
- 桌面未做多课程增删的额外 Android 截图（逻辑同一 CourseProgress 规则）。
- 题目级进度不保存，符合需求；重新进入课程从第 1 题开始。
