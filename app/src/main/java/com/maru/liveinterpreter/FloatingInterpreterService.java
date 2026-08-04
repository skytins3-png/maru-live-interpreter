package com.maru.liveinterpreter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

public final class FloatingInterpreterService extends Service implements InterpreterEngine.Listener {
    private static final String CHANNEL = "maru_interpreter_auto";
    private WindowManager windowManager;
    private Button bubble;
    private InterpreterEngine engine;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(77, buildNotification("자동 일상대화 준비 중"));
        engine = new InterpreterEngine(this, this);
        if (Settings.canDrawOverlays(this)) {
            showBubble();
            bubble.postDelayed(engine::startAutoConversation, 700);
        } else stopSelf();
    }

    private Notification buildNotification(String status) {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL)
                .setContentTitle("MARU 자동 일상대화 통역")
                .setContentText(status)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pending).setOngoing(true).build();
    }

    private void showBubble() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        bubble = new Button(this);
        bubble.setText("자동\n대화");
        bubble.setTextSize(12);
        bubble.setTextColor(Color.WHITE);
        bubble.setBackgroundColor(Color.rgb(18, 107, 91));
        bubble.setAllCaps(false);
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(dp(60), dp(60), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(10);
        params.y = dp(210);
        bubble.setOnClickListener(v -> {
            if (engine.isAutoConversation()) engine.stopAutoConversation();
            else engine.startAutoConversation();
        });
        final float[] start = new float[4];
        bubble.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                start[0] = event.getRawX(); start[1] = event.getRawY(); start[2] = params.x; start[3] = params.y; return false;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                params.x = (int) (start[2] - (event.getRawX() - start[0]));
                params.y = (int) (start[3] + (event.getRawY() - start[1]));
                windowManager.updateViewLayout(bubble, params); return true;
            }
            return false;
        });
        windowManager.addView(bubble, params);
    }

    private void update(String shortText, int color, String detail) {
        if (bubble != null) {
            bubble.setText(shortText);
            bubble.setBackgroundColor(color);
        }
        getSystemService(NotificationManager.class).notify(77, buildNotification(detail));
    }

    @Override public void onState(String state) {
        if (state.contains("상대방")) update("상대방\n듣는 중", Color.rgb(25, 118, 210), state);
        else if (state.contains("형의")) update("형의 말\n듣는 중", Color.rgb(255, 143, 0), state);
        else if (state.contains("전달")) update("번역\n전달 중", Color.rgb(123, 31, 162), state);
        else if (state.contains("중지")) update("자동대화\n중지", Color.DKGRAY, state);
        else update("자동\n대화", Color.rgb(18, 107, 91), state);
    }

    @Override public void onOriginal(String text) { }
    @Override public void onTranslated(String text) { }
    @Override public void onDetectedLanguage(String label) { update(label + "\n감지", Color.rgb(0, 121, 107), "상대방 언어: " + label); }
    @Override public void onError(String error) { update("잠시 후\n재시도", Color.rgb(198, 40, 40), error); }

    private void createChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "자동 일상대화 통역", NotificationManager.IMPORTANCE_LOW));
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    @Override public void onDestroy() {
        if (engine != null) engine.release();
        if (bubble != null && windowManager != null) windowManager.removeView(bubble);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
