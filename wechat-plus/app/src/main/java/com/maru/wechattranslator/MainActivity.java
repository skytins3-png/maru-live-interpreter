package com.maru.wechattranslator;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int MIC_REQUEST=21;
    private final int blue=0xff247ce7, ink=0xff1b3150, soft=0xfff5f8fc;
    private boolean incoming=true, autoSpeak=false, slow=false, autoLanguage=false, faceMode=false, textMode=false, awaitingOverlay=false;
    private TranslateEngine translator;
    private SpeechInput speech;
    private TextToSpeech tts;
    private boolean speechReady;
    private SharedPreferences prefs;
    private TextView languageLeft, languageRight, direction, titleSource, titleTarget, english, reverse, status, historyHint, micCaption;
    private EditText input, output;
    private Button micButton, listenButton, changeButton, faceButton, textButton;
    private LinearLayout historyPane;
    private int sequence;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs=getSharedPreferences("maru_wechat_05",MODE_PRIVATE);
        autoSpeak=prefs.getBoolean("autoSpeak",false);
        slow=prefs.getBoolean("slow",false);
        autoLanguage=prefs.getBoolean("autoLanguage",false);
        translator=new TranslateEngine();
        speech=new SpeechInput(this);
        tts=new TextToSpeech(this,code->runOnUiThread(()->speechReady=code==TextToSpeech.SUCCESS));
        render();
    }
    private int dp(float v) { return Math.round(v*getResources().getDisplayMetrics().density); }
    private LinearLayout linear(boolean vertical) {
        LinearLayout a=new LinearLayout(this); a.setOrientation(vertical?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL); return a;
    }
    private LinearLayout.LayoutParams params(int w,int h) { return new LinearLayout.LayoutParams(w<0?w:dp(w), h<0?h:dp(h)); }
    private TextView text(String s,int sp,int color,boolean bold) {
        TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private GradientDrawable background(int color,float radius) {
        GradientDrawable a=new GradientDrawable();a.setColor(color);a.setCornerRadius(dp(radius));return a;
    }
    private Button button(String s,int color) {
        Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextSize(14);b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(ColorStateList.valueOf(color));return b;
    }
    private void addGap(LinearLayout root,int height) {
        View gap=new View(this);root.addView(gap,new LinearLayout.LayoutParams(-1,dp(height)));
    }
    private TextView sectionTitle(String s) {
        TextView v=text(s,15,ink,true); v.setPadding(dp(3),dp(6),dp(3),dp(5));return v;
    }
    private void render() {
        LinearLayout root=linear(true);root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(15),dp(6),dp(15),dp(10));setContentView(root);
        LinearLayout top=linear(false);top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout brand=linear(true);
        brand.addView(text("MARU  통역+",22,ink,true));
        brand.addView(text("중국어 · 한국어 대화 모드",12,0xff8a97ab,false));
        top.addView(brand,new LinearLayout.LayoutParams(0,dp(62),1));
        Button history=button("기록",0xff64738e);
        top.addView(history,params(68,48));history.setOnClickListener(v->showHistory());
        Button settings=button("설정",0xff526f94);
        top.addView(settings,params(68,48));settings.setOnClickListener(v->showSettings());
        root.addView(top);

        LinearLayout langs=linear(false);langs.setGravity(Gravity.CENTER_VERTICAL);
        langs.setPadding(dp(9),dp(8),dp(9),dp(8)); langs.setBackground(background(soft,28));
        languageLeft=text("중국어\n(중국 본토)",18,ink,true);
        languageLeft.setGravity(Gravity.CENTER);
        langs.addView(languageLeft,new LinearLayout.LayoutParams(0,dp(72),1));
        changeButton=button("⇄",0xffdfeafa);changeButton.setTextSize(24);changeButton.setTextColor(blue);
        langs.addView(changeButton,params(56,55));changeButton.setOnClickListener(v->changeDirection(!incoming));
        languageRight=text("한국어",18,ink,true);languageRight.setGravity(Gravity.CENTER);
        langs.addView(languageRight,new LinearLayout.LayoutParams(0,dp(72),1));
        root.addView(langs);addGap(root,9);

        LinearLayout modes=linear(false);
        textButton=button("대화 / 텍스트",0xff4b7cae);
        faceButton=button("맞대면 보기",0xff637a9a);
        Button openWeChat=button("위챗 열기",0xff178875);
        modes.addView(textButton,new LinearLayout.LayoutParams(0,dp(45),1));
        modes.addView(faceButton,new LinearLayout.LayoutParams(0,dp(45),1));
        modes.addView(openWeChat,new LinearLayout.LayoutParams(0,dp(45),1));
        textButton.setTextSize(12);faceButton.setTextSize(12);openWeChat.setTextSize(12);
        textButton.setOnClickListener(v->{
            textMode=!textMode;
            inform(textMode?"텍스트 모드: 문장을 쓰고 번역을 누르세요.":"대화 모드: 큰 마이크를 누르고 한 문장씩 말하세요.");
            focusTextMode();
        });
        faceButton.setOnClickListener(v->{
            faceMode=!faceMode;
            output.setRotation(faceMode?180f:0f);
            faceButton.setText(faceMode?"맞대면 해제":"맞대면 보기");
            inform(faceMode?"번역문을 상대가 읽도록 180도 회전했습니다.":"맞대면 표시를 해제했습니다.");
        });
        openWeChat.setOnClickListener(v->openWeChat());
        root.addView(modes);

        ScrollView scroll=new ScrollView(this);
        LinearLayout content=linear(true);
        content.setPadding(dp(4),dp(9),dp(4),dp(8));scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        direction=text("",17,0xff2573cb,true);
        direction.setGravity(Gravity.CENTER);content.addView(direction);addGap(content,8);
        titleSource=sectionTitle("중국어 원문");content.addView(titleSource);
        input=new EditText(this);input.setTextSize(18);input.setMinLines(2);input.setMaxLines(6);
        input.setPadding(dp(11),dp(10),dp(11),dp(10));
        input.setBackground(background(0xffeff5fc,14));
        content.addView(input,params(-1,95));addGap(content,8);
        TextView translateNote=text("영어 중간 번역",13,0xff63758b,true);content.addView(translateNote);
        english=text("영어 중간 번역 대기",15,0xff4b5c73,false);
        english.setPadding(dp(10),dp(7),dp(10),dp(7));english.setBackground(background(0xfff7f8fa,11));
        content.addView(english,params(-1,-2));addGap(content,12);
        titleTarget=sectionTitle("한국어 번역");content.addView(titleTarget);
        output=new EditText(this);output.setTextSize(20);output.setMinLines(2);output.setMaxLines(6);
        output.setPadding(dp(12),dp(12),dp(12),dp(12));
        output.setHint("번역 후 여기에서 확인하고 수정하세요");
        output.setBackground(background(0xffeef6ff,14));
        content.addView(output,params(-1,105));addGap(content,6);
        Button recheck=button("✎ 수정한 문장 다시 역번역",0xff537eaf);
        content.addView(recheck,params(-1,45));recheck.setOnClickListener(v->recheck());
        content.addView(sectionTitle("역번역 검토 · 일치해도 정확성을 보장하지 않음"));
        reverse=text("역번역 대기",16,0xff495a6f,false);
        reverse.setPadding(dp(10),dp(9),dp(10),dp(9));reverse.setBackground(background(0xfffff9ef,12));
        content.addView(reverse,params(-1,-2));addGap(content,8);
        LinearLayout actions=linear(false);
        Button play=button("▶ 번역 음성",0xffbd4e7f);Button copy=button("복사",0xff708098);Button share=button("위챗 문자",0xff23836e);
        actions.addView(play,new LinearLayout.LayoutParams(0,dp(48),1.2f));
        actions.addView(copy,new LinearLayout.LayoutParams(0,dp(48),0.7f));
        actions.addView(share,new LinearLayout.LayoutParams(0,dp(48),0.9f));
        content.addView(actions);
        play.setOnClickListener(v->speak(output.getText().toString(),incoming?Locale.KOREAN:Locale.SIMPLIFIED_CHINESE));
        copy.setOnClickListener(v->copyOutput());
        share.setOnClickListener(v->shareOutput());
        Button examples=button("자주 쓰는 문장",0xff627fa0);
        content.addView(examples,params(-1,45));
        examples.setOnClickListener(v->showExamples());
        historyHint=text("대화는 기기 안에 최대 40개 저장되며 설정에서 지울 수 있습니다.",12,0xff8492a3,false);
        content.addView(historyHint);
        addGap(content,8);
        Button overlay=button("▣ 위챗 화면 위 작은 통역창",0xff597cb8);
        overlay.setOnClickListener(v->openOverlay());content.addView(overlay,params(-1,47));
        TextView warning=text("위챗 통화 오디오를 직접 읽거나 번역 음성을 위챗 마이크로 보내는 기능은 제공되지 않습니다. 통화 중에는 마이크가 제한될 수 있어요.",12,0xffa55a32,false);
        warning.setPadding(dp(3),dp(10),dp(3),dp(5));content.addView(warning);

        LinearLayout bottom=linear(true);
        bottom.setGravity(Gravity.CENTER);bottom.setBackground(background(0xfffafcff,20));
        micCaption=text("아래 마이크를 누르고 말해 보세요",14,0xff60718a,false);
        micCaption.setGravity(Gravity.CENTER);bottom.addView(micCaption);
        LinearLayout dock=linear(false);dock.setGravity(Gravity.CENTER_VERTICAL);
        listenButton=button("중국어",0xff6989b1);
        dock.addView(listenButton,new LinearLayout.LayoutParams(0,dp(62),1));
        micButton=button("🎤",blue);micButton.setTextSize(29);
        dock.addView(micButton,params(94,78));
        Button run=button("번역",0xff16856d);
        dock.addView(run,new LinearLayout.LayoutParams(0,dp(62),1));
        bottom.addView(dock);
        status=text("",13,0xff447398,false);
        status.setGravity(Gravity.CENTER);status.setMaxLines(3);
        bottom.addView(status,params(-1,-2));root.addView(bottom);
        listenButton.setOnClickListener(v->changeDirection(!incoming));
        micButton.setOnClickListener(v->startListening());
        run.setOnClickListener(v->translate());
        changeDirection(true);
        inform("한 문장씩 말하면 번역해 줍니다. 통화 연결 기능은 아닙니다.");
    }
    private void focusTextMode() {
        if(textMode){input.requestFocus();micCaption.setText("텍스트를 입력하고 오른쪽 번역 버튼을 누르세요");}
        else {input.clearFocus();micCaption.setText("큰 마이크를 눌러 중국어 또는 한국어를 말하세요");}
    }
    private void changeDirection(boolean remoteChinese) {
        ++sequence;
        incoming=remoteChinese;
        if(speech!=null)speech.stop();
        languageLeft.setText(remoteChinese?"중국어\n(중국 본토)":"한국어");
        languageRight.setText(remoteChinese?"한국어":"중국어\n(중국 본토)");
        direction.setText(remoteChinese?"상대방 중국어  →  한국어로 듣기":"내 한국어  →  중국어로 말하기");
        titleSource.setText(remoteChinese?"중국어 원문":"한국어 원문");
        titleTarget.setText(remoteChinese?"한국어 최종 번역":"중국어 최종 번역");
        listenButton.setText(remoteChinese?"중국어 듣기":"한국어 말하기");
        input.setText(remoteChinese?"你好，你吃饭了吗？":"저녁 먹었어? 오늘은 어땠어?");
        output.setText("");english.setText("영어 중간 번역 대기");
        reverse.setText("역번역 대기");output.setRotation(faceMode?180f:0f);
    }
    private void startListening() {
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC_REQUEST);return;
        }
        int current=++sequence;
        inform(autoLanguage?"자동 언어 감지 시험 중 · 엔진에서 지원하지 않을 수 있습니다.":"마이크를 듣고 있습니다. 말을 마치면 번역합니다.");
        micCaption.setText("● 듣는 중 · 잠시 멈추면 인식됩니다");
        speech.listen(incoming,autoLanguage,new SpeechInput.Listener(){
            public void partial(String result) {
                if(current!=sequence)return;
                input.setText(result);
            }
            public void recognized(String result,boolean sourceChinese) {
                if(current!=sequence)return;
                if(sourceChinese!=incoming)changeDirection(sourceChinese);
                input.setText(result);
                micCaption.setText("마이크 준비 완료");
                translate();
            }
            public void error(String text) {
                if(current!=sequence)return;
                micCaption.setText("🎤 다시 누르면 재시도");
                inform(text);
            }
        });
    }
    private void translate() {
        String original=input.getText().toString().trim();
        if(original.isEmpty()){inform("번역할 말을 입력해 주세요.");return;}
        boolean side=incoming;
        int current=++sequence;
        speech.stop();
        inform("영어 경유 번역 · 역번역 진행 중… 첫 실행 시 다운로드가 필요합니다.");
        translator.translate(original,side,r->{
            if(current!=sequence||isFinishing()||isDestroyed())return;
            english.setText(r.english);
            output.setText(r.translated);
            reverse.setText(r.back);
            saveHistory(original,r.translated,side);
            inform("번역 완료. 원문과 역번역을 비교하고 수정하세요.");
            if(autoSpeak)speak(r.translated,side?Locale.KOREAN:Locale.SIMPLIFIED_CHINESE);
        },error->{if(current==sequence)inform(error);});
    }
    private void recheck() {
        String content=output.getText().toString().trim();
        if(content.isEmpty()){inform("수정하거나 번역한 문장이 없습니다.");return;}
        int current=++sequence;
        inform("수정문 역번역 중…");
        translator.recheck(content,incoming,
            back->{if(current==sequence){reverse.setText(back);inform("수정문 역번역 완료 · 의미를 확인해 주세요.");}},
            error->{if(current==sequence)inform(error);});
    }
    private void speak(String s,Locale locale) {
        if(s==null||s.trim().isEmpty()){inform("먼저 번역문을 작성하세요.");return;}
        if(!speechReady||tts==null){inform("음성 엔진이 준비되지 않았습니다. 갤럭시 텍스트 읽어주기 설정을 확인해 주세요.");return;}
        int code=tts.setLanguage(locale);
        if(code==TextToSpeech.LANG_NOT_SUPPORTED||code==TextToSpeech.LANG_MISSING_DATA) {
            inform("해당 언어 음성이 없습니다. 휴대폰 TTS 언어를 설치해 주세요.");return;
        }
        tts.setSpeechRate(slow?0.73f:0.94f);
        tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"maru-05");
        inform("스피커로 번역 음성을 듣고 있습니다. 위챗 마이크 송출은 아닙니다.");
    }
    private void copyOutput() {
        String s=output.getText().toString().trim();
        if(s.isEmpty()){inform("복사할 문장이 없습니다.");return;}
        ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE))
          .setPrimaryClip(ClipData.newPlainText("MARU 번역문",s));
        inform("번역문을 복사했습니다.");
    }
    private void shareOutput() {
        String s=output.getText().toString().trim();
        if(s.isEmpty()){inform("공유할 문장이 없습니다.");return;}
        Intent i=new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,s);i.setPackage("com.tencent.mm");
        try{startActivity(i);}catch(Exception e){
            i.setPackage(null);
            try{startActivity(Intent.createChooser(i,"번역문 공유"));}
            catch(Exception ex){inform("공유를 열 수 없습니다. 복사를 사용하세요.");}
        }
    }
    private void openWeChat() {
        Intent i=getPackageManager().getLaunchIntentForPackage("com.tencent.mm");
        if(i==null){inform("위챗이 설치되어 있지 않거나 열 수 없습니다.");return;}
        inform("위챗을 엽니다. 통화 연결·통화 음성 직접 변환은 제공되지 않습니다.");
        try{startActivity(i);}catch(Exception e){inform("위챗 실행 실패: "+e.getMessage());}
    }
    private void openOverlay() {
        if(!Settings.canDrawOverlays(this)){
            awaitingOverlay=true;
            new AlertDialog.Builder(this).setTitle("작은 통역창 권한")
                .setMessage("위챗 화면 위에 수동 번역창을 표시하려면 '다른 앱 위에 표시'만 허용하세요. 접근성 권한은 필요하지 않습니다.")
                .setPositiveButton("권한 설정",(d,w)->startActivity(
                    new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))))
                .setNegativeButton("취소",null).show();
            return;
        }
        try{
            Intent i=new Intent(this,OverlayService.class);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
            inform("위챗 화면의 작은 번역창을 실행했습니다. 수동으로만 동작합니다.");
        }catch(Exception e){inform("작은 번역창을 시작하지 못했습니다: "+e.getMessage());}
    }
    @Override protected void onResume() {
        super.onResume();
        if(awaitingOverlay) {
            awaitingOverlay=false;
            if(Settings.canDrawOverlays(this))openOverlay();
        }
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==MIC_REQUEST&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)startListening();
        else if(request==MIC_REQUEST)inform("마이크 권한 없이 텍스트 번역은 이용할 수 있습니다.");
    }
    private void showSettings() {
        String[] choices={"번역 후 음성 자동 읽기","느린 음성 재생","음성인식 자동 언어 감지 (실험적)"};
        boolean[] state={autoSpeak,slow,autoLanguage};
        new AlertDialog.Builder(this).setTitle("음성 · 대화 설정")
            .setMultiChoiceItems(choices,state,(d,which,checked)->state[which]=checked)
            .setPositiveButton("저장",(d,w)->{
                autoSpeak=state[0];slow=state[1];autoLanguage=state[2];
                prefs.edit().putBoolean("autoSpeak",autoSpeak)
                    .putBoolean("slow",slow).putBoolean("autoLanguage",autoLanguage).apply();
                inform("설정을 저장했습니다.");
            })
            .setNeutralButton("기록 지우기",(d,w)->new AlertDialog.Builder(this)
                .setTitle("대화 기록 모두 삭제")
                .setMessage("기기에 저장된 번역문을 모두 삭제할까요?")
                .setPositiveButton("삭제",(d2,w2)->{
                    prefs.edit().remove("history").apply();inform("대화 기록을 삭제했습니다.");
                }).setNegativeButton("취소",null).show())
            .setNegativeButton("취소",null).show();
    }
    private void showExamples() {
        String[] examples={"밥 먹었어?","오늘은 어떻게 지냈어?","너무 걱정하지 마.","내 말이 오해 없이 전해졌으면 좋겠어.",
           "나는 네 이야기를 끝까지 듣고 싶어.","지금 통화할 수 있어?","조금 천천히 말해 줘.","나는 너를 소중하게 생각해."};
        new AlertDialog.Builder(this).setTitle("자주 쓰는 한국어 문장").setItems(examples,(d,index)->{
            changeDirection(false);input.setText(examples[index]);translate();
        }).setNegativeButton("닫기",null).show();
    }
    private void saveHistory(String from,String to,boolean sourceChinese) {
        try{
            JSONArray old=new JSONArray(prefs.getString("history","[]"));
            JSONArray updated=new JSONArray();
            JSONObject item=new JSONObject();
            item.put("from",from);item.put("to",to);item.put("chinese",sourceChinese);
            item.put("when",new SimpleDateFormat("MM/dd HH:mm",Locale.KOREA).format(new Date()));
            updated.put(item);
            for(int i=0;i<old.length()&&i<39;i++)updated.put(old.getJSONObject(i));
            prefs.edit().putString("history",updated.toString()).apply();
        }catch(Exception ignored){}
    }
    private void showHistory() {
        try{
            JSONArray items=new JSONArray(prefs.getString("history","[]"));
            if(items.length()==0){
                new AlertDialog.Builder(this).setTitle("대화 기록")
                    .setMessage("저장된 통역 기록이 없습니다.").setPositiveButton("확인",null).show();return;
            }
            String[] lines=new String[items.length()];
            for(int i=0;i<items.length();i++){
                JSONObject a=items.getJSONObject(i);
                lines[i]=a.optString("when")+"  "+a.optString("from")+" \n→ "+a.optString("to");
            }
            new AlertDialog.Builder(this).setTitle("대화 기록 · 기기에만 저장")
                .setItems(lines,(d,i)->{
                    JSONObject a=items.optJSONObject(i);if(a==null)return;
                    changeDirection(a.optBoolean("chinese",true));
                    input.setText(a.optString("from"));output.setText(a.optString("to"));
                    reverse.setText("기록에서 불러왔습니다. 다시 검사 버튼을 눌러 확인하세요.");
                }).setNegativeButton("닫기",null).show();
        }catch(Exception e){inform("기록을 읽을 수 없습니다: "+e.getMessage());}
    }
    private void inform(String s) {
        if(status!=null)status.setText(s);
    }
    @Override protected void onDestroy() {
        ++sequence;
        if(speech!=null)speech.stop();
        if(translator!=null)translator.close();
        if(tts!=null){tts.stop();tts.shutdown();}
        super.onDestroy();
    }
}
