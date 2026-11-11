package io.github.myoslatal.languageforge;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

/** Project media helpers: image decode/downsample, cache files, PNG storage. */
final class MediaFiles {

    static final int MAX_DECODE_DIMENSION = 2048;
    private static final String IMAGE_DIR = "resources/images";
    private static final String AUDIO_DIR = "resources/audio";

    private MediaFiles() {
    }

    static File cacheFile(Context context, String prefix) {
        File directory = context.getCacheDir();
        if (!directory.exists() && !directory.mkdirs()) {
            return null;
        }
        return new File(directory, prefix + "_" + UUID.randomUUID()
                .toString().replace("-", "") + ".png");
    }

    static File cacheAudioFile(Context context, String extension) {
        File directory = context.getCacheDir();
        if (!directory.exists() && !directory.mkdirs()) {
            return null;
        }
        String suffix = extension == null || extension.isEmpty() ? ".m4a" : extension;
        return new File(directory, "audio_" + UUID.randomUUID()
                .toString().replace("-", "") + suffix);
    }

    static File copyUriToCache(Context context, ContentResolver resolver, Uri uri)
            throws IOException {
        String extension = ".m4a";
        String displayName = null;
        try (android.database.Cursor cursor = resolver.query(uri,
                new String[]{android.provider.OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                displayName = cursor.getString(0);
            }
        } catch (Exception ignored) {
        }
        if (displayName != null) {
            int dot = displayName.lastIndexOf('.');
            if (dot >= 0 && dot < displayName.length() - 1) {
                String ext = displayName.substring(dot).toLowerCase(Locale.ROOT);
                if (ext.matches("\\.[a-z0-9]{1,8}")) {
                    extension = ext;
                }
            }
        }
        File target = cacheAudioFile(context, extension);
        if (target == null) {
            throw new IOException("无法创建音频缓存");
        }
        try (InputStream in = resolver.openInputStream(uri);
             FileOutputStream out = new FileOutputStream(target)) {
            if (in == null) {
                throw new IOException("无法读取音频");
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            deleteQuietly(target);
            throw e;
        }
        return target;
    }

    static long audioDuration(File file) {
        if (file == null || !file.isFile()) {
            return 0L;
        }
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String value = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (value == null) {
                return 0L;
            }
            return Math.max(0L, Long.parseLong(value));
        } catch (Exception e) {
            return 0L;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    static File copyAudioToProject(File projectDirectory, File source, String entryId)
            throws IOException {
        File directory = new File(projectDirectory, AUDIO_DIR);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("无法创建音频目录");
        }
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        String extension = dot >= 0 ? name.substring(dot) : ".m4a";
        if (!extension.matches("\\.[a-zA-Z0-9]{1,8}")) {
            extension = ".m4a";
        }
        String safeId = entryId == null ? "media" : entryId.replaceAll("[^a-zA-Z0-9_-]", "");
        File target = new File(directory, "vocab_" + safeId + "_"
                + System.currentTimeMillis() + extension);
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        }
        return target;
    }

    static Bitmap decodeUri(ContentResolver resolver, Uri uri, int maxDimension)
            throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDimension);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            bitmap = BitmapFactory.decodeStream(in, null, options);
        }
        if (bitmap == null) {
            return null;
        }
        int orientation = readOrientation(resolver, uri);
        return applyOrientation(bitmap, orientation);
    }

    static Bitmap decodeFile(File file, int maxDimension) {
        if (file == null || !file.isFile()) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDimension);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    static int[] imageSize(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null;
        }
        return new int[]{options.outWidth, options.outHeight};
    }

    static File writePng(Bitmap bitmap, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建图片目录");
        }
        try (FileOutputStream out = new FileOutputStream(target)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw new IOException("无法写入 PNG");
            }
        }
        return target;
    }

    static File copyToProject(File projectDirectory, File source, String entryId)
            throws IOException {
        File images = new File(projectDirectory, IMAGE_DIR);
        if (!images.exists() && !images.mkdirs()) {
            throw new IOException("无法创建资源目录");
        }
        String safeId = entryId == null ? "media" : entryId.replaceAll("[^a-zA-Z0-9_-]", "");
        File target = new File(images, "vocab_" + safeId + "_"
                + System.currentTimeMillis() + ".png");
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        }
        return target;
    }

    static String relativePath(File projectDirectory, File file) {
        if (projectDirectory == null || file == null) {
            return null;
        }
        try {
            String projectCanonical = projectDirectory.getCanonicalPath();
            String fileCanonical = file.getCanonicalPath();
            if (!fileCanonical.startsWith(projectCanonical + File.separator)) {
                return null;
            }
            return fileCanonical.substring(projectCanonical.length() + 1)
                    .replace(File.separatorChar, '/');
        } catch (IOException e) {
            return null;
        }
    }

    static File resolveProjectFile(File projectDirectory, String relativePath) {
        if (projectDirectory == null || relativePath == null || relativePath.trim().isEmpty()) {
            return null;
        }
        File file = new File(projectDirectory, relativePath);
        try {
            String projectCanonical = projectDirectory.getCanonicalPath();
            String fileCanonical = file.getCanonicalPath();
            if (!fileCanonical.startsWith(projectCanonical + File.separator)) {
                return null;
            }
            return file;
        } catch (IOException e) {
            return null;
        }
    }

    static Bitmap loadProjectImage(File projectDirectory, String relativePath) {
        File file = resolveProjectFile(projectDirectory, relativePath);
        return decodeFile(file, MAX_DECODE_DIMENSION);
    }

    static void deleteQuietly(File file) {
        if (file != null && file.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static int sampleSize(int width, int height, int maxDimension) {
        int sample = 1;
        while (Math.max(width / sample, height / sample) > maxDimension) {
            sample *= 2;
        }
        return sample;
    }

    private static int readOrientation(ContentResolver resolver, Uri uri) {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                return ExifInterface.ORIENTATION_NORMAL;
            }
            ExifInterface exif = new ExifInterface(in);
            return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception e) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static Bitmap applyOrientation(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.postScale(1, -1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.postRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.postRotate(270);
                matrix.postScale(-1, 1);
                break;
            default:
                return bitmap;
        }
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) {
            bitmap.recycle();
        }
        return rotated;
    }
}