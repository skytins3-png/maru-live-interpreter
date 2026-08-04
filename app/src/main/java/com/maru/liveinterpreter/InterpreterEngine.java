package com.maru.liveinterpreter;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public final class InterpreterEngine implements RecognitionListener, TextToSpeech.OnInitListener {
    public interface Listener {
        void onState(String state);
        void onOriginal(String text);
        void onTranslated(String text);
        void onDetectedLanguage(String label);
        void onError(String error);
    }

    private final Context context;
    private final Listener listener;
    private final TranslationClient translator = new TranslationClient();
    private final SpeechRecognizer recognizer;
    private final TextToSpeech tts;
    private final Handler main = new Handler(Looper.getMainLooper());
    private LanguageOption target = LanguageOption.ENGLISH;
    private boolean remoteMode;
    private boolean ttsReady;
    private boolean recognitionActive;
    private boolean autoConversation;
    private boolean nextRemote = true;
    private boolean remoteLanguageLocked;
    private long lastStartMillis;
    private String detectedTag = "en-US";
    private String lastPartial = "";
    private final Runnable finishAfterShortSilence;

    public InterpreterEngine(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        recognizer.setRecognitionListener(this);
        finishAfterShortSilence = () -> {
            if (recognitionActive && !lastPartial.trim().isEmpty()) {
                try { recognizer.stopListening(); } catch (RuntimeException ignored) { }
            }
        };
        tts = new TextToSpeech(context, this);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { }
            @Override public void onError(String utteranceId) { main.post(() -> scheduleNext(450)); }
            @Override public void onDone(String utteranceId) { main.post(() -> scheduleNext(280)); }
        });
    }

    public void setTarget(LanguageOption target) { this.target = target; }

    public void listenKorean() {
        remoteMode = false;
        start(buildIntent("ko-KR", false), "형의 답변을 듣고 있어요…");
    }

    public void listenRemoteAuto() {
        remoteMode = true;
        Intent intent = buildIntent(remoteLanguageLocked ? detectedTag : "en-US", true);
        if (!remoteLanguageLocked && Build.VERSION.SDK_INT >= 34) {
            ArrayList<String> allowed = new ArrayList<>(Arrays.asList("en-US", "zh-CN", "ja-JP", "ru-RU", "bn-BD"));
            intent.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true);
            intent.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, allowed);
            intent.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED);
            intent.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, allowed);
        }
        start(intent, remoteLanguageLocked
                ? "상대방 말을 듣는 중… " + target.label + " 고정"
                : "상대방 말을 듣는 중… 언어 자동감지");
    }

    public void startAutoConversation() {
        autoConversation = true;
        nextRemote = true;
        remoteLanguageLocked = false;
        detectedTag = "en-US";
        listener.onState("자동 일상대화 시작");
        main.postDelayed(this::listenRemoteAuto, 250);
    }

    public void stopAutoConversation() {
        autoConversation = false;
        recognitionActive = false;
        main.removeCallbacksAndMessages(null);
        recognizer.cancel();
        tts.stop();
        listener.onState("자동 일상대화 중지");
    }

    public boolean isAutoConversation() { return autoConversation; }

    private Intent buildIntent(String language, boolean remote) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, remote ? 3 : 1);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 650L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 400L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 250L);
        return intent;
    }

    private void start(Intent intent, String status) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onError("휴대폰 음성인식 서비스를 사용할 수 없습니다"); return;
        }
        tts.stop();
        long wait = Math.max(0, 250 - (System.currentTimeMillis() - lastStartMillis));
        Runnable begin = () -> {
            try {
                lastPartial = "";
                main.removeCallbacks(finishAfterShortSilence);
                recognitionActive = true;
                lastStartMillis = System.currentTimeMillis();
                recognizer.startListening(intent);
                listener.onState(status);
            } catch (RuntimeException e) {
                recognitionActive = false;
                listener.onError("음성인식을 다시 준비합니다");
                if (autoConversation) main.postDelayed(this::retryCurrentMode, 650);
            }
        };
        if (recognitionActive) {
            recognitionActive = false;
            recognizer.cancel();
            main.postDelayed(begin, Math.max(300, wait));
        } else main.postDelayed(begin, wait);
    }

    public void stop() { recognizer.stopListening(); }

    private void translateKorean(String korean) {
        listener.onOriginal(korean);
        listener.onState(target.label + "로 번역 중…");
        translator.translateKorean(korean, target, new TranslationClient.Callback() {
            @Override public void onResult(String translated) {
                listener.onTranslated(translated);
                speak(translated, target.voiceLocale, target.label);
            }
            @Override public void onError(String message) { translationError(message); }
        });
    }

    private void translateRemote(String foreignText) {
        LanguageOption detected = LanguageOption.fromTag(resolveDetectedTag(foreignText));
        detectedTag = detected.code;
        target = detected;
        remoteLanguageLocked = true;
        listener.onDetectedLanguage(detected.label);
        listener.onOriginal(detected.label + " 원문\n" + foreignText);
        listener.onState("한국어로 번역 중…");
        translator.translateToKorean(foreignText, detected.code, new TranslationClient.Callback() {
            @Override public void onResult(String translated) {
                listener.onTranslated("한국어\n" + translated);
                speak(translated, Locale.KOREA, "한국어");
            }
            @Override public void onError(String message) { translationError(message); }
        });
    }

    private void translationError(String message) {
        listener.onError(message);
        if (autoConversation) main.postDelayed(this::retryCurrentMode, 650);
    }

    private String resolveDetectedTag(String text) {
        if (detectedTag != null && !detectedTag.trim().isEmpty() && !"en-US".equals(detectedTag)) return detectedTag;
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(text.charAt(i));
            if (block == Character.UnicodeBlock.HIRAGANA || block == Character.UnicodeBlock.KATAKANA) return "ja";
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) return "zh-CN";
            if (block == Character.UnicodeBlock.CYRILLIC) return "ru";
            if (block == Character.UnicodeBlock.BENGALI) return "bn";
        }
        return "en";
    }

    private void speak(String text, Locale locale, String label) {
        if (!ttsReady) { translationError("음성 엔진을 준비 중입니다. 잠시 후 다시 시도합니다"); return; }
        int language = tts.setLanguage(locale);
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            translationError(label + " 음성을 휴대폰 설정에서 내려받아 주세요"); return;
        }
        selectLowVoice(locale);
        tts.setSpeechRate(0.92f);
        tts.setPitch(0.78f);
        nextRemote = !remoteMode;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, remoteMode ? "maru_to_korean" : "maru_to_foreign");
        listener.onState(label + " 음성으로 전달 중…");
    }

    private void selectLowVoice(Locale locale) {
        if (tts.getVoices() == null) return;
        Voice best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Voice voice : tts.getVoices()) {
            if (!voice.getLocale().getLanguage().equals(locale.getLanguage())) continue;
            String name = voice.getName().toLowerCase(Locale.ROOT);
            int score = 0;
            if (name.contains("male") || name.contains("man") || name.contains("m1") || name.contains("low")) score += 10;
            if (!voice.isNetworkConnectionRequired()) score += 2;
            if (voice.getQuality() >= Voice.QUALITY_HIGH) score += 2;
            if (score > bestScore) { best = voice; bestScore = score; }
        }
        if (best != null) tts.setVoice(best);
    }

    private void scheduleNext(long delay) {
        if (!autoConversation) return;
        main.postDelayed(() -> {
            if (!autoConversation) return;
            if (nextRemote) listenRemoteAuto(); else listenKorean();
        }, delay);
    }

    private void retryCurrentMode() {
        if (!autoConversation) return;
        if (remoteMode) listenRemoteAuto(); else listenKorean();
    }

    public void release() {
        autoConversation = false;
        main.removeCallbacksAndMessages(null);
        recognizer.destroy();
        tts.stop();
        tts.shutdown();
    }

    @Override public void onInit(int status) { ttsReady = status == TextToSpeech.SUCCESS; }
    @Override public void onReadyForSpeech(Bundle params) { }
    @Override public void onBeginningOfSpeech() { listener.onState(remoteMode ? "상대방 말하는 중…" : "형의 답변 듣는 중…"); }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { listener.onState("말이 끝났어요 · 변환 중…"); }
    @Override public void onError(int error) {
        recognitionActive = false;
        main.removeCallbacks(finishAfterShortSilence);
        String message = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                ? "말을 기다리는 중…" : "마이크 또는 음성인식 오류: " + error;
        listener.onError(message);
        if (autoConversation) main.postDelayed(this::retryCurrentMode,
                error == SpeechRecognizer.ERROR_CLIENT ? 800 : 450);
    }
    @Override public void onResults(Bundle results) {
        recognitionActive = false;
        main.removeCallbacks(finishAfterShortSilence);
        ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null || list.isEmpty() || list.get(0).trim().isEmpty()) {
            listener.onError("인식된 말이 없습니다");
            if (autoConversation) main.postDelayed(this::retryCurrentMode, 450);
        } else if (remoteMode) translateRemote(list.get(0).trim());
        else translateKorean(list.get(0).trim());
    }
    @Override public void onPartialResults(Bundle partialResults) {
        ArrayList<String> list = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null || list.isEmpty()) return;
        String partial = list.get(0).trim();
        if (partial.isEmpty()) return;
        lastPartial = partial;
        main.removeCallbacks(finishAfterShortSilence);
        main.postDelayed(finishAfterShortSilence, 650);
    }
    @Override public void onEvent(int eventType, Bundle params) { }

    @Override public void onLanguageDetection(Bundle results) {
        if (Build.VERSION.SDK_INT < 34 || results == null) return;
        String tag = results.getString(SpeechRecognizer.DETECTED_LANGUAGE);
        int confidence = results.getInt(SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL, 0);
        if (tag != null && confidence >= SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL_CONFIDENT) {
            detectedTag = tag;
            target = LanguageOption.fromTag(tag);
            remoteLanguageLocked = true;
            listener.onDetectedLanguage(target.label);
        }
    }
}
