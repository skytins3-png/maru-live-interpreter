package com.maru.wechattranslator;

import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import java.util.HashMap;
import java.util.Map;

/** On-device translation with a visible English intermediate and non-authoritative back-translation. */
public final class TranslateEngine {
    public interface Success { void accept(Result r); }
    public interface Failure { void accept(String message); }
    private interface Step { void accept(String text); }
    public static final class Result {
        public final String original, english, translated, back;
        public final boolean incoming;
        Result(String o, String e, String t, String b, boolean i) {
            original=o; english=e; translated=t; back=b; incoming=i;
        }
    }
    private final Map<String, Translator> cache = new HashMap<>();
    private boolean closed;
    private Translator client(String from, String to) {
        String key=from+":"+to;
        Translator value=cache.get(key);
        if(value!=null) return value;
        value=Translation.getClient(new TranslatorOptions.Builder()
          .setSourceLanguage(from).setTargetLanguage(to).build());
        cache.put(key,value);
        return value;
    }
    private void step(String from, String to, String input, Step success, Failure fail) {
        if (closed) { fail.accept("번역기가 종료되었습니다."); return; }
        final Translator t;
        try { t=client(from,to); } catch (Exception e) {
            fail.accept("언어 모델 준비 실패: "+e.getMessage()); return;
        }
        t.downloadModelIfNeeded()
         .addOnSuccessListener(x->t.translate(input)
            .addOnSuccessListener(value-> { if(!closed)success.accept(value); })
            .addOnFailureListener(e->fail.accept("번역 오류: "+e.getMessage())))
         .addOnFailureListener(e->fail.accept("언어 모델 다운로드 오류: "+e.getMessage()
           +" · Wi-Fi를 확인해 주세요."));
    }
    public void translate(String input, boolean chineseIncoming, Success ok, Failure fail) {
        String from=chineseIncoming?TranslateLanguage.CHINESE:TranslateLanguage.KOREAN;
        String to=chineseIncoming?TranslateLanguage.KOREAN:TranslateLanguage.CHINESE;
        String original=input==null?"":input.trim();
        if(original.isEmpty()) { fail.accept("먼저 원문을 입력해 주세요."); return; }
        step(from,TranslateLanguage.ENGLISH,original,en->
          step(TranslateLanguage.ENGLISH,to,en,target->
            step(to,TranslateLanguage.ENGLISH,target,reverseEnglish->
              step(TranslateLanguage.ENGLISH,from,reverseEnglish,back->
                ok.accept(new Result(original,en,target,back,chineseIncoming)),fail),fail),fail),fail);
    }
    public void recheck(String translated, boolean chineseIncoming, StepResult ok, Failure fail) {
        String from=chineseIncoming?TranslateLanguage.KOREAN:TranslateLanguage.CHINESE;
        String to=chineseIncoming?TranslateLanguage.CHINESE:TranslateLanguage.KOREAN;
        if(translated==null||translated.trim().isEmpty()) { fail.accept("검사할 번역문이 없습니다."); return; }
        step(from,TranslateLanguage.ENGLISH,translated,en->
          step(TranslateLanguage.ENGLISH,to,en,ok::accept,fail),fail);
    }
    public interface StepResult { void accept(String backTranslated); }
    public void close() {
        closed=true;
        for(Translator t:cache.values()) t.close();
        cache.clear();
    }
}
