package com.maru.wechattranslator;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

/** Explicit microphone capture. It does not capture WeChat call streams. */
public final class SpeechInput {
    public interface Listener {
        void partial(String text);
        void recognized(String text, boolean chinese);
        void error(String reason);
    }
    private SpeechRecognizer recognizer;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int generation;
    private String detectedTag="";
    public SpeechInput(Context c) { context=c; }
    public void listen(boolean chinese, boolean autoDetect, Listener listener) {
        stop();
        if(!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.error("음성인식 서비스를 사용할 수 없습니다. 텍스트 입력을 이용해 주세요."); return;
        }
        final int token=++generation;
        detectedTag="";
        try {
            recognizer=SpeechRecognizer.createSpeechRecognizer(context);
            recognizer.setRecognitionListener(new RecognitionListener() {
                public void onReadyForSpeech(Bundle b) {}
                public void onBeginningOfSpeech() {}
                public void onRmsChanged(float value) {}
                public void onBufferReceived(byte[] b) {}
                public void onEndOfSpeech() {}
                public void onPartialResults(Bundle b) {
                    if(token!=generation)return;
                    ArrayList<String> a=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if(a!=null && !a.isEmpty())listener.partial(a.get(0));
                }
                public void onResults(Bundle b) {
                    if(token!=generation)return;
                    ArrayList<String> a=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if(a==null||a.isEmpty()||a.get(0).trim().isEmpty()) {
                        listener.error("음성 문장이 인식되지 않았어요.");
                    } else {
                        String result=a.get(0).trim();
                        boolean direction=autoDetect?guessDirection(result,detectedTag,chinese):chinese;
                        listener.recognized(result,direction);
                    }
                    clear(token);
                }
                public void onError(int code) {
                    if(token!=generation)return;
                    listener.error("음성인식 오류 "+code+" · 통화 중 마이크가 제한될 수 있습니다.");
                    clear(token);
                }
                public void onEvent(int event,Bundle b) {}
                @Override public void onLanguageDetection(Bundle b) {
                    if(Build.VERSION.SDK_INT < 34||b==null) return;
                    String tag=b.getString(SpeechRecognizer.DETECTED_LANGUAGE);
                    if(tag!=null&&!tag.isEmpty()) detectedTag=tag;
                }
            });
            Intent request=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            request.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            request.putExtra(RecognizerIntent.EXTRA_LANGUAGE,chinese?"zh-CN":"ko-KR");
            request.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
            request.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
            if(autoDetect && Build.VERSION.SDK_INT >= 34) {
                request.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION,true);
                request.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,
                    new ArrayList<>(Arrays.asList("ko-KR","zh-CN")));
            }
            recognizer.startListening(request);
        } catch(Exception e) { listener.error("마이크 시작 오류: "+e.getMessage()); stop(); }
    }
    static boolean guessDirection(String text,String tag,boolean fallback) {
        if(tag!=null&&!tag.isEmpty()){
            if(tag.toLowerCase(Locale.ROOT).startsWith("ko"))return false;
            if(tag.toLowerCase(Locale.ROOT).startsWith("zh"))return true;
        }
        int ko=0,cn=0;
        for(int i=0;i<text.length();i++) {
            char ch=text.charAt(i);
            if((ch>=0xAC00&&ch<=0xD7A3)||(ch>=0x3130&&ch<=0x318F))ko++;
            if(ch>=0x4E00&&ch<=0x9FFF)cn++;
        }
        if(ko>0&&ko>=cn)return false;
        if(cn>0&&cn>ko)return true;
        return fallback;
    }
    private void clear(int token) {
        if(token!=generation)return;
        SpeechRecognizer r=recognizer;
        recognizer=null;generation++;
        if(r!=null)handler.post(()->{ try {r.destroy();}catch(Exception ignored){} });
    }
    public void stop() {
        ++generation;
        SpeechRecognizer old=recognizer; recognizer=null;
        if(old!=null){ try{old.cancel();old.destroy();}catch(Exception ignored){} }
    }
}
