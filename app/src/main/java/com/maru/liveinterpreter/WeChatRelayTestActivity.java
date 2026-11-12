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
    private String lastDirectTranslation = "";
    private Locale lastVoiceLocale = Locale.SIMPLIFIED_CHINESE;
    private String source = "ko";
    private String target = "zh-CN";

    private TextView status;
    private TextView original;
    private TextView intermediate;
    private TextView result;
    private TextView reverseEnglish;
    private TextView reverseSource;
    private TextView directResult;
    private TextView comparison;
    private EditText approvedOutput;
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
        root.addView(label("영어 경유 번역과 직접 번역을 나란히 확인하고, 역번역까지 비교합니다. 확인 전에는 음성을 자동으로 재생하지 않습니다.", 15, Color.DKGRAY));
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
            lastTranslation = "";
            lastDirectTranslation = "";
            approvedOutput.setText("");
            replay.setEnabled(false);
            comparison.setText("비교 결과 초기화 · 새 문장을 번역해 주세요");
        });
        root.addView(spacer(12));

        status = label("시험 준비 완료 · 기본 방향: 한국어 → 중국어", 16, Color.rgb(14, 92, 76));
        root.addView(status);
        original = box(root, "① 음성 인식 / 원문");
        intermediate = box(root, "② 영어 중간 번역");
        result = box(root, "③ 최종 번역");
        reverseEnglish = box(root, "④ 역번역 영어");
        reverseSource = box(root, "⑤ 원래 언어로 역번역 · 의미 확인");
        directResult = box(root, "⑥ 직접 번역 (비교용)");
        comparison = box(root, "⚠ 문장 뜻을 확인한 다음 음성 읽기");
        button("영어 경유 결과 선택", root, () -> {
            if (busy || lastTranslation.isEmpty()) return;
            approvedOutput.setText(lastTranslation);
        });
        button("직접 번역 결과 선택", root, () -> {
            if (busy || lastDirectTranslation.isEmpty()) return;
            approvedOutput.setText(lastDirectTranslation);
        });
        approvedOutput = new EditText(this);
        approvedOutput.setTextSize(18);
        approvedOutput.setMinLines(2);
        approvedOutput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        approvedOutput.setHint("최종 문장을 직접 검토하거나 수정하세요");
        approvedOutput.setPadding(dp(12), dp(12), dp(12), dp(12));
        approvedOutput.setBackgroundColor(Color.WHITE);
        root.addView(approvedOutput, new LinearLayout.LayoutParams(-1, -2));
        replay = button("🔊 검토한 문장만 읽기 (직접 누를 때 재생)", root, () -> {
            if (busy) return;
            String checked = approvedOutput.getText().toString().trim();
            if (checked.isEmpty()) { toast("검토한 문장이 없습니다"); return; }
            speak(checked, lastVoiceLocale);
        });
        replay.setEnabled(false);
        root.addView(spacer(8));

        TextView notice = label("중요: 두 번역이 같고 역번역이 비슷하더라도 뜻의 정확성이 보장되지는 않습니다. 특히 사랑·서운함·화남·거절처럼 감정이 담긴 말은 상대방에게 문장의 의도를 다시 확인하세요. 이 화면은 따로 마이크/문장을 시험합니다. 위챗 통화 소리를 직접 가져오거나 상대방 통화에 음성을 주입하지 않습니다. 실제 통화는 Galaxy AI 실시간 통역을 사용하세요. 음성이나 텍스트가 외부 음성 인식/번역 서비스에 전송될 수 있고 무료 번역 서버 사용량 제한이 있습니다.", 13, Color.DKGRAY);
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
        lastDirectTranslation = "";
        approvedOutput.setText("");
        replay.setEnabled(false);
        original.setText("① 원문 (" + from + ")\n" + phrase);
        intermediate.setText("② 영어 중간 번역\n진행 중…");
        result.setText("③ 최종 번역\n대기");
        reverseEnglish.setText("④ 역번역 영어\n대기");
        reverseSource.setText("⑤ 역번역 검증\n대기");
        directResult.setText("⑥ 직접 번역\n대기");
        comparison.setText("⚠ 결과 비교와 뜻 확인이 끝나기 전에는 음성을 재생하지 않습니다");
        status.setText("1/5 원문을 영어로 번역 중…");
        translator.translate(phrase, from, "en", new TranslationClient.Callback() {
            @Override public void onError(String message) { fail("영어 번역 실패: " + message); }
            @Override public void onResult(String english) {
                if (destroyed) return;
                intermediate.setText("② 영어 중간 번역\n" + english);
                status.setText("2/5 영어를 최종 언어로 번역 중…");
                translator.translate(english, "en", to, new TranslationClient.Callback() {
                    @Override public void onError(String message) { fail("최종 번역 실패: " + message); }
                    @Override public void onResult(String finalText) {
                        if (destroyed) return;
                        result.setText("③ 최종 번역 (" + to + ")\n" + finalText);
                        lastTranslation = finalText;
                        lastVoiceLocale = "ko".equals(to) ? Locale.KOREA : Locale.SIMPLIFIED_CHINESE;
                        approvedOutput.setText(finalText);
                        status.setText("3/5 최종 문장을 영어로 역번역 중…");
                        translator.translate(finalText, to, "en", new TranslationClient.Callback() {
                            @Override public void onError(String message) { fail("영어 역번역 실패: " + message); }
                            @Override public void onResult(String backEnglish) {
                                if (destroyed) return;
                                reverseEnglish.setText("④ 역번역 영어\n" + backEnglish);
                                status.setText("4/5 영어를 원래 언어로 역번역 중…");
                                translator.translate(backEnglish, "en", from, new TranslationClient.Callback() {
                                    @Override public void onError(String message) { fail("검증 번역 실패: " + message); }
                                    @Override public void onResult(String backOriginal) {
                                        if (destroyed) return;
                                        reverseSource.setText("⑤ 역번역 검증 (" + from + ")\n" + backOriginal
                                            + "\n원문과 의미가 같은지 직접 확인해 주세요.");
                                        status.setText("5/5 원문을 직접 번역해 영어 경유 결과와 비교 중…");
                                        translator.translate(phrase, from, to, new TranslationClient.Callback() {
                                            @Override public void onError(String message) {
                                                if (destroyed) return;
                                                directResult.setText("⑥ 직접 번역 실패: " + message);
                                                comparison.setText("⚠ 비교하지 못했습니다. 자동 음성 재생 안 함. 원문과 역번역을 직접 확인하세요.");
                                                status.setText("영어 경유 번역 완료 · 직접 비교 실패");
                                                completeRelay();
                                            }
                                            @Override public void onResult(String directText) {
                                                if (destroyed) return;
                                                lastDirectTranslation = directText;
                                                directResult.setText("⑥ 직접 번역 (" + to + ")\n" + directText);
                                                boolean samePath = normalizedEqual(finalText, directText);
                                                boolean sameReturn = normalizedEqual(phrase, backOriginal);
                                                String warning = samePath
                                                    ? "영어 경유 번역과 직접 번역의 글자가 비슷합니다."
                                                    : "⚠ 두 번역이 다릅니다. 오해를 막으려면 뜻을 다시 확인해 주세요.";
                                                warning += sameReturn
                                                    ? "\n역번역은 원문과 비슷하지만 정확성 보장은 아닙니다."
                                                    : "\n⚠ 역번역이 원문과 다릅니다. 주의해서 검토하세요.";
                                                warning += "\n감정 표현은 상대방에게 뜻을 확인하세요. 자동 음성 재생은 꺼져 있습니다.";
                                                comparison.setText(warning);
                                                status.setText("5단계 비교 완료 · 결과를 읽고 원하는 문장만 재생하세요");
                                                completeRelay();
                                            }
                                        });
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
        completeRelay();
    }

    private void completeRelay() {
        busy = false;
        setControlsEnabled(true);
        replay.setEnabled(!lastTranslation.isEmpty());
    }

    // A text-only warning: similar spelling DOES NOT prove semantic equivalence.
    private static boolean normalizedEqual(String left, String right) {
        return normalizeForDisplayComparison(left).equals(normalizeForDisplayComparison(right));
    }

    private static String normalizeForDisplayComparison(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder();
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (Character.isLetterOrDigit(codePoint))
                out.appendCodePoint(Character.toLowerCase(codePoint));
            offset += Character.charCount(codePoint);
        }
        return out.toString();
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
