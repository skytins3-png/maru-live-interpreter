package com.maru.liveinterpreter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

public final class FloatingInterpreterService extends Service {
    private static final String CHANNEL = "maru_interpreter";
    private WindowManager windowManager;
    private View bubble;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setContentTitle("MARU 실시간 통역")
                .setContentText("떠 있는 마이크 버튼을 사용 중입니다")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pending).setOngoing(true).build();
        startForeground(77, notification);
        if (Settings.canDrawOverlays(this)) showBubble(); else stopSelf();
    }

    private void showBubble() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        Button button = new Button(this);
        button.setText("통역\n🎤"); button.setTextSize(15); button.setAllCaps(false);
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(dp(78), dp(78), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END; params.x = dp(12); params.y = dp(220);
        button.setOnClickListener(v -> {
            Intent open = new Intent(this, MainActivity.class)
                    .setAction(MainActivity.ACTION_LISTEN)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(open);
        });
        final float[] start = new float[4];
        button.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) { start[0]=event.getRawX(); start[1]=event.getRawY(); start[2]=params.x; start[3]=params.y; return false; }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                params.x = (int)(start[2] - (event.getRawX()-start[0])); params.y = (int)(start[3] + (event.getRawY()-start[1]));
                windowManager.updateViewLayout(button, params); return true;
            }
            return false;
        });
        bubble = button; windowManager.addView(bubble, params);
    }

    private void createChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "실시간 통역", NotificationManager.IMPORTANCE_LOW));
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    @Override public void onDestroy() { if (bubble != null && windowManager != null) windowManager.removeView(bubble); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
