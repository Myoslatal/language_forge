package io.github.myoslatal.languageforge;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Thin wrapper around android.speech.tts.TextToSpeech.
 *
 * Mirrors the HTML speak() helper: asynchronous engine initialization, BCP-47
 * language selection with a project-level fallback, rate 0.85 and QUEUE_FLUSH.
 * Also exposes synthesizeToFile for generating vocabulary example audio. All
 * failures are reported as user-facing messages; nothing throws to the UI.
 */
final class TtsSupport {

    static final String TAG = "FORGE_TTS";

    interface StatusListener {
        void onTtsReady();
        void onTtsFailed(String message);
    }

    interface PlaybackListener {
        void onTtsDone();
        void onTtsError(String message);
    }

    interface SynthCallback {
        void onDone(File file);
        void onError(String message);
    }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StatusListener statusListener;
    private TextToSpeech tts;
    private boolean initialized;
    private boolean failed;
    private boolean shutdown;
    private String errorMessage = "";
    private String engineName = "";
    private PlaybackListener playbackListener;
    private SynthCallback synthCallback;
    private File synthFile;
    private String synthUtteranceId;
    private String pendingSpeakText;
    private String pendingSpeakLang;
    private float pendingSpeakRate = 0.85f;
    private String pendingSynthText;
    private String pendingSynthLang;
    private float pendingSynthRate = 0.85f;
    private File pendingSynthFile;
    private SynthCallback pendingSynthCallback;

    private final Runnable initTimeout = () -> {
        if (!initialized && !failed && !shutdown) {
            fail("初始化超时（可能未安装语音合成引擎）");
        }
    };

    TtsSupport(Context context, StatusListener listener) {
        this.context = context.getApplicationContext();
        this.statusListener = listener;
        handler.postDelayed(initTimeout, 8000);
        try {
            tts = new TextToSpeech(this.context, status -> handler.post(() -> onInit(status)));
        } catch (Exception error) {
            fail("系统 TTS 引擎不可用：" + error.getMessage());
        }
    }

    private void onInit(int status) {
        handler.removeCallbacks(initTimeout);
        if (shutdown) return;
        if (status != TextToSpeech.SUCCESS || tts == null) {
            fail("未安装语音合成引擎");
            return;
        }
        initialized = true;
        failed = false;
        try {
            engineName = tts.getDefaultEngine();
        } catch (Exception ignored) {
            engineName = "";
        }
        try {
            tts.setOnUtteranceProgressListener(progressListener);
        } catch (Exception error) {
            Log.w(TAG, "setOnUtteranceProgressListener failed", error);
        }
        Log.d(TAG, "init ok engine=" + engineName);
        if (statusListener != null) statusListener.onTtsReady();
        if (pendingSpeakText != null) {
            String text = pendingSpeakText;
            String lang = pendingSpeakLang;
            float rate = pendingSpeakRate;
            pendingSpeakText = null;
            pendingSpeakLang = null;
            speakNow(text, lang, rate);
        }
        if (pendingSynthText != null) {
            String text = pendingSynthText;
            String lang = pendingSynthLang;
            float rate = pendingSynthRate;
            File file = pendingSynthFile;
            SynthCallback callback = pendingSynthCallback;
            pendingSynthText = null;
            pendingSynthLang = null;
            pendingSynthFile = null;
            pendingSynthCallback = null;
            startSynthesisNow(text, lang, rate, file, callback);
        }
    }

    private void fail(String message) {
        if (shutdown) return;
        failed = true;
        initialized = false;
        errorMessage = message;
        handler.removeCallbacks(initTimeout);
        Log.w(TAG, "unavailable: " + message);
        if (statusListener != null) statusListener.onTtsFailed(message);
    }

    boolean isReady() {
        return initialized && !failed && !shutdown;
    }

    boolean isInitializing() {
        return !initialized && !failed && !shutdown;
    }

    boolean isFailed() {
        return failed && !shutdown;
    }

    String getUnavailableMessage() {
        return failed ? errorMessage : null;
    }

    String getEngineName() {
        return engineName == null ? "" : engineName;
    }

    String getStatusText() {
        if (failed) return errorMessage;
        if (!initialized) return "系统 TTS 正在初始化…";
        return "系统 TTS：" + (engineName == null || engineName.isEmpty() ? "默认引擎" : engineName);
    }

    void setPlaybackListener(PlaybackListener listener) {
        playbackListener = listener;
    }

    /**
     * Speaks with QUEUE_FLUSH. Returns a warning (for example an unsupported
     * language fallback) or an error message; null means accepted for playback.
     */
    String speak(String text, String lang, float rate) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return "没有可朗读的文本";
        if (failed) return errorMessage;
        if (!initialized) {
            pendingSpeakText = value;
            pendingSpeakLang = lang;
            pendingSpeakRate = rate;
            Log.d(TAG, "queued speech until init: " + value);
            return null;
        }
        return speakNow(value, lang, rate);
    }

    private String speakNow(String text, String lang, float rate) {
        if (tts == null) return "系统 TTS 引擎不可用";
        String warning = applyLanguage(lang);
        float safeRate = rate > 0f && rate <= 4f ? rate : 0.85f;
        tts.setSpeechRate(safeRate);
        int result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null,
                "forge-speak-" + System.nanoTime());
        if (result != TextToSpeech.SUCCESS) {
            Log.w(TAG, "speak failed result=" + result);
            return "TTS 播放失败（引擎拒绝朗读）";
        }
        Log.d(TAG, "speak text=" + text + " lang=" + normalizeLang(lang) + " rate=" + safeRate
                + " engine=" + engineName);
        return warning;
    }

    void synthesizeToFile(String text, String lang, float rate, File output, SynthCallback callback) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            callback.onError("没有可朗读的文本");
            return;
        }
        if (failed) {
            callback.onError(errorMessage);
            return;
        }
        if (!initialized) {
            if (pendingSynthCallback != null) {
                pendingSynthCallback.onError("新的 TTS 生成请求取代了上一次请求");
            }
            pendingSynthText = value;
            pendingSynthLang = lang;
            pendingSynthRate = rate;
            pendingSynthFile = output;
            pendingSynthCallback = callback;
            Log.d(TAG, "queued synthesis until init: " + value);
            return;
        }
        startSynthesisNow(value, lang, rate, output, callback);
    }

    private void startSynthesisNow(String text, String lang, float rate, File output,
                                   SynthCallback callback) {
        if (tts == null || output == null) {
            callback.onError("系统 TTS 引擎不可用");
            return;
        }
        if (synthCallback != null) {
            callback.onError("上一次 TTS 生成尚未完成");
            return;
        }
        applyLanguage(lang);
        float safeRate = rate > 0f && rate <= 4f ? rate : 0.85f;
        tts.setSpeechRate(safeRate);
        File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            callback.onError("无法创建 TTS 音频目录");
            return;
        }
        if (output.exists()) output.delete();
        synthFile = output;
        synthCallback = callback;
        synthUtteranceId = "forge-synth-" + System.nanoTime();
        int result = tts.synthesizeToFile(text, null, output, synthUtteranceId);
        if (result != TextToSpeech.SUCCESS) {
            synthFile = null;
            synthCallback = null;
            synthUtteranceId = null;
            Log.w(TAG, "synthesizeToFile rejected result=" + result);
            callback.onError("TTS 生成音频失败（引擎拒绝请求）");
            return;
        }
        Log.d(TAG, "synthesize text=" + text + " lang=" + normalizeLang(lang)
                + " rate=" + safeRate + " out=" + output.getAbsolutePath()
                + " engine=" + engineName);
    }

    private final UtteranceProgressListener progressListener = new UtteranceProgressListener() {
        @Override
        public void onStart(String utteranceId) {
            Log.d(TAG, "onStart " + utteranceId);
        }

        @Override
        public void onDone(String utteranceId) {
            handler.post(() -> {
                if (utteranceId != null && utteranceId.equals(synthUtteranceId)) {
                    finishSynthesis();
                } else if (playbackListener != null) {
                    playbackListener.onTtsDone();
                }
            });
        }

        @Override
        public void onError(String utteranceId) {
            handler.post(() -> failUtterance(utteranceId, "系统 TTS 朗读失败"));
        }

        @Override
        public void onError(String utteranceId, int errorCode) {
            handler.post(() -> failUtterance(utteranceId,
                    "系统 TTS 朗读失败（错误码 " + errorCode + "）"));
        }
    };

    private void finishSynthesis() {
        File file = synthFile;
        SynthCallback callback = synthCallback;
        synthFile = null;
        synthCallback = null;
        synthUtteranceId = null;
        if (callback == null) return;
        if (file == null || !file.isFile() || file.length() <= 44) {
            Log.w(TAG, "synthesis produced no audio: " + (file == null ? "null" : file.getAbsolutePath()));
            callback.onError("系统 TTS 没有生成有效音频");
            return;
        }
        Log.d(TAG, "synthesize ok file=" + file.getAbsolutePath() + " bytes=" + file.length());
        callback.onDone(file);
    }

    private void failUtterance(String utteranceId, String message) {
        if (utteranceId != null && utteranceId.equals(synthUtteranceId)) {
            SynthCallback callback = synthCallback;
            synthFile = null;
            synthCallback = null;
            synthUtteranceId = null;
            if (callback != null) callback.onError(message);
            return;
        }
        if (playbackListener != null) playbackListener.onTtsError(message);
    }

    private String applyLanguage(String lang) {
        if (tts == null) return null;
        Locale locale = parseLocale(lang);
        int available = TextToSpeech.LANG_NOT_SUPPORTED;
        try {
            available = tts.isLanguageAvailable(locale);
        } catch (Exception ignored) {
        }
        if (available >= TextToSpeech.LANG_AVAILABLE) {
            try {
                if (tts.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE) {
                    return null;
                }
            } catch (Exception ignored) {
            }
        }
        try {
            tts.setLanguage(Locale.getDefault());
        } catch (Exception ignored) {
        }
        String warning = "系统 TTS 不支持语言 " + normalizeLang(lang) + "，已改用默认语言";
        Log.w(TAG, warning + " available=" + available);
        return warning;
    }

    void stop() {
        if (tts != null && initialized) {
            try {
                tts.stop();
                Log.d(TAG, "stop");
            } catch (Exception ignored) {
            }
        }
    }

    void shutdown() {
        shutdown = true;
        handler.removeCallbacks(initTimeout);
        pendingSpeakText = null;
        pendingSynthText = null;
        pendingSynthCallback = null;
        synthCallback = null;
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {
            }
            tts = null;
        }
        Log.d(TAG, "shutdown");
    }

    static String normalizeLang(String lang) {
        String value = lang == null ? "" : lang.trim().replace('_', '-');
        return value.isEmpty() ? "en-US" : value;
    }

    static String resolveLang(String questionLang, String projectLang) {
        String question = questionLang == null ? "" : questionLang.trim();
        if (!question.isEmpty()) return question;
        String project = projectLang == null ? "" : projectLang.trim();
        if (!project.isEmpty()) return project;
        return "en-US";
    }

    static String ttsText(ProjectStructure.Question question) {
        if (question == null) return "";
        String tts = question.tts == null ? "" : question.tts.trim();
        if (!tts.isEmpty()) return tts;
        return question.prompt == null ? "" : question.prompt.trim();
    }

    static String readProjectTtsLang(File projectDirectory) {
        if (projectDirectory == null) return "en-US";
        File file = new File(projectDirectory, "project.json");
        if (!file.isFile()) return "en-US";
        try {
            String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            String value = new JSONObject(raw).optString("ttsLang", "en-US").trim();
            return value.isEmpty() ? "en-US" : value;
        } catch (Exception ignored) {
            return "en-US";
        }
    }

    private static Locale parseLocale(String lang) {
        String tag = normalizeLang(lang);
        Locale locale = Locale.forLanguageTag(tag);
        if (locale == null || locale.getLanguage().isEmpty()) return Locale.US;
        return locale;
    }
}
