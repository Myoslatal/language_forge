package io.github.myoslatal.languageforge;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Project chapter/unit/course tree, persisted as UTF-8 structure.json. */
final class ProjectStructure {

    static final String FILE_NAME = "structure.json";
    static final String TEMP_NAME = "structure.json.tmp";
    static final int SCHEMA = 1;
    static final int MAX_NAME_LENGTH = 60;
    private static final String TAG = "ProjectStructure";

    final List<Chapter> chapters = new ArrayList<>();

    static ProjectStructure load(File projectDirectory) {
        ProjectStructure structure = new ProjectStructure();
        if (projectDirectory == null) {
            return structure;
        }
        File file = new File(projectDirectory, FILE_NAME);
        if (!file.isFile()) {
            Log.i(TAG, "No structure.json; using empty structure: " + file);
            return structure;
        }
        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(content);
            if (root.optInt("schema", -1) != SCHEMA) {
                Log.w(TAG, "Unsupported structure schema; using empty structure");
                return structure;
            }
            JSONArray chapterArray = root.optJSONArray("chapters");
            if (chapterArray == null) {
                return structure;
            }
            for (int i = 0; i < chapterArray.length(); i++) {
                JSONObject chapterObject = chapterArray.optJSONObject(i);
                if (chapterObject == null) {
                    continue;
                }
                Chapter chapter = new Chapter(
                        cleanId(chapterObject.optString("id")),
                        cleanName(chapterObject.optString("name"), "未命名章节"));
                JSONArray unitArray = chapterObject.optJSONArray("units");
                if (unitArray != null) {
                    for (int j = 0; j < unitArray.length(); j++) {
                        JSONObject unitObject = unitArray.optJSONObject(j);
                        if (unitObject == null) {
                            continue;
                        }
                        Unit unit = new Unit(
                                cleanId(unitObject.optString("id")),
                                cleanName(unitObject.optString("name"), "未命名单元"));
                        JSONArray courseArray = unitObject.optJSONArray("courses");
                        if (courseArray != null) {
                            for (int k = 0; k < courseArray.length(); k++) {
                                JSONObject courseObject = courseArray.optJSONObject(k);
                                if (courseObject == null) {
                                    continue;
                                }
                                Course course = new Course(
                                        cleanId(courseObject.optString("id")),
                                        cleanName(courseObject.optString("name"),
                                                "未命名课程"));
                                JSONArray questionArray =
                                        courseObject.optJSONArray("questions");
                                if (questionArray != null) {
                                    for (int q = 0; q < questionArray.length(); q++) {
                                        JSONObject questionObject =
                                                questionArray.optJSONObject(q);
                                        if (questionObject == null) {
                                            continue;
                                        }
                                        Question question = new Question(
                                                cleanId(questionObject.optString("id")),
                                                cleanName(questionObject.optString("name"),
                                                        "未命名题目"));
                                        String type = questionObject.optString("type", "").trim();
                                        if (!type.isEmpty()) question.type = type;
                                        question.sentence = questionObject.optString("sentence", "");
                                        question.audio = questionObject.optString("audio", "");
                                        question.audioDuration =
                                                Math.max(0, questionObject.optLong("audioDuration", 0));
                                        JSONArray parts = questionObject.optJSONArray("parts");
                                        if (parts != null) {
                                            for (int p = 0; p < parts.length(); p++) {
                                                String part = parts.optString(p, "").trim();
                                                if (!part.isEmpty()) question.parts.add(part);
                                            }
                                        }
                                        JSONArray options = questionObject.optJSONArray("options");
                                        if (options != null) {
                                            for (int o = 0; o < options.length(); o++) {
                                                question.options.add(options.optString(o, ""));
                                            }
                                        }
                                        question.correctIndex =
                                                questionObject.optInt("correctIndex", -1);
                                        question.hintsEnabled =
                                                questionObject.optBoolean("hintsEnabled", true);
                                        JSONArray segments = questionObject.optJSONArray("segments");
                                        if (segments != null) {
                                            for (int s = 0; s < segments.length(); s++) {
                                                JSONObject segment = segments.optJSONObject(s);
                                                if (segment == null) continue;
                                                String kind = segment.optString("kind", "");
                                                if ("word".equals(kind)) {
                                                    String vocabId = segment.optString("vocabId", "");
                                                    if (!vocabId.isEmpty()) {
                                                        question.segments.add(
                                                                Question.Segment.word(vocabId));
                                                    }
                                                } else if ("punct".equals(kind)) {
                                                    String text = segment.optString("text", "");
                                                    if (!text.isEmpty()) {
                                                        question.segments.add(
                                                                Question.Segment.punct(text));
                                                    }
                                                }
                                            }
                                        }
                                        if (question.segments.isEmpty()) {
                                            for (String part : question.parts) {
                                                question.segments.add(Question.Segment.word(part));
                                            }
                                        }
                                        question.audioStart = Math.max(0,
                                                questionObject.optLong("audioStart", 0));
                                        question.audioEnd = Math.max(question.audioStart,
                                                questionObject.optLong("audioEnd",
                                                        question.audioDuration));
                                        course.questions.add(question);
                                    }
                                }
                                unit.courses.add(course);
                            }
                        }
                        chapter.units.add(unit);
                    }
                }
                structure.chapters.add(chapter);
            }
        } catch (IOException | JSONException e) {
            Log.w(TAG, "Could not read structure.json; using empty structure", e);
            return new ProjectStructure();
        }
        return structure;
    }

    boolean save(File projectDirectory) {
        if (projectDirectory == null || !projectDirectory.isDirectory()) {
            return false;
        }
        File temp = new File(projectDirectory, TEMP_NAME);
        File target = new File(projectDirectory, FILE_NAME);
        try {
            JSONObject root = new JSONObject();
            root.put("schema", SCHEMA);
            JSONArray chapterArray = new JSONArray();
            for (Chapter chapter : chapters) {
                JSONObject chapterObject = new JSONObject();
                chapterObject.put("id", chapter.id);
                chapterObject.put("name", chapter.name);
                JSONArray unitArray = new JSONArray();
                for (Unit unit : chapter.units) {
                    JSONObject unitObject = new JSONObject();
                    unitObject.put("id", unit.id);
                    unitObject.put("name", unit.name);
                    JSONArray courseArray = new JSONArray();
                    for (Course course : unit.courses) {
                        JSONObject courseObject = new JSONObject();
                        courseObject.put("id", course.id);
                        courseObject.put("name", course.name);
                        JSONArray questionArray = new JSONArray();
                        for (Question question : course.questions) {
                            JSONObject questionObject = new JSONObject();
                            questionObject.put("id", question.id);
                            questionObject.put("name", question.name);
                            questionObject.put("type", question.type);
                            JSONArray parts = new JSONArray();
                            for (String part : question.parts) parts.put(part);
                            questionObject.put("parts", parts);
                            questionObject.put("sentence", question.sentence);
                            questionObject.put("audio", question.audio);
                            questionObject.put("audioDuration", question.audioDuration);
                            JSONArray options = new JSONArray();
                            for (String option : question.options) options.put(option);
                            questionObject.put("options", options);
                            questionObject.put("correctIndex", question.correctIndex);
                            questionObject.put("hintsEnabled", question.hintsEnabled);
                            JSONArray segments = new JSONArray();
                            for (Question.Segment segment : question.segments) {
                                JSONObject object = new JSONObject();
                                if (segment.word) {
                                    object.put("kind", "word");
                                    object.put("vocabId", segment.vocabId);
                                } else {
                                    object.put("kind", "punct");
                                    object.put("text", segment.text);
                                }
                                segments.put(object);
                            }
                            questionObject.put("segments", segments);
                            JSONArray derivedParts = new JSONArray();
                            for (Question.Segment segment : question.segments) {
                                if (segment.word) derivedParts.put(segment.vocabId);
                            }
                            question.parts.clear();
                            for (Question.Segment segment : question.segments) {
                                if (segment.word) question.parts.add(segment.vocabId);
                            }
                            questionObject.put("parts", derivedParts);
                            questionObject.put("audioStart", question.audioStart);
                            questionObject.put("audioEnd", question.audioEnd);
                            questionArray.put(questionObject);
                        }
                        courseObject.put("questions", questionArray);
                        courseArray.put(courseObject);
                    }
                    unitObject.put("courses", courseArray);
                    unitArray.put(unitObject);
                }
                chapterObject.put("units", unitArray);
                chapterArray.put(chapterObject);
            }
            root.put("chapters", chapterArray);

            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(root.toString().getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            try {
                Files.move(temp.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | JSONException e) {
            Log.w(TAG, "Could not write structure.json", e);
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            return false;
        }
    }

    Chapter addChapter(String name) {
        Chapter chapter = new Chapter(UUID.randomUUID().toString(),
                cleanName(name, "未命名章节"));
        chapters.add(chapter);
        return chapter;
    }

    Unit addUnit(Chapter chapter, String name) {
        Unit unit = new Unit(UUID.randomUUID().toString(), cleanName(name, "未命名单元"));
        chapter.units.add(unit);
        return unit;
    }

    Course addCourse(Unit unit, String name) {
        Course course = new Course(UUID.randomUUID().toString(),
                cleanName(name, "未命名课程"));
        unit.courses.add(course);
        return course;
    }

    Question addQuestion(Course course, String name) {
        Question question = new Question(UUID.randomUUID().toString(),
                cleanName(name, "未命名题目"));
        question.type = Question.TYPE_TRANSLATION_CHOICE;
        course.questions.add(question);
        return question;
    }

    Question findQuestion(Course course, String questionId) {
        if (course == null || questionId == null) return null;
        for (Question question : course.questions) {
            if (question.id.equals(questionId)) return question;
        }
        return null;
    }

    int unitCount() {
        int count = 0;
        for (Chapter chapter : chapters) {
            count += chapter.units.size();
        }
        return count;
    }

    int courseCount() {
        int count = 0;
        for (Chapter chapter : chapters) {
            for (Unit unit : chapter.units) {
                count += unit.courses.size();
            }
        }
        return count;
    }

    static int courseCount(Chapter chapter) {
        int count = 0;
        for (Unit unit : chapter.units) {
            count += unit.courses.size();
        }
        return count;
    }

    static String cleanName(String value, String fallback) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty()) {
            name = fallback;
        }
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH).trim();
        }
        return name;
    }

    private static String cleanId(String value) {
        if (value == null || value.trim().isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return value.trim();
    }

    static final class Chapter {
        final String id;
        final String name;
        final List<Unit> units = new ArrayList<>();

        Chapter(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static final class Unit {
        final String id;
        final String name;
        final List<Course> courses = new ArrayList<>();

        Unit(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static final class Course {
        final String id;
        final String name;
        final List<Question> questions = new ArrayList<>();

        Course(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static final class Question {
        static final String TYPE_TRANSLATION_CHOICE = "translation_choice";

        final String id;
        String name;
        String type = TYPE_TRANSLATION_CHOICE;
        static final class Segment {
            final boolean word;
            final String vocabId;
            final String text;

            private Segment(boolean word, String vocabId, String text) {
                this.word = word;
                this.vocabId = vocabId;
                this.text = text;
            }

            static Segment word(String vocabId) {
                return new Segment(true, vocabId, "");
            }

            static Segment punct(String text) {
                return new Segment(false, "", text);
            }
        }

        final List<String> parts = new ArrayList<>();
        final List<Segment> segments = new ArrayList<>();
        String sentence = "";
        boolean hintsEnabled = true;
        String audio = "";
        long audioDuration;
        long audioStart;
        long audioEnd;
        final List<String> options = new ArrayList<>();
        int correctIndex = -1;

        Question(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}