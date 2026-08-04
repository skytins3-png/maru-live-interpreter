package com.maru.liveinterpreter;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity implements InterpreterEngine.Listener {
    public static final String ACTION_LISTEN = "com.maru.liveinterpreter.LISTEN";
    private InterpreterEngine engine;
    private Spinner language;
    private TextView state;
    private TextView original;
    private TextView translated;
    private boolean listenWhenReady;

    private final BroadcastReceiver listenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { startListening(); }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        engine = new InterpreterEngine(this, this);
        setContentView(buildScreen());
        IntentFilter filter = new IntentFilter(ACTION_LISTEN);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(listenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        //noinspection UnspecifiedRegisterReceiverFlag -- required legacy overload on Android 8-12L.
        else registerReceiver(listenReceiver, filter);
        requestMicrophoneIfNeeded();
        listenWhenReady = ACTION_LISTEN.equals(getIntent().getAction());
    }

    private View buildScreen() {
        int pad = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(244, 250, 248));

        TextView title = text("MARU 실시간 음성 통역", 26, Color.rgb(16, 67, 60));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, matchWrap());
        TextView guide = text("자동 일상대화를 한 번 시작하면\n상대방과 형의 말을 번갈아 듣고 통역합니다.", 16, Color.DKGRAY);
        guide.setPadding(0, dp(8), 0, dp(18));
        root.addView(guide, matchWrap());

        language = new Spinner(this);
        language.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, LanguageOption.values()));
        root.addView(language, new LinearLayout.LayoutParams(-1, dp(54)));

        Button talk = new Button(this);
        talk.setText("🎤  내가 한국어로 말하기");
        talk.setTextSize(20);
        talk.setOnClickListener(v -> startListening());
        LinearLayout.LayoutParams talkParams = new LinearLayout.LayoutParams(-1, dp(72));
        talkParams.setMargins(0, dp(14), 0, dp(10));
        root.addView(talk, talkParams);

        Button remote = new Button(this);
        remote.setText("🌍  상대방 외국어 듣기 · 자동감지");
        remote.setTextSize(18);
        remote.setOnClickListener(v -> startRemoteListening());
        LinearLayout.LayoutParams remoteParams = new LinearLayout.LayoutParams(-1, dp(64));
        remoteParams.setMargins(0, 0, 0, dp(10));
        root.addView(remote, remoteParams);

        Button floating = new Button(this);
        floating.setText("자동 일상대화 시작 · 비고 화면 유지");
        floating.setOnClickListener(v -> enableFloatingButton());
        root.addView(floating, new LinearLayout.LayoutParams(-1, dp(54)));

        state = text("준비됐어요", 16, Color.rgb(18, 107, 91));
        state.setPadding(0, dp(18), 0, dp(10));
        root.addView(state, matchWrap());
        original = card("형이 말한 한국어가 여기에 표시됩니다");
        translated = card("번역된 문장이 여기에 표시됩니다");
        root.addView(original, cardParams());
        root.addView(translated, cardParams());
        return root;
    }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestMicrophoneIfNeeded(); return;
        }
        engine.setTarget((LanguageOption) language.getSelectedItem());
        engine.listenKorean();
    }

    private void startRemoteListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestMicrophoneIfNeeded(); return;
        }
        engine.listenRemoteAuto();
    }

    private void requestMicrophoneIfNeeded() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 100);
    }

    @Override protected void onResume() {
        super.onResume();
        if (listenWhenReady && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            listenWhenReady = false;
            state.postDelayed(this::startListening, 350);
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (ACTION_LISTEN.equals(intent.getAction())) {
            listenWhenReady = true;
            if (state != null) state.postDelayed(this::startListening, 250);
        }
    }

    private void enableFloatingButton() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "다른 앱 위에 표시를 허용해 주세요", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }
        Intent service = new Intent(this, FloatingInterpreterService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
        Toast.makeText(this, "자동 일상대화를 시작했어요. 비고로 돌아가세요", Toast.LENGTH_LONG).show();
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(sp); view.setTextColor(color); return view;
    }
    private TextView card(String value) {
        TextView view = text(value, 18, Color.rgb(25, 40, 38));
        view.setGravity(Gravity.CENTER_VERTICAL); view.setPadding(dp(14), dp(12), dp(14), dp(12)); view.setBackgroundColor(Color.WHITE); return view;
    }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams cardParams() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(90)); p.setMargins(0, 0, 0, dp(10)); return p; }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    @Override public void onState(String value) { state.setText(value); }
    @Override public void onOriginal(String value) { original.setText("한국어\n" + value); }
    @Override public void onTranslated(String value) { translated.setText("번역\n" + value); }
    @Override public void onDetectedLanguage(String label) {
        LanguageOption[] values = LanguageOption.values();
        for (int i = 0; i < values.length; i++) {
            if (values[i].label.equals(label)) { language.setSelection(i); break; }
        }
        state.setText("감지된 상대방 언어: " + label);
    }
    @Override public void onError(String value) { state.setText(value); Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    @Override protected void onDestroy() { unregisterReceiver(listenReceiver); engine.release(); super.onDestroy(); }
}
