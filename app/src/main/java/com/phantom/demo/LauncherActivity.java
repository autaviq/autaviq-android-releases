package com.phantom.demo;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.drawable.GradientDrawable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class LauncherActivity extends Activity {
    private static final int REQ_MIC = 1001;
    private static final int NAVY = Color.rgb(5, 18, 34);
    private static final int PANEL = Color.rgb(12, 34, 58);
    private static final int PANEL_2 = Color.rgb(15, 43, 72);
    private static final int TEXT = Color.rgb(247, 250, 252);
    private static final int MUTED = Color.rgb(166, 184, 209);
    private static final int LIME = Color.rgb(201, 255, 61);
    private static final int SKY = Color.rgb(76, 201, 255);
    private static final int PURPLE = Color.rgb(147, 100, 255);
    private static final int ORANGE = Color.rgb(255, 166, 61);
    private static final int TEAL = Color.rgb(47, 210, 181);

    private SharedPreferences prefs;
    private FrameLayout root;
    private BurstLayer burstLayer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);

        prefs = getSharedPreferences("phantom_reader_prefs", MODE_PRIVATE);

        if (prefs.getBoolean("intro_complete", false)) {
            showHome();
        } else {
            showPermissionsIntro();
        }
    }

    private int dp(float n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView txt(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(0, 0, 0, 0);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable bg(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private GradientDrawable bgStroke(int color, float radiusDp, int strokeColor, float strokeDp) {
        GradientDrawable g = bg(color, radiusDp);
        g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    private void mount(View content) {
        root = new FrameLayout(this);
        root.setBackgroundColor(NAVY);
        root.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        burstLayer = new BurstLayer(this);
        burstLayer.setClickable(false);
        root.addView(burstLayer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        setContentView(root);
    }

    private Button actionButton(String label, int color, int textColor) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(17);
        b.setTextColor(textColor);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(18), 0, dp(18), 0);
        b.setBackground(bg(color, 22));
        b.setStateListAnimator(null);
        return b;
    }

    private void playful(View view, Runnable action) {
        view.setOnClickListener(v -> {
            v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(70)
                    .withEndAction(() -> v.animate()
                            .scaleX(1.04f).scaleY(1.04f).setDuration(90)
                            .withEndAction(() -> v.animate()
                                    .scaleX(1f).scaleY(1f).setDuration(90)
                                    .withEndAction(action)
                                    .start())
                            .start())
                    .start();

            if (burstLayer != null) {
                int[] loc = new int[2];
                v.getLocationInWindow(loc);
                burstLayer.burst(loc[0] + v.getWidth() / 2f,
                        loc[1] + v.getHeight() / 2f);
            }

            if (prefs.getBoolean("ui_sounds", true)) playPop();
            if (prefs.getBoolean("ui_haptics", true)) haptic();
        });
    }

    private void haptic() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;

            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(16, 70));
            } else {
                vibrator.vibrate(16);
            }
        } catch (Exception ignored) {}
    }

    private void playPop() {
        new Thread(() -> {
            try {
                final int sampleRate = 22050;
                final int durationMs = 85;
                int count = sampleRate * durationMs / 1000;
                short[] pcm = new short[count];

                for (int i = 0; i < count; i++) {
                    double t = i / (double) sampleRate;
                    double env = Math.exp(-35.0 * t);
                    double tone = Math.sin(2.0 * Math.PI * (780.0 - 300.0 * t) * t);
                    pcm[i] = (short) (tone * env * 9500);
                }

                AudioTrack track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(pcm.length * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();

                track.write(pcm, 0, pcm.length);
                track.play();
                Thread.sleep(durationMs + 30);
                track.release();
            } catch (Exception ignored) {}
        }).start();
    }

    private void showPermissionsIntro() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(28), dp(22), dp(22));
        page.setBackgroundColor(NAVY);

        TextView brand = txt("📖  Project Phantom", 21, TEXT, true);
        page.addView(brand, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(0, dp(26), 0, 0);

        TextView icon = txt("🎙️", 58, TEXT, false);
        icon.setGravity(Gravity.CENTER);
        body.addView(icon, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(90)));

        TextView title = txt("Let stories hear you", 31, TEXT, true);
        title.setGravity(Gravity.CENTER);
        body.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(55)));

        TextView copy = txt("We use the microphone so reading aloud can make the story move, react and make sounds.", 17, MUTED, false);
        copy.setGravity(Gravity.CENTER);
        copy.setLineSpacing(dp(4), 1f);
        body.addView(copy, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(88)));

        body.addView(permissionCard("🎤", "Voice & microphone", "Used only while reading.", SKY));
        body.addView(permissionCard("🔊", "Sound & animation", "Story sounds respond to the reader.", ORANGE));
        body.addView(permissionCard("🛡️", "Privacy first", "We keep the child experience simple and private.", TEAL));

        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        page.addView(body, bodyLp);

        Button allow = actionButton("Allow microphone", LIME, Color.rgb(10, 24, 35));
        playful(allow, () -> {
            if (Build.VERSION.SDK_INT >= 23 &&
                    checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            } else {
                showWalkthrough(0);
            }
        });
        page.addView(allow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(60)));

        Button later = actionButton("Not now", PANEL_2, TEXT);
        LinearLayout.LayoutParams laterLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        laterLp.topMargin = dp(9);
        page.addView(later, laterLp);
        playful(later, () -> showWalkthrough(0));

        mount(page);
    }

    private View permissionCard(String icon, String title, String sub, int accent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(bgStroke(PANEL, 18, Color.argb(80, Color.red(accent), Color.green(accent), Color.blue(accent)), 1));

        TextView ico = txt(icon, 29, TEXT, false);
        ico.setGravity(Gravity.CENTER);
        row.addView(ico, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView a = txt(title, 16, TEXT, true);
        TextView b = txt(sub, 13, MUTED, false);
        texts.addView(a, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(25)));
        texts.addView(b, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(23)));
        row.addView(texts, new LinearLayout.LayoutParams(0, dp(52), 1f));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(78));
        lp.bottomMargin = dp(10);
        row.setLayoutParams(lp);
        return row;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == REQ_MIC) showWalkthrough(0);
    }

    private void showWalkthrough(int step) {
        String[] icons = {"📖", "✨", "👨‍👩‍👧"};
        String[] titles = {
                "Read it out loud",
                "Watch the story react",
                "Read together or independently"
        };
        String[] copies = {
                "The words stay clear and easy to follow. Just start reading naturally.",
                "Characters move, sounds play and the world responds while the sentence is being read.",
                "A parent can read with younger children, then the child can take over as confidence grows."
        };

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(24), dp(22), dp(22));
        page.setBackgroundColor(NAVY);

        TextView brand = txt("Project Phantom", 18, TEXT, true);
        page.addView(brand, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));

        LinearLayout visual = new LinearLayout(this);
        visual.setOrientation(LinearLayout.VERTICAL);
        visual.setGravity(Gravity.CENTER);
        visual.setBackground(bg(PANEL, 28));

        TextView icon = txt(icons[step], 78, TEXT, false);
        icon.setGravity(Gravity.CENTER);
        visual.addView(icon, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(130)));

        TextView title = txt(titles[step], 28, TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(dp(20), 0, dp(20), 0);
        visual.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(80)));

        TextView copy = txt(copies[step], 17, MUTED, false);
        copy.setGravity(Gravity.CENTER);
        copy.setLineSpacing(dp(4), 1f);
        copy.setPadding(dp(25), 0, dp(25), 0);
        visual.addView(copy, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(120)));

        page.addView(visual, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        for (int i = 0; i < 3; i++) {
            View dot = new View(this);
            dot.setBackground(bg(i == step ? LIME : Color.rgb(55, 76, 103), 10));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    i == step ? dp(26) : dp(10), dp(10));
            dlp.setMargins(dp(4), dp(15), dp(4), dp(15));
            dots.addView(dot, dlp);
        }
        page.addView(dots, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));

        Button next = actionButton(step == 2 ? "Let's read" : "Next", LIME, Color.rgb(10, 24, 35));
        playful(next, () -> {
            if (step < 2) showWalkthrough(step + 1);
            else {
                prefs.edit().putBoolean("intro_complete", true).apply();
                showHome();
            }
        });
        page.addView(next, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(60)));

        mount(page);
    }

    private void showHome() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(NAVY);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);

        column.addView(buildTopBar(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(68)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(24));

        content.addView(buildHero(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(286)));

        TextView categoriesLabel = txt("Choose a world", 22, TEXT, true);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        labelLp.topMargin = dp(12);
        content.addView(categoriesLabel, labelLp);
        content.addView(buildCategories(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(128)));

        content.addView(sectionTitle("Continue reading"), sectionTitleLp());
        content.addView(buildContinueCard(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(126)));

        content.addView(sectionTitle("Featured stories"), sectionTitleLp());
        content.addView(buildFeatured(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(226)));

        content.addView(sectionTitle("Explore"), sectionTitleLp());
        content.addView(buildExplore(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(112)));

        scroll.addView(content);
        column.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        column.addView(buildBottomNav(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(86)));

        page.addView(column, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        mount(page);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(17), dp(8), dp(12), dp(8));

        TextView brand = txt("📖  Project Phantom", 20, TEXT, true);
        bar.addView(brand, new LinearLayout.LayoutParams(0, dp(52), 1f));

        TextView profile = txt("🙂  My Profile", 14, TEXT, true);
        profile.setGravity(Gravity.CENTER);
        profile.setBackground(bg(PANEL_2, 22));
        playful(profile, () -> Toast.makeText(this, "Child profile", Toast.LENGTH_SHORT).show());
        bar.addView(profile, new LinearLayout.LayoutParams(dp(125), dp(46)));

        TextView settings = txt("⚙️", 25, TEXT, false);
        settings.setGravity(Gravity.CENTER);
        settings.setBackground(bg(PANEL_2, 23));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(48), dp(46));
        slp.leftMargin = dp(7);
        bar.addView(settings, slp);
        playful(settings, this::showParentControls);

        return bar;
    }

    private View buildHero() {
        FrameLayout hero = new FrameLayout(this);
        hero.setBackground(bgStroke(PANEL, 28, Color.rgb(31, 107, 172), 1));

        HeroArt art = new HeroArt(this);
        hero.addView(art, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER_VERTICAL);
        overlay.setPadding(dp(20), dp(18), dp(20), dp(18));

        TextView title = txt("Stories come to life\nwhen you read", 31, TEXT, true);
        title.setLineSpacing(dp(2), 1f);
        overlay.addView(title, new LinearLayout.LayoutParams(
                (int)(getResources().getDisplayMetrics().widthPixels * 0.65f), dp(105)));

        Button start = actionButton("Start Reading  ›", LIME, Color.rgb(9, 25, 34));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(210), dp(58));
        blp.topMargin = dp(12);
        overlay.addView(start, blp);
        playful(start, this::openReader);

        hero.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        return hero;
    }

    private View buildCategories() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        row.addView(category("🦁", "Animals", Color.rgb(17, 115, 191)));
        row.addView(category("🕌", "Islamic", Color.rgb(14, 132, 103)));
        row.addView(category("🧭", "Adventure", Color.rgb(183, 104, 25)));
        row.addView(category("🌙", "Bedtime", Color.rgb(92, 54, 174)));

        hsv.addView(row);
        return hsv;
    }

    private View category(String icon, String label, int color) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(10), dp(8), dp(10), dp(8));
        card.setBackground(bgStroke(color, 24, Color.argb(110, 255, 255, 255), 1));

        TextView ico = txt(icon, 37, TEXT, false);
        ico.setGravity(Gravity.CENTER);
        card.addView(ico, new LinearLayout.LayoutParams(dp(95), dp(65)));

        TextView lab = txt(label, 15, TEXT, true);
        lab.setGravity(Gravity.CENTER);
        card.addView(lab, new LinearLayout.LayoutParams(dp(95), dp(34)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(112), dp(112));
        lp.rightMargin = dp(10);
        card.setLayoutParams(lp);

        playful(card, () -> Toast.makeText(this, label + " stories", Toast.LENGTH_SHORT).show());
        return card;
    }

    private TextView sectionTitle(String s) {
        return txt(s, 22, TEXT, true);
    }

    private LinearLayout.LayoutParams sectionTitleLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(8);
        return lp;
    }

    private View buildContinueCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(bgStroke(PANEL_2, 22, Color.rgb(44, 95, 132), 1));

        MiniDragonView mini = new MiniDragonView(this);
        mini.setBackground(bg(Color.rgb(22, 65, 82), 18));
        card.addView(mini, new LinearLayout.LayoutParams(dp(102), dp(102)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(13), 0, dp(10), 0);

        TextView title = txt("The Little Dragon", 18, TEXT, true);
        info.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(34)));

        ProgressBar p = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        p.setMax(8);
        p.setProgress(3);
        info.addView(p, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(12)));

        TextView progress = txt("3 of 8 passages", 13, MUTED, false);
        info.addView(progress, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(32)));

        card.addView(info, new LinearLayout.LayoutParams(0, dp(96), 1f));

        TextView play = txt("▶", 25, Color.rgb(9, 25, 34), true);
        play.setGravity(Gravity.CENTER);
        play.setBackground(bg(LIME, 28));
        card.addView(play, new LinearLayout.LayoutParams(dp(58), dp(58)));

        playful(card, this::openReader);
        playful(play, this::openReader);
        return card;
    }

    private View buildFeatured() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        row.addView(storyCard("🐉", "The Little Dragon", "Ages 3+", Color.rgb(19, 82, 109), true));
        row.addView(storyCard("🌙", "Night of the Stars", "Ages 4+", Color.rgb(61, 43, 125), false));
        row.addView(storyCard("🦕", "Dino's New Friend", "Ages 3+", Color.rgb(61, 111, 67), false));

        hsv.addView(row);
        return hsv;
    }

    private View storyCard(String emoji, String title, String age, int color, boolean opensReader) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(bgStroke(color, 22, Color.argb(80, 255, 255, 255), 1));

        TextView art = txt(emoji, 58, TEXT, false);
        art.setGravity(Gravity.CENTER);
        card.addView(art, new LinearLayout.LayoutParams(dp(148), dp(118)));

        TextView name = txt(title, 16, TEXT, true);
        name.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(name, new LinearLayout.LayoutParams(dp(148), dp(42)));

        TextView meta = txt("📖  " + age, 13, MUTED, false);
        card.addView(meta, new LinearLayout.LayoutParams(dp(148), dp(30)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(172), dp(210));
        lp.rightMargin = dp(12);
        card.setLayoutParams(lp);

        playful(card, opensReader ? this::openReader :
                () -> Toast.makeText(this, title, Toast.LENGTH_SHORT).show());
        return card;
    }

    private View buildExplore() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        View newStories = exploreCard("✨  New Stories", PURPLE);
        View nature = exploreCard("🌿  Nature", Color.rgb(12, 124, 119));

        row.addView(newStories, new LinearLayout.LayoutParams(0, dp(96), 1f));
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(96), 1f);
        right.leftMargin = dp(10);
        row.addView(nature, right);
        return row;
    }

    private View exploreCard(String label, int color) {
        TextView card = txt(label + "  ›", 17, TEXT, true);
        card.setGravity(Gravity.CENTER);
        card.setBackground(bgStroke(color, 22, Color.argb(100, 255, 255, 255), 1));
        playful(card, () -> Toast.makeText(this, label.replace("✨  ", "").replace("🌿  ", ""), Toast.LENGTH_SHORT).show());
        return card;
    }

    private View buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(8), dp(8), dp(10));
        nav.setBackground(bgStroke(Color.rgb(7, 28, 49), 26, Color.rgb(30, 68, 101), 1));

        nav.addView(navItem("🏠", "Home", true), new LinearLayout.LayoutParams(0, dp(68), 1f));
        nav.addView(navItem("📚", "Stories", false), new LinearLayout.LayoutParams(0, dp(68), 1f));
        nav.addView(navItem("⭐", "Explore", false), new LinearLayout.LayoutParams(0, dp(68), 1f));
        nav.addView(navItem("❤️", "My Library", false), new LinearLayout.LayoutParams(0, dp(68), 1f));
        return nav;
    }

    private View navItem(String icon, String label, boolean active) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        if (active) item.setBackground(bg(Color.argb(50, 201, 255, 61), 18));

        TextView ico = txt(icon, 25, TEXT, false);
        ico.setGravity(Gravity.CENTER);
        item.addView(ico, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(36)));

        TextView lab = txt(label, 12, active ? LIME : MUTED, active);
        lab.setGravity(Gravity.CENTER);
        item.addView(lab, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(25)));

        playful(item, () -> {
            if (!active) Toast.makeText(this, label, Toast.LENGTH_SHORT).show();
        });
        return item;
    }

    private void openReader() {
        startActivity(new Intent(this, MainActivity.class));
    }

    private void showParentControls() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), dp(8));

        Switch sounds = new Switch(this);
        sounds.setText("Playful tap sounds");
        sounds.setTextSize(16);
        sounds.setChecked(prefs.getBoolean("ui_sounds", true));
        sounds.setPadding(0, dp(8), 0, dp(8));
        sounds.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean("ui_sounds", checked).apply());

        Switch haptics = new Switch(this);
        haptics.setText("Tap vibration");
        haptics.setTextSize(16);
        haptics.setChecked(prefs.getBoolean("ui_haptics", true));
        haptics.setPadding(0, dp(8), 0, dp(8));
        haptics.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean("ui_haptics", checked).apply());

        TextView privacy = txt("Microphone access is used for the read-aloud experience. Story interaction should remain child-safe and privacy-first.", 13, Color.DKGRAY, false);
        privacy.setPadding(0, dp(12), 0, 0);
        privacy.setLineSpacing(dp(3), 1f);

        box.addView(sounds);
        box.addView(haptics);
        box.addView(privacy);

        new AlertDialog.Builder(this)
                .setTitle("Parent controls")
                .setView(box)
                .setPositiveButton("Done", null)
                .show();
    }

    static class HeroArt extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

        HeroArt(Activity a) { super(a); }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight();

            p.setShader(new LinearGradient(0,0,w,h,
                    Color.rgb(5,28,54), Color.rgb(24,91,121), Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(0,0,w,h), 40,40,p);
            p.setShader(null);

            p.setColor(Color.rgb(255, 211, 91));
            c.drawCircle(w*.84f,h*.18f,28,p);

            p.setColor(Color.rgb(22,88,69));
            Path hill = new Path();
            hill.moveTo(w*.48f,h);
            hill.quadTo(w*.70f,h*.48f,w,h*.58f);
            hill.lineTo(w,h);
            hill.close();
            c.drawPath(hill,p);

            float x=w*.76f, y=h*.61f, s=Math.min(w,h)*.0053f;
            p.setColor(Color.rgb(80,188,114));
            c.drawOval(new RectF(x-26*s,y-8*s,x+30*s,y+25*s),p);
            c.drawCircle(x+23*s,y-28*s,20*s,p);

            p.setColor(Color.rgb(255,145,59));
            Path wing1=new Path();
            wing1.moveTo(x-4*s,y-9*s); wing1.lineTo(x-34*s,y-42*s); wing1.lineTo(x-29*s,y+2*s); wing1.close();
            c.drawPath(wing1,p);
            Path wing2=new Path();
            wing2.moveTo(x+5*s,y-10*s); wing2.lineTo(x+39*s,y-39*s); wing2.lineTo(x+30*s,y+3*s); wing2.close();
            c.drawPath(wing2,p);

            p.setColor(Color.WHITE);
            c.drawCircle(x+30*s,y-31*s,5*s,p);
            p.setColor(Color.rgb(17,24,39));
            c.drawCircle(x+32*s,y-31*s,2.2f*s,p);

            Random r=new Random(8);
            p.setColor(Color.argb(190,255,238,129));
            for(int i=0;i<14;i++) {
                float sx=w*(.48f+r.nextFloat()*.48f);
                float sy=h*(.08f+r.nextFloat()*.5f);
                c.drawCircle(sx,sy,1.5f+r.nextFloat()*2.5f,p);
            }
        }
    }

    static class MiniDragonView extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        MiniDragonView(Activity a){super(a);}
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight();
            p.setColor(Color.rgb(13,56,70)); c.drawRect(0,0,w,h,p);
            p.setColor(Color.rgb(74,163,100)); c.drawCircle(w*.45f,h*.55f,w*.18f,p);
            c.drawOval(new RectF(w*.38f,h*.52f,w*.77f,h*.78f),p);
            p.setColor(Color.rgb(255,145,59));
            Path wing=new Path(); wing.moveTo(w*.47f,h*.53f); wing.lineTo(w*.27f,h*.24f); wing.lineTo(w*.62f,h*.45f); wing.close(); c.drawPath(wing,p);
            p.setColor(Color.WHITE); c.drawCircle(w*.50f,h*.52f,w*.025f,p);
            p.setColor(Color.BLACK); c.drawCircle(w*.51f,h*.52f,w*.012f,p);
        }
    }

    static class BurstLayer extends View {
        static class Particle {
            float x,y,angle,distance,radius;
            int color;
        }

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        List<Particle> particles = new ArrayList<>();
        float progress = 1f;
        ValueAnimator animator;
        Random random = new Random();

        int[] colors = {
                Color.rgb(201,255,61),
                Color.rgb(76,201,255),
                Color.rgb(147,100,255),
                Color.rgb(255,166,61),
                Color.rgb(47,210,181)
        };

        BurstLayer(Activity a){super(a);}

        void burst(float x,float y){
            particles.clear();
            for(int i=0;i<9;i++){
                Particle part=new Particle();
                part.x=x; part.y=y;
                part.angle=(float)(Math.PI*2*i/9.0 + random.nextFloat()*.25);
                part.distance=36+random.nextFloat()*55;
                part.radius=5+random.nextFloat()*7;
                part.color=colors[i%colors.length];
                particles.add(part);
            }
            progress=0f;
            if(animator!=null) animator.cancel();
            animator=ValueAnimator.ofFloat(0f,1f);
            animator.setDuration(420);
            animator.addUpdateListener(a->{progress=(float)a.getAnimatedValue(); invalidate();});
            animator.start();
        }

        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            if(progress>=1f) return;
            float fade=1f-progress;
            for(Particle part:particles){
                float ease=(float)(1-Math.pow(1-progress,2));
                float px=part.x+(float)Math.cos(part.angle)*part.distance*ease;
                float py=part.y+(float)Math.sin(part.angle)*part.distance*ease;
                p.setColor(part.color);
                p.setAlpha((int)(230*fade));
                c.drawCircle(px,py,part.radius*(1-progress*.35f),p);
            }
        }
    }
}
