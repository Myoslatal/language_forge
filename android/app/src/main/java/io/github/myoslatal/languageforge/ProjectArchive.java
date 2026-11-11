package io.github.myoslatal.languageforge;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** .conlang ZIP import/export helpers. */
final class ProjectArchive {

    private static final int MAX_ENTRIES = 5000;
    private static final long MAX_UNCOMPRESSED = 200L * 1024 * 1024;
    private static final String CONLANG = ".conlang";
    private static final String PROJECT_JSON = "project.json";

    private ProjectArchive() {
    }

    static String exportFileName(String input, String fallbackProjectName) {
        String base = input == null ? "" : input.trim();
        while (base.toLowerCase(Locale.ROOT).endsWith(CONLANG)) {
            base = base.substring(0, base.length() - CONLANG.length()).trim();
        }
        base = sanitize(base);
        if (base.isEmpty()) {
            String fallback = fallbackProjectName == null ? "" : fallbackProjectName.trim();
            while (fallback.toLowerCase(Locale.ROOT).endsWith(CONLANG)) {
                fallback = fallback.substring(0, fallback.length() - CONLANG.length()).trim();
            }
            base = sanitize(fallback);
        }
        if (base.isEmpty()) {
            base = "project";
        }
        if (base.length() > 80) {
            base = base.substring(0, 80).trim();
        }
        base = base.replaceAll("[. ]+$", "");
        if (base.isEmpty()) {
            base = "project";
        }
        return base + CONLANG;
    }

    static void exportProject(File projectDirectory, OutputStream output) throws IOException {
        if (projectDirectory == null || !projectDirectory.isDirectory()) {
            throw new IOException("项目目录不存在");
        }
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(output))) {
            addFiles(zip, projectDirectory, "");
            zip.finish();
        }
    }

    static ImportResult importProject(ProjectStore store, InputStream input, String displayName)
            throws IOException {
        if (displayName == null
                || !displayName.toLowerCase(Locale.ROOT).endsWith(CONLANG)) {
            throw new IOException("请选择 .conlang 文件");
        }
        File root = store.getRoot();
        if (root == null) {
            throw new IOException("外部存储不可用");
        }
        if (!root.exists() && !root.mkdirs()) {
            throw new IOException("无法创建项目目录");
        }
        File temp = new File(root, ".import-tmp-" + UUID.randomUUID()
                .toString().replace("-", ""));
        if (!temp.mkdirs()) {
            throw new IOException("无法创建导入临时目录");
        }

        File finalDirectory = null;
        try {
            extract(input, temp);
            File contentRoot = findProjectRoot(temp);
            String name = readProjectName(new File(contentRoot, PROJECT_JSON));
            finalDirectory = store.createUniqueDirectory(name);
            if (finalDirectory == null) {
                throw new IOException("无法创建项目文件夹");
            }
            copyContents(contentRoot, finalDirectory);
            return new ImportResult(name, finalDirectory);
        } catch (Exception e) {
            if (finalDirectory != null) {
                deleteRecursively(root, finalDirectory);
            }
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException(e.getMessage() == null ? "导入失败" : e.getMessage(), e);
        } finally {
            deleteRecursively(root, temp);
        }
    }

    private static void extract(InputStream input, File temp) throws IOException {
        String tempCanonical = temp.getCanonicalPath();
        int entries = 0;
        long total = 0L;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(input))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ENTRIES) {
                    throw new IOException("ZIP 条目过多");
                }
                String name = entry.getName();
                if (name == null || name.isEmpty()) {
                    zip.closeEntry();
                    continue;
                }
                name = name.replace('\\', '/');
                validateEntryName(name);
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }
                if (entry.getSize() > MAX_UNCOMPRESSED) {
                    throw new IOException("ZIP 解压内容过大");
                }
                File target = new File(temp, name);
                String targetCanonical = target.getCanonicalPath();
                if (!targetCanonical.startsWith(tempCanonical + File.separator)) {
                    throw new IOException("ZIP 路径越界");
                }
                File parent = target.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("无法创建解压目录");
                }
                try (FileOutputStream out = new FileOutputStream(target)) {
                    int read;
                    while ((read = zip.read(buffer)) > 0) {
                        total += read;
                        if (total > MAX_UNCOMPRESSED) {
                            throw new IOException("ZIP 解压内容过大");
                        }
                        out.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static void validateEntryName(String name) throws IOException {
        if (name.startsWith("/")) {
            throw new IOException("ZIP 包含绝对路径");
        }
        String[] parts = name.split("/");
        for (String part : parts) {
            if ("..".equals(part)) {
                throw new IOException("ZIP 路径越界");
            }
        }
    }

    private static File findProjectRoot(File temp) throws IOException {
        File rootJson = new File(temp, PROJECT_JSON);
        if (rootJson.isFile()) {
            return temp;
        }
        File[] children = temp.listFiles(File::isDirectory);
        if (children != null && children.length == 1) {
            File wrapper = children[0];
            if (new File(wrapper, PROJECT_JSON).isFile()) {
                return wrapper;
            }
        }
        throw new IOException("ZIP 根目录缺少 project.json");
    }

    private static String readProjectName(File json) throws IOException {
        String content = new String(Files.readAllBytes(json.toPath()), StandardCharsets.UTF_8);
        try {
            JSONObject object = new JSONObject(content);
            if (object.optInt("schema", -1) != 1) {
                throw new IOException("project.json schema 不受支持");
            }
            String name = object.optString("name", "").trim();
            if (name.isEmpty()) {
                throw new IOException("project.json 缺少项目名称");
            }
            return name;
        } catch (org.json.JSONException e) {
            throw new IOException("project.json 格式错误", e);
        }
    }

    private static void copyContents(File source, File destination) throws IOException {
        File[] children = source.listFiles();
        if (children == null) {
            throw new IOException("无法读取导入内容");
        }
        Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File child : children) {
            copyRecursively(child, new File(destination, child.getName()));
        }
    }

    private static void copyRecursively(File source, File destination) throws IOException {
        if (source.isDirectory()) {
            if (!destination.exists() && !destination.mkdirs()) {
                throw new IOException("无法创建项目子目录");
            }
            File[] children = source.listFiles();
            if (children == null) {
                throw new IOException("无法读取项目子目录");
            }
            for (File child : children) {
                copyRecursively(child, new File(destination, child.getName()));
            }
            return;
        }
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        }
    }

    private static void addFiles(ZipOutputStream zip, File directory, String relative)
            throws IOException {
        File[] children = directory.listFiles();
        if (children == null) {
            throw new IOException("无法读取项目目录");
        }
        Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File child : children) {
            String entryName = relative.isEmpty()
                    ? child.getName()
                    : relative + "/" + child.getName();
            if (child.isDirectory()) {
                addFiles(zip, child, entryName);
            } else if (child.isFile()) {
                zip.putNextEntry(new ZipEntry(entryName));
                try (FileInputStream in = new FileInputStream(child)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        zip.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static String sanitize(String value) {
        String result = value == null ? "" : value.trim();
        result = result.replaceAll("[\\\\/:*?\"<>|]", "-");
        result = result.replaceAll("\\s+", " ").trim();
        return result;
    }

    private static void deleteRecursively(File root, File file) {
        if (root == null || file == null) {
            return;
        }
        try {
            String rootCanonical = root.getCanonicalPath();
            String fileCanonical = file.getCanonicalPath();
            if (!fileCanonical.equals(rootCanonical)
                    && !fileCanonical.startsWith(rootCanonical + File.separator)) {
                return;
            }
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        deleteRecursively(root, child);
                    }
                }
            }
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        } catch (IOException ignored) {
            // Cleanup is best-effort.
        }
    }

    static final class ImportResult {
        final String name;
        final File directory;

        ImportResult(String name, File directory) {
            this.name = name;
            this.directory = directory;
        }
    }
}
