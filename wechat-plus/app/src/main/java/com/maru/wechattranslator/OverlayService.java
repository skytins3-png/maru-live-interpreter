package com.maru.wechattranslator;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Manual translator window. It does not capture or inject a WeChat call stream. */
public final class OverlayService extends Service {
 private WindowManager wm;
 private View bubble, panel;
 private WindowManager.LayoutParams bp, pp;
 private TranslateEngine translator;
 private SpeechInput speech;
 private TextToSpeech tts;
 private boolean ready, incoming=true;
 private int requestId;
 private EditText source, target;
 private TextView state, english, back;
 int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
 Button button(String label,int bg){
   Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextSize(13);
   b.setTextColor(Color.WHITE);b.setBackgroundTintList(ColorStateList.valueOf(bg));return b;
 }
 TextView text(String label,int size){
   TextView v=new TextView(this);v.setText(label);v.setTextSize(size);
   v.setTextColor(0xff1c324a);v.setPadding(dp(5),dp(5),dp(5),dp(5));return v;
 }
 @Override public void onCreate(){
   super.onCreate();
   if(!Settings.canDrawOverlays(this)){stopSelf();return;}
   translator=new TranslateEngine();speech=new SpeechInput(this);
   tts=new TextToSpeech(this,x->ready=x==TextToSpeech.SUCCESS);
   NotificationManager nm=getSystemService(NotificationManager.class);
   nm.createNotificationChannel(new NotificationChannel("maru_plus","MARU 통역창",NotificationManager.IMPORTANCE_LOW));
   PendingIntent open=PendingIntent.getActivity(this,9,new Intent(this,MainActivity.class),
     PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
   startForeground(206,new Notification.Builder(this,"maru_plus")
     .setContentTitle("MARU 위챗 수동 통역창").setContentText("위챗 통화 음성 직접 전송 아님")
     .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentIntent(open).setOngoing(true).build());
   wm=(WindowManager)getSystemService(WINDOW_SERVICE);
   makeBubble();makePanel();
 }
 void makeBubble(){
   Button b=button("中 ↔ 한",0xff247ce7);
   bubble=b;
   bp=new WindowManager.LayoutParams(dp(75),dp(65),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
   bp.gravity=Gravity.TOP|Gravity.END;bp.x=dp(8);bp.y=dp(170);
   wm.addView(bubble,bp);
   b.setOnTouchListener(new View.OnTouchListener(){
     float downX,downY;int startX,startY;boolean dragged;
     public boolean onTouch(View v,MotionEvent e){
       switch(e.getActionMasked()){
         case MotionEvent.ACTION_DOWN:
           downX=e.getRawX();downY=e.getRawY();startX=bp.x;startY=bp.y;dragged=false;return true;
         case MotionEvent.ACTION_MOVE:
           float dx=e.getRawX()-downX,dy=e.getRawY()-downY;
           if(Math.abs(dx)+Math.abs(dy)>dp(12))dragged=true;
           if(dragged){bp.x=Math.max(0,startX-Math.round(dx));bp.y=Math.max(0,startY+Math.round(dy));wm.updateViewLayout(bubble,bp);}
           return true;
         case MotionEvent.ACTION_UP:
           if(!dragged)visible(panel==null||panel.getParent()==null);return true;
       }
       return true;
     }
   });
 }
 void makePanel(){
   LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
   body.setBackgroundColor(0xfff4f9ff);body.setPadding(dp(11),dp(10),dp(11),dp(12));
   LinearLayout top=new LinearLayout(this);
   top.addView(text("MARU · 위챗 통역+",18),new LinearLayout.LayoutParams(0,dp(47),1));
   Button collapse=button("접기",0xff6d7d9b);top.addView(collapse,new LinearLayout.LayoutParams(dp(69),dp(47)));
   body.addView(top);collapse.setOnClickListener(v->visible(false));
   state=text("중국어 → 영어 → 한국어",14);body.addView(state);
   LinearLayout directions=new LinearLayout(this);
   Button receive=button("상대 중국어",0xff3479cf),reply=button("내 한국어",0xffab4780);
   directions.addView(receive,new LinearLayout.LayoutParams(0,dp(47),1));
   directions.addView(reply,new LinearLayout.LayoutParams(0,dp(47),1));
   body.addView(directions);receive.setOnClickListener(v->direction(true));reply.setOnClickListener(v->direction(false));
   body.addView(text("원문 · 입력 또는 음성",12));
   source=new EditText(this);source.setTextSize(17);source.setMinLines(2);
   body.addView(source,new LinearLayout.LayoutParams(-1,dp(81)));
   LinearLayout buttons=new LinearLayout(this);
   Button mic=button("🎤 음성",0xff3870b4),translate=button("영어경유 번역",0xff15876a);
   buttons.addView(mic,new LinearLayout.LayoutParams(0,dp(46),1));
   buttons.addView(translate,new LinearLayout.LayoutParams(0,dp(46),1));body.addView(buttons);
   mic.setOnClickListener(v->listen());translate.setOnClickListener(v->translate());
   body.addView(text("영어 중간 번역",12));english=text("대기 중",14);body.addView(english);
   body.addView(text("최종 번역 · 수정 가능",12));target=new EditText(this);
   target.setTextSize(18);target.setMinLines(2);body.addView(target,new LinearLayout.LayoutParams(-1,dp(90)));
   body.addView(text("역번역 검토 · 정확성 보증 아님",12));back=text("대기 중",14);body.addView(back);
   LinearLayout actions=new LinearLayout(this);
   Button play=button("▶ 읽기",0xffb64e7f),check=button("재검토",0xff6080a9),copy=button("복사",0xff607792);
   actions.addView(play,new LinearLayout.LayoutParams(0,dp(46),1));
   actions.addView(check,new LinearLayout.LayoutParams(0,dp(46),1));
   actions.addView(copy,new LinearLayout.LayoutParams(0,dp(46),1));body.addView(actions);
   play.setOnClickListener(v->speak());check.setOnClickListener(v->review());copy.setOnClickListener(v->copy());
   LinearLayout end=new LinearLayout(this);
   Button wechat=button("위챗 열기",0xff168d77),stop=button("끄기",0xff667487);
   end.addView(wechat,new LinearLayout.LayoutParams(0,dp(46),1));
   end.addView(stop,new LinearLayout.LayoutParams(0,dp(46),1));body.addView(end);
   stop.setOnClickListener(v->stopSelf());
   wechat.setOnClickListener(v->{ Intent i=getPackageManager().getLaunchIntentForPackage("com.tencent.mm");
       if(i!=null){try{startActivity(i);}catch(Exception ignored){}}visible(false); });
   ScrollView scroll=new ScrollView(this);scroll.addView(body);
   panel=scroll;
   pp=new WindowManager.LayoutParams(dp(330),dp(565),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
       WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
   pp.gravity=Gravity.TOP|Gravity.END;pp.x=dp(4);pp.y=dp(75);
   direction(true);
 }
 void visible(boolean show){
   if(panel==null||wm==null)return;
   try{
     if(show&&panel.getParent()==null)wm.addView(panel,pp);
     else if(!show&&panel.getParent()!=null)wm.removeView(panel);
   }catch(Exception ignored){}
 }
 void direction(boolean chinese){
   incoming=chinese;requestId++;speech.stop();
   state.setText(chinese?"중국어 → 영어 → 한국어":"한국어 → 영어 → 중국어");
   source.setText(chinese?"你吃饭了吗？":"밥 먹었어?");
   target.setText("");english.setText("대기 중");back.setText("대기 중");
 }
 void listen(){
   if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
     state.setText("메인 앱에서 마이크 권한을 허용하세요.");return;
   }
   boolean ch=incoming;int run=++requestId;state.setText("마이크 듣는 중 · 통화 중에는 제한될 수 있습니다.");
   speech.listen(ch,false,new SpeechInput.Listener(){
     public void partial(String s){if(run==requestId)source.setText(s);}
     public void recognized(String s,boolean detected){if(run==requestId){source.setText(s);translate();}}
     public void error(String s){if(run==requestId)state.setText(s);}
   });
 }
 void translate(){
   String s=source.getText().toString().trim();if(s.isEmpty()){state.setText("말하거나 입력해 주세요.");return;}
   boolean ch=incoming;int run=++requestId;speech.stop();state.setText("번역 및 역번역 중…");
   translator.translate(s,ch,r->{
     if(run!=requestId||state==null)return;
     english.setText(r.english);target.setText(r.translated);back.setText(r.back);
     state.setText("완료 · 뜻을 검토하세요");
   },e->{if(run==requestId&&state!=null)state.setText(e);});
 }
 void review(){
   String s=target.getText().toString().trim();int run=++requestId;
   translator.recheck(s,incoming,v->{if(run==requestId){back.setText(v);state.setText("재검토 완료");}},
      e->{if(run==requestId)state.setText(e);});
 }
 void speak(){
   String s=target.getText().toString().trim();
   if(s.isEmpty()){state.setText("번역문을 입력하세요.");return;}
   if(!ready){state.setText("음성 엔진 준비 중");return;}
   int result=tts.setLanguage(incoming?Locale.KOREAN:Locale.SIMPLIFIED_CHINESE);
   if(result==TextToSpeech.LANG_NOT_SUPPORTED||result==TextToSpeech.LANG_MISSING_DATA){
      state.setText("해당 언어 음성을 설치해 주세요.");return;
   }
   tts.setSpeechRate(0.92f);tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"maru_overlay_v05");
   state.setText("스피커 음성 재생 중 · 위챗 마이크 송출 아님");
 }
 void copy(){
   String s=target.getText().toString().trim();
   if(s.isEmpty()){state.setText("번역문이 없습니다.");return;}
   ((ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE))
       .setPrimaryClip(ClipData.newPlainText("MARU 번역문",s));state.setText("복사 완료");
 }
 @Override public int onStartCommand(Intent i,int flags,int id){return START_NOT_STICKY;}
 @Override public IBinder onBind(Intent i){return null;}
 @Override public void onDestroy(){
   ++requestId;
   if(speech!=null)speech.stop();
   if(translator!=null)translator.close();
   if(tts!=null){tts.stop();tts.shutdown();}
   if(wm!=null){
     if(panel!=null&&panel.getParent()!=null)try{wm.removeView(panel);}catch(Exception ignored){}
     if(bubble!=null&&bubble.getParent()!=null)try{wm.removeView(bubble);}catch(Exception ignored){}
   }
   state=null;super.onDestroy();
 }
}
