package com.maru.liveinterpreter;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Standalone voice / text verification. This Activity does NOT capture audio from WeChat:
 * Android restricts access to third-party VoIP call streams. For a real WeChat call, use
 * Galaxy AI > Call assist > Live translate in other apps.
 */
public final class WeChatRelayTestActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int REQUEST_KOREAN = 501;
    private static final int REQUEST_CHINESE = 502;
    private final TranslationClient translator = new TranslationClient();
    private TextToSpeech tts;
    private boolean ttsReady;
    private boolean busy;
    private boolean destroyed;
    private String lastTranslation = "";
    private Locale lastVoiceLocale = Locale.SIMPLIFIED_CHINESE;
    private String source = "ko";
    private String target = "zh-CN";

    private TextView status;
    private TextView original;
    private TextView intermediate;
    private TextView result;
    private TextView reverseEnglish;
    private TextView reverseSource;
    private EditText typed;
    private Button koreanMic;
    private Button chineseMic;
    private Button translateTyped;
    private Button replay;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tts = new TextToSpeech(this, this);
        setContentView(buildScreen());
    }

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(24));
        root.setBackgroundColor(Color.rgb(246, 250, 249));
        scroll.addView(root);

        TextView heading = label("위챗 한·중 음성 번역 테스트", 24, Color.rgb(17, 78, 66));
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(heading);
        root.addView(label("음성 인식 → 영어 → 최종 언어 → 영어 → 원래 언어로 역번역 검사", 15, Color.DKGRAY));
        root.addView(spacer(14));

        koreanMic = button("🎤 한국어 말하기 → 영어 → 중국어", root, () -> recognize("ko-KR", REQUEST_KOREAN));
        chineseMic = button("🎤 중국어 말하기 → 영어 → 한국어", root, () -> recognize("zh-CN", REQUEST_CHINESE));
        root.addView(spacer(12));

        typed = new EditText(this);
        typed.setTextSize(17);
        typed.setMinLines(2);
        typed.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        typed.setHint("소리 내기 어려우면 여기에 문장을 입력하세요");
        typed.setBackgroundColor(Color.WHITE);
        typed.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(typed, new LinearLayout.LayoutParams(-1, -2));

        translateTyped = button("입력 문장 번역 · 현재 방향", root, () -> {
            String value = typed.getText().toString().trim();
            if (value.isEmpty()) { toast("먼저 문장을 입력해 주세요"); return; }
            runRelay(value);
        });
        button("↔ 번역 방향 바꾸기", root, () -> {
            if (busy) return;
            boolean toChinese = "ko".equals(source);
            source = toChinese ? "zh-CN" : "ko";
            target = toChinese ? "ko" : "zh-CN";
            status.setText(toChinese ? "중국어 → 영어 → 한국어 선택" : "한국어 → 영어 → 중국어 선택");
        });
        root.addView(spacer(12));

        status = label("시험 준비 완료 · 기본 방향: 한국어 → 중국어", 16, Color.rgb(14, 92, 76));
        root.addView(status);
        original = box(root, "① 음성 인식 / 원문");
        intermediate = box(root, "② 영어 중간 번역");
        result = box(root, "③ 최종 번역");
        reverseEnglish = box(root, "④ 역번역 영어");
        reverseSource = box(root, "⑤ 원래 언어로 역번역 · 의미 확인");
        replay = button("🔊 최종 번역 다시 듣기", root, () -> speak(lastTranslation, lastVoiceLocale));
        replay.setEnabled(false);
        root.addView(spacer(8));

        TextView notice = label("주의: 이 화면은 마이크/문장 번역을 따로 시험하는 기능입니다. 위챗 통화 상대방의 소리를 직접 가져오거나 통화에 번역 음성을 주입하지 않습니다. 통화에서는 갤럭시 AI 실시간 통역을 사용하세요. 음성 인식 서비스와 번역 서버에 음성 또는 텍스트가 전송될 수 있습니다. 무료 번역 서버에는 사용량 제한이 있으며, 연쇄 번역이 100% 정확한 것은 아닙니다.", 13, Color.DKGRAY);
        notice.setPadding(0, dp(12), 0, dp(8));
        root.addView(notice);
        button("⚙ 휴대폰 설정 열기 · Galaxy AI → 통화 어시스트", root, () ->
            startActivity(new Intent(Settings.ACTION_SETTINGS)));
        return scroll;
    }

    private void recognize(String languageTag, int requestCode) {
        if (busy) return;
        // The installed system recognizer handles microphone capture and permission UI.
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
            requestCode == REQUEST_KOREAN ? "한국어로 말해 주세요" : "请说中文");
        try {
            startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            toast("휴대폰에 음성 인식 앱이 없습니다. 키보드 입력으로 시험해 주세요.");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode != REQUEST_KOREAN && requestCode != REQUEST_CHINESE) return;
        ArrayList<String> recognized = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (recognized == null || recognized.isEmpty()) { toast("음성 인식 결과가 없습니다"); return; }
        if (requestCode == REQUEST_KOREAN) {
            source = "ko"; target = "zh-CN";
        } else {
            source = "zh-CN"; target = "ko";
        }
        typed.setText(recognized.get(0));
        runRelay(recognized.get(0));
    }

    private void runRelay(String input) {
        if (busy) return;
        String phrase = input.trim();
        if (phrase.isEmpty()) return;
        final String from = source, to = target;
        busy = true;
        setControlsEnabled(false);
        lastTranslation = "";
        replay.setEnabled(false);
        original.setText("① 원문 (" + from + ")\n" + phrase);
        intermediate.setText("② 영어 중간 번역\n진행 중…");
        result.setText("③ 최종 번역\n대기");
        reverseEnglish.setText("④ 역번역 영어\n대기");
        reverseSource.setText("⑤ 역번역 검증\n대기");
        status.setText("1/4 원문을 영어로 번역 중…");
        translator.translate(phrase, from, "en", new TranslationClient.Callback() {
            @Override public void onError(String message) { fail("영어 번역 실패: " + message); }
            @Override public void onResult(String english) {
                if (destroyed) return;
                intermediate.setText("② 영어 중간 번역\n" + english);
                status.setText("2/4 영어를 최종 언어로 번역 중…");
                translator.translate(english, "en", to, new TranslationClient.Callback() {
                    @Override public void onError(String message) { fail("최종 번역 실패: " + message); }
                    @Override public void onResult(String finalText) {
                        if (destroyed) return;
                        result.setText("③ 최종 번역 (" + to + ")\n" + finalText);
                        lastTranslation = finalText;
                        lastVoiceLocale = "ko".equals(to) ? Locale.KOREA : Locale.SIMPLIFIED_CHINESE;
                        speak(finalText, lastVoiceLocale);
                        status.setText("3/4 최종 문장을 영어로 역번역 중…");
                        translator.translate(finalText, to, "en", new TranslationClient.Callback() {
                            @Override public void onError(String message) { fail("영어 역번역 실패: " + message); }
                            @Override public void onResult(String backEnglish) {
                                if (destroyed) return;
                                reverseEnglish.setText("④ 역번역 영어\n" + backEnglish);
                                status.setText("4/4 영어를 원래 언어로 역번역 중…");
                                translator.translate(backEnglish, "en", from, new TranslationClient.Callback() {
                                    @Override public void onError(String message) { fail("검증 번역 실패: " + message); }
                                    @Override public void onResult(String backOriginal) {
                                        if (destroyed) return;
                                        reverseSource.setText("⑤ 역번역 검증 (" + from + ")\n" + backOriginal
                                            + "\n원문과 의미가 같은지 직접 확인해 주세요.");
                                        status.setText("4단계 번역 완료 · 원문과 역번역을 비교해 주세요");
                                        finish();
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });
    }

    private void fail(String reason) {
        if (destroyed) return;
        status.setText(reason);
        toast(reason);
        finish();
    }

    private void finish() {
        busy = false;
        setControlsEnabled(true);
        replay.setEnabled(!lastTranslation.isEmpty());
    }

    private void setControlsEnabled(boolean value) {
        koreanMic.setEnabled(value);
        chineseMic.setEnabled(value);
        translateTyped.setEnabled(value);
    }

    private void speak(String text, Locale locale) {
        if (text == null || text.isEmpty()) return;
        if (!ttsReady) { toast("음성 합성 엔진을 준비 중입니다"); return; }
        int code = tts.setLanguage(locale);
        if (code == TextToSpeech.LANG_NOT_SUPPORTED || code == TextToSpeech.LANG_MISSING_DATA) {
            toast("언어 음성팩을 휴대폰에 설치해 주세요"); return;
        }
        tts.setSpeechRate(0.92f);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "maru_wechat_relay");
    }

    @Override public void onInit(int statusCode) {
        ttsReady = statusCode == TextToSpeech.SUCCESS;
    }

    private TextView label(String value, int size, int color) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private TextView box(LinearLayout root, String value) {
        TextView v = label(value, 17, Color.rgb(20, 45, 40));
        v.setPadding(dp(13), dp(12), dp(13), dp(12));
        v.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(9), 0, 0);
        root.addView(v, params);
        return v;
    }

    private Button button(String value, LinearLayout root, Runnable handler) {
        Button b = new Button(this);
        b.setText(value); b.setTextSize(17);
        b.setAllCaps(false);
        b.setOnClickListener(v -> handler.run());
        root.addView(b, new LinearLayout.LayoutParams(-1, dp(60)));
        return b;
    }

    private View spacer(int size) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(size)));
        return v;
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        destroyed = true;
        if (tts != null) { tts.stop(); tts.shutdown(); }
        translator.close();
        super.onDestroy();
    }
}
