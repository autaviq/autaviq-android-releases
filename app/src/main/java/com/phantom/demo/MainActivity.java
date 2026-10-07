package com.phantom.demo;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_AUDIO = 42;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private boolean wantsListening = false;
    private boolean restartScheduled = false;
    private int sessionCount = 0;

    private SceneView sceneView;
    private TextView sceneText, promptText, heardText, statusText, debugText;
    private Button micButton;
    private int scene = 0;

    private final String[] labels = {"HOUSE", "BUS STOP", "BUS", "WORK", "HOME"};
    private final String[] prompts = {
            "Try: “John leaves his house and walks to the bus stop.”",
            "Try: “He reaches the bus stop and waits for the bus.”",
            "Try: “The bus arrives and John gets on.”",
            "Try: “Eventually John arrives at work.”",
            "Try: “John finishes work and goes home.”"
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(7,17,31));
        getWindow().setNavigationBarColor(Color.rgb(7,17,31));
        buildUi();
        setupSpeech();
        setScene(0, "Ready");
    }

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView text(String value, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setPadding(dp(4), dp(3), dp(4), dp(3));
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setMinHeight(dp(50));
        return b;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(12),dp(14),dp(14));
        root.setBackgroundColor(Color.rgb(7,17,31));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("Project Phantom", 20, Color.WHITE, true);
        TextView sub = text("Android voice demo · 0.1", 11, Color.rgb(151,169,195), false);
        titleBox.addView(title);
        titleBox.addView(sub);

        statusText = text("Mic off", 12, Color.rgb(203,213,225), true);
        statusText.setGravity(Gravity.CENTER);

        top.addView(titleBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f));
        top.addView(statusText, new LinearLayout.LayoutParams(dp(110),dp(38)));
        root.addView(top);

        sceneView = new SceneView(this);
        LinearLayout.LayoutParams stageLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f);
        stageLp.topMargin=dp(8);
        stageLp.bottomMargin=dp(8);
        root.addView(sceneView, stageLp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14),dp(10),dp(14),dp(10));
        card.setBackgroundColor(Color.rgb(17,27,46));

        sceneText = text("HOUSE",13,Color.rgb(125,211,252),true);
        promptText = text(prompts[0],17,Color.WHITE,true);
        heardText = text("Tap Start mic and speak naturally.",13,Color.rgb(166,184,209),false);
        card.addView(sceneText);
        card.addView(promptText);
        card.addView(heardText);
        root.addView(card);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(0,dp(8),0,0);

        Button back=button("← Back");
        Button next=button("Next →");
        Button reset=button("Reset");
        micButton=button("🎙 Start mic");

        controls.addView(back,new LinearLayout.LayoutParams(0,dp(54),1f));
        controls.addView(micButton,new LinearLayout.LayoutParams(0,dp(54),1.35f));
        controls.addView(next,new LinearLayout.LayoutParams(0,dp(54),1f));
        root.addView(controls);
        root.addView(reset,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(48)));

        debugText = text("",11,Color.rgb(148,163,184),false);
        debugText.setVisibility(View.GONE);
        ScrollView debugScroll = new ScrollView(this);
        debugScroll.addView(debugText);
        root.addView(debugScroll,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(72)));

        title.setOnLongClickListener(v -> {
            debugText.setVisibility(debugText.getVisibility()==View.VISIBLE ? View.GONE : View.VISIBLE);
            return true;
        });

        back.setOnClickListener(v -> setScene(scene-1,"Manual back"));
        next.setOnClickListener(v -> setScene(scene+1,"Manual next"));
        reset.setOnClickListener(v -> setScene(0,"Reset"));
        micButton.setOnClickListener(v -> {
            if(wantsListening) stopListening();
            else requestAndStart();
        });

        setContentView(root);
    }

    private void setupSpeech() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.setText("Voice unavailable");
            heardText.setText("No Android speech recogniser found. Use Back / Next for the demo.");
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            } else {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            }
        } catch (Exception e) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        }

        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle p) {
                statusText.setText("● Listening");
                micButton.setText("■ Stop mic");
            }

            @Override public void onBeginningOfSpeech() {
                statusText.setText("● Hearing you");
            }

            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] b) {}

            @Override public void onEndOfSpeech() {
                statusText.setText("Processing…");
            }

            @Override public void onError(int error) {
                if (!wantsListening) return;
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    stopListening();
                    heardText.setText("Microphone permission is required.");
                    return;
                }
                scheduleRestart(error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 450 : 180);
            }

            @Override public void onResults(Bundle results) {
                handleResults(results,true);
                if(wantsListening) scheduleRestart(120);
            }

            @Override public void onPartialResults(Bundle partial) {
                handleResults(partial,false);
            }

            @Override public void onEvent(int eventType, Bundle params) {}
        });

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-GB");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
    }

    private void requestAndStart() {
        if(Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_AUDIO);
            return;
        }
        wantsListening=true;
        startSession();
    }

    @Override public void onRequestPermissionsResult(int req,String[] perms,int[] grants) {
        super.onRequestPermissionsResult(req,perms,grants);
        if(req==REQ_AUDIO && grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED) {
            wantsListening=true;
            startSession();
        } else {
            heardText.setText("Microphone permission was not granted.");
        }
    }

    private void startSession() {
        if(!wantsListening || recognizer==null) return;
        restartScheduled=false;
        try { recognizer.cancel(); } catch(Exception ignored) {}

        handler.postDelayed(() -> {
            if(!wantsListening) return;
            try {
                sessionCount++;
                recognizer.startListening(recognizerIntent);
                updateDebug("Session "+sessionCount);
            } catch(Exception e) {
                scheduleRestart(350);
            }
        },80);
    }

    private void scheduleRestart(long ms) {
        if(!wantsListening || restartScheduled) return;
        restartScheduled=true;
        handler.postDelayed(this::startSession,ms);
    }

    private void stopListening() {
        wantsListening=false;
        restartScheduled=false;
        handler.removeCallbacksAndMessages(null);
        if(recognizer!=null) {
            try { recognizer.cancel(); } catch(Exception ignored) {}
        }
        statusText.setText("Mic off");
        micButton.setText("🎙 Start mic");
    }

    private void handleResults(Bundle b, boolean isFinal) {
        if(b==null) return;
        ArrayList<String> list=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if(list==null || list.isEmpty()) return;

        String best=list.get(0);
        heardText.setText((isFinal ? "Heard: " : "Hearing: ")+"“"+best+"”");

        Decision d=decide(best);
        if(d.target>=0 && d.target!=scene && (isFinal || d.conf>=95)) {
            setScene(d.target,d.reason+" · "+d.conf+"%");
        } else {
            updateDebug(d.reason+" · "+d.conf+"% · "+best);
        }
    }

    private static class Decision {
        int target,conf;
        String reason;
        Decision(int t,int c,String r){target=t;conf=c;reason=r;}
    }

    private Decision decide(String raw) {
        String t=raw.toLowerCase(Locale.UK)
                .replaceAll("[^a-z0-9' ]"," ")
                .replaceAll("\\s+"," ")
                .trim();

        boolean back=t.matches(".*\\b(go back|back to|return to|again|take me back)\\b.*");
        boolean skip=t.matches(".*\\b(skip to|jump to|take me to|go straight to)\\b.*");
        boolean future=t.matches(".*\\b(we'll|we will|later|shortly|not yet|before we get|before he gets|before we go)\\b.*");

        int target=-1,conf=0;

        if(t.matches(".*\\b(goes|go|heads|head|returns|return|went|back) home\\b.*")
                || t.matches(".*\\b(finishes|finished|leaves|left) work.*\\bhome\\b.*")) {
            target=4; conf=99;
        }

        if(t.matches(".*\\b(arrives|arrived|gets|got|reaches|reached) (at )?work\\b.*")
                || t.matches(".*\\b(enters|entered) (the )?office\\b.*")
                || t.matches(".*\\bat work\\b.*")) {
            target=3; conf=98;
        }

        if(t.matches(".*\\b(gets|got|boards|boarded) on (the )?bus\\b.*")
                || t.matches(".*\\bon the bus\\b.*")
                || t.matches(".*\\bbus arrives.*(gets on|boards)\\b.*")) {
            target=2; conf=99;
        }

        if(t.matches(".*\\bbus stop\\b.*")
                || t.matches(".*\\b(wait|waits|waiting|waited) for (the )?bus\\b.*")) {
            target=1; conf=98;
        }

        if(t.matches(".*\\b(leaves|left|starts from) (his |the )?(house|home)\\b.*")
                || t.matches(".*\\b(at|in) (the )?house\\b.*")) {
            target=0; conf=96;
        }

        // Destination beats an earlier source-location mention.
        if(t.matches(".*\\b(walks|walked|walking|heads|headed|goes|went|reaches|reached) (to|towards|toward) (the )?bus stop\\b.*")) {
            target=1; conf=99;
        }

        if(t.matches(".*\\bbus.*\\b(arrives|arrived|reaches|reached) (at )?work\\b.*")) {
            target=3; conf=99;
        }

        if(back || skip) {
            if(t.contains("bus stop")) {
                target=1; conf=99;
            } else if(t.matches(".*\\bbus\\b.*")) {
                target=2; conf=99;
            } else if(t.matches(".*\\b(work|office)\\b.*")) {
                target=3; conf=99;
            } else if(t.matches(".*\\bhome\\b.*")) {
                target=4; conf=99;
            } else if(t.matches(".*\\bhouse\\b.*")) {
                target=0; conf=99;
            } else if(back) {
                target=Math.max(0,scene-1); conf=99;
            }
        }

        if(target<0) return new Decision(-1,0,"UNKNOWN");
        if(future && target>scene) return new Decision(scene,96,"Future mention — stay");
        if(target<scene && !back && conf<96) return new Decision(scene,conf,"Backward mention — stay");
        if(Math.abs(target-scene)>1 && !back && !skip && conf<98) return new Decision(scene,conf,"Large jump blocked");

        return new Decision(target,conf,target==scene ? "Stay" : "Move");
    }

    private void setScene(int s,String why) {
        scene=Math.max(0,Math.min(labels.length-1,s));
        sceneText.setText(labels[scene]);
        promptText.setText(prompts[scene]);
        sceneView.scene=scene;
        sceneView.invalidate();

        try {
            Vibrator v=(Vibrator)getSystemService(VIBRATOR_SERVICE);
            if(v!=null && v.hasVibrator()) {
                if(Build.VERSION.SDK_INT>=26) v.vibrate(VibrationEffect.createOneShot(25,VibrationEffect.DEFAULT_AMPLITUDE));
                else v.vibrate(25);
            }
        } catch(Exception ignored) {}

        updateDebug(labels[scene]+" · "+why);
    }

    private void updateDebug(String s) {
        debugText.setText("Scene: "+labels[scene]
                +"\nMic wanted: "+wantsListening
                +"\nSpeech sessions: "+sessionCount
                +"\nLast: "+s);
    }

    @Override protected void onDestroy() {
        wantsListening=false;
        if(recognizer!=null) {
            try { recognizer.destroy(); } catch(Exception ignored) {}
        }
        super.onDestroy();
    }

    static class SceneView extends View {
        int scene=0;
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);

        SceneView(Activity c) {
            super(c);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(7);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            setBackgroundColor(Color.rgb(128,198,238));
        }

        void fill(Canvas c,int color,float l,float t,float r,float b) {
            p.setColor(color);
            p.setStyle(Paint.Style.FILL);
            c.drawRect(l,t,r,b,p);
        }

        void txt(Canvas c,String s,float x,float y,float size,int color) {
            p.setColor(color);
            p.setTextSize(size);
            p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            c.drawText(s,x,y,p);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w=getWidth(),h=getHeight(),ground=h*.64f;

            fill(c,Color.rgb(135,204,244),0,0,w,ground);
            fill(c,Color.rgb(121,185,84),0,ground,w,h);

            p.setColor(Color.rgb(255,229,122));
            c.drawCircle(w-55,55,31,p);

            fill(c,Color.rgb(61,72,88),0,h*.76f,w,h*.91f);
            stroke.setColor(Color.rgb(238,216,135));
            stroke.setStrokeWidth(5);
            for(float x=-20;x<w;x+=70) c.drawLine(x,h*.835f,x+38,h*.835f,stroke);

            drawHouse(c,w,h);
            drawStop(c,w,h);
            if(scene==2) drawBus(c,w,h,w*.2f);
            if(scene==3) drawOffice(c,w,h);
            drawPerson(c,w,h,personX(w));

            p.setColor(Color.argb(190,5,15,28));
            c.drawRoundRect(new RectF(12,12,160,54),12,12,p);
            txt(c,label(),25,41,19,Color.WHITE);
        }

        String label() {
            return new String[]{"HOUSE","BUS STOP","BUS","WORK","HOME"}[scene];
        }

        float personX(float w) {
            if(scene==0 || scene==4) return w*.23f;
            if(scene==1) return w*.73f;
            if(scene==2) return w*.52f;
            return w*.58f;
        }

        void drawHouse(Canvas c,float w,float h) {
            float l=w*.05f,top=h*.45f,r=l+w*.26f,b=h*.76f;
            p.setColor(Color.rgb(243,213,166));
            c.drawRect(l,top,r,b,p);

            Path roof=new Path();
            roof.moveTo(l-12,top);
            roof.lineTo((l+r)/2,top-h*.16f);
            roof.lineTo(r+12,top);
            roof.close();
            p.setColor(Color.rgb(159,63,63));
            c.drawPath(roof,p);

            p.setColor(Color.rgb(107,66,38));
            c.drawRect(l+w*.105f,b-h*.14f,l+w*.16f,b,p);
        }

        void drawStop(Canvas c,float w,float h) {
            float x=w*.86f;
            p.setColor(Color.rgb(74,85,104));
            c.drawRect(x,h*.48f,x+8,h*.75f,p);
            p.setColor(Color.rgb(37,99,235));
            c.drawRoundRect(new RectF(x-31,h*.39f,x+39,h*.49f),8,8,p);
            txt(c,"BUS",x-20,h*.455f,20,Color.WHITE);
        }

        void drawBus(Canvas c,float w,float h,float x) {
            float y=h*.57f,bw=w*.52f,bh=h*.19f;
            p.setColor(Color.rgb(239,68,68));
            c.drawRoundRect(new RectF(x,y,x+bw,y+bh),18,18,p);

            p.setColor(Color.rgb(188,230,255));
            float wx=x+18;
            for(int i=0;i<4;i++) {
                c.drawRoundRect(new RectF(wx+i*bw*.2f,y+16,wx+i*bw*.2f+bw*.15f,y+bh*.45f),5,5,p);
            }

            p.setColor(Color.rgb(17,24,39));
            c.drawCircle(x+bw*.2f,y+bh+9,18,p);
            c.drawCircle(x+bw*.8f,y+bh+9,18,p);
        }

        void drawOffice(Canvas c,float w,float h) {
            float l=w*.67f,t=h*.35f,r=w*.95f,b=h*.75f;
            p.setColor(Color.rgb(203,213,225));
            c.drawRect(l,t,r,b,p);
            txt(c,"WORK",l+18,t+35,20,Color.rgb(30,41,59));

            p.setColor(Color.rgb(59,130,246));
            for(int yy=0;yy<3;yy++) {
                for(int xx=0;xx<2;xx++) {
                    c.drawRect(l+22+xx*48,t+52+yy*38,l+50+xx*48,t+76+yy*38,p);
                }
            }
        }

        void drawPerson(Canvas c,float w,float h,float x) {
            if(scene==2) return;
            float y=h*.54f;
            stroke.setColor(Color.rgb(17,24,39));
            stroke.setStrokeWidth(7);

            p.setColor(Color.rgb(241,194,155));
            c.drawCircle(x,y,18,p);
            c.drawLine(x,y+20,x,y+77,stroke);
            c.drawLine(x,y+36,x-27,y+60,stroke);
            c.drawLine(x,y+36,x+27,y+60,stroke);
            c.drawLine(x,y+77,x-23,y+113,stroke);
            c.drawLine(x,y+77,x+23,y+113,stroke);
        }
    }
}
