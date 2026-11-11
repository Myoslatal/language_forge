package io.github.myoslatal.languageforge;

import java.io.File;

/** One on-disk project metadata record. */
final class Project {
    final File directory;
    final String folderName;
    final String name;
    final String courseLine;
    final int lessons;
    final int questions;
    final int words;
    final String updated;
    final long createdAt;
    boolean pinned;

    Project(File directory, String folderName, String name, String courseLine,
            int lessons, int questions, int words, String updated,
            long createdAt, boolean pinned) {
        this.directory = directory;
        this.folderName = folderName;
        this.name = name;
        this.courseLine = courseLine;
        this.lessons = lessons;
        this.questions = questions;
        this.words = words;
        this.updated = updated;
        this.createdAt = createdAt;
        this.pinned = pinned;
    }
}
