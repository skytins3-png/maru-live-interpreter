package com.maru.liveinterpreter;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
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

    private final Activity activity;
    private final Listener listener;
    private final TranslationClient translator = new TranslationClient();
    private final SpeechRecognizer recognizer;
    private final TextToSpeech tts;
    private LanguageOption target = LanguageOption.ENGLISH;
    private boolean remoteMode;
    private boolean ttsReady;
    private String detectedTag = "en-US";

    public InterpreterEngine(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        recognizer = SpeechRecognizer.createSpeechRecognizer(activity);
        recognizer.setRecognitionListener(this);
        tts = new TextToSpeech(activity, this);
    }

    public void setTarget(LanguageOption target) { this.target = target; }

    public void listenKorean() {
        remoteMode = false;
        start(buildIntent("ko-KR", false), "듣고 있어요… 한국어로 말씀하세요");
    }

    public void listenRemoteAuto() {
        remoteMode = true;
        detectedTag = "en-US";
        Intent intent = buildIntent("en-US", true);
        if (Build.VERSION.SDK_INT >= 34) {
            intent.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true);
            ArrayList<String> allowed = new ArrayList<>(Arrays.asList("en-US", "zh-CN", "ja-JP", "ru-RU", "bn-BD"));
            intent.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, allowed);
            intent.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED);
            intent.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, allowed);
        }
        start(intent, "상대방 말을 듣고 있어요… 언어를 자동감지합니다");
    }

    private Intent buildIntent(String language, boolean remote) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, remote ? 3 : 1);
        return intent;
    }

    private void start(Intent intent, String status) {
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            listener.onError("휴대폰 음성인식 서비스를 사용할 수 없습니다"); return;
        }
        tts.stop();
        recognizer.cancel();
        recognizer.startListening(intent);
        listener.onState(status);
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
            @Override public void onError(String message) { listener.onError(message); }
        });
    }

    private void translateRemote(String foreignText) {
        LanguageOption detected = LanguageOption.fromTag(resolveDetectedTag(foreignText));
        detectedTag = detected.code;
        listener.onDetectedLanguage(detected.label);
        listener.onOriginal(detected.label + " 원문\n" + foreignText);
        listener.onState("한국어로 번역 중…");
        translator.translateToKorean(foreignText, detected.code, new TranslationClient.Callback() {
            @Override public void onResult(String translated) {
                listener.onTranslated("한국어\n" + translated);
                speak(translated, Locale.KOREA, "한국어");
            }
            @Override public void onError(String message) { listener.onError(message); }
        });
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
        if (!ttsReady) { listener.onError("음성 엔진을 준비 중입니다. 잠시 후 다시 눌러주세요"); return; }
        int language = tts.setLanguage(locale);
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            listener.onError(label + " 음성을 휴대폰 설정에서 내려받아 주세요"); return;
        }
        tts.setSpeechRate(0.92f);
        tts.setPitch(0.86f);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "maru_translation");
        listener.onState(label + " 번역 음성을 재생하고 있어요");
    }

    public void release() { recognizer.destroy(); tts.stop(); tts.shutdown(); }
    @Override public void onInit(int status) { ttsReady = status == TextToSpeech.SUCCESS; }
    @Override public void onReadyForSpeech(Bundle params) { }
    @Override public void onBeginningOfSpeech() { listener.onState(remoteMode ? "상대방 말을 듣는 중…" : "형의 말씀을 듣는 중…"); }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { listener.onState("말이 끝났어요. 변환 중…"); }
    @Override public void onError(int error) {
        String message = error == SpeechRecognizer.ERROR_NO_MATCH ? "말을 알아듣지 못했어요. 다시 눌러주세요" : "마이크 또는 음성인식 오류: " + error;
        listener.onError(message);
    }
    @Override public void onResults(Bundle results) {
        ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null || list.isEmpty() || list.get(0).trim().isEmpty()) listener.onError("인식된 말이 없습니다");
        else if (remoteMode) translateRemote(list.get(0).trim());
        else translateKorean(list.get(0).trim());
    }
    @Override public void onPartialResults(Bundle partialResults) { }
    @Override public void onEvent(int eventType, Bundle params) { }

    @Override public void onLanguageDetection(Bundle results) {
        if (Build.VERSION.SDK_INT < 34 || results == null) return;
        String tag = results.getString(SpeechRecognizer.DETECTED_LANGUAGE);
        int confidence = results.getInt(SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL, 0);
        if (tag != null && confidence >= SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL_CONFIDENT) {
            detectedTag = tag;
            listener.onDetectedLanguage(LanguageOption.fromTag(tag).label);
        }
    }
}
