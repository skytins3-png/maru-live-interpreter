package com.maru.liveinterpreter;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import java.util.ArrayList;
import java.util.Locale;

public final class InterpreterEngine implements RecognitionListener, TextToSpeech.OnInitListener {
    public interface Listener { void onState(String state); void onOriginal(String text); void onTranslated(String text); void onError(String error); }
    private final Activity activity;
    private final Listener listener;
    private final TranslationClient translator = new TranslationClient();
    private final SpeechRecognizer recognizer;
    private final TextToSpeech tts;
    private LanguageOption target = LanguageOption.ENGLISH;
    private boolean ttsReady;

    public InterpreterEngine(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        recognizer = SpeechRecognizer.createSpeechRecognizer(activity);
        recognizer.setRecognitionListener(this);
        tts = new TextToSpeech(activity, this);
    }

    public void setTarget(LanguageOption target) { this.target = target; }

    public void listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) { listener.onError("휴대폰 음성인식 서비스를 사용할 수 없습니다"); return; }
        tts.stop();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        recognizer.startListening(intent);
        listener.onState("듣고 있어요… 한국어로 말씀하세요");
    }

    public void stop() { recognizer.stopListening(); }

    private void translate(String korean) {
        listener.onOriginal(korean);
        listener.onState(target.label + "로 번역 중…");
        translator.translateKorean(korean, target, new TranslationClient.Callback() {
            @Override public void onResult(String translated) {
                listener.onTranslated(translated);
                speak(translated);
            }
            @Override public void onError(String message) { listener.onError(message); }
        });
    }

    private void speak(String text) {
        if (!ttsReady) { listener.onError("음성 엔진을 준비 중입니다. 잠시 후 다시 눌러주세요"); return; }
        int language = tts.setLanguage(target.voiceLocale);
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            listener.onError(target.label + " 음성을 휴대폰 설정에서 내려받아 주세요"); return;
        }
        tts.setSpeechRate(0.92f);
        tts.setPitch(0.86f);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "maru_translation");
        listener.onState("번역 음성을 재생하고 있어요");
    }

    public void release() { recognizer.destroy(); tts.stop(); tts.shutdown(); }
    @Override public void onInit(int status) { ttsReady = status == TextToSpeech.SUCCESS; }
    @Override public void onReadyForSpeech(Bundle params) { }
    @Override public void onBeginningOfSpeech() { listener.onState("말씀을 듣는 중…"); }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { listener.onState("말씀이 끝났어요"); }
    @Override public void onError(int error) { listener.onError(error == SpeechRecognizer.ERROR_NO_MATCH ? "말씀을 알아듣지 못했어요. 다시 눌러주세요" : "마이크 또는 음성인식 오류: " + error); }
    @Override public void onResults(Bundle results) {
        ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null || list.isEmpty() || list.get(0).trim().isEmpty()) listener.onError("인식된 말이 없습니다"); else translate(list.get(0).trim());
    }
    @Override public void onPartialResults(Bundle partialResults) { }
    @Override public void onEvent(int eventType, Bundle params) { }
}
