package com.phantom.demo;

import android.Manifest;
import android.animation.ValueAnimator;
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
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQ_AUDIO = 42;

    private static final int WAKE = 1;
    private static final int WINGS = 2;
    private static final int OUTSIDE = 3;
    private static final int BUTTERFLY = 4;
    private static final int FOLLOW = 5;
    private static final int STREAM = 6;
    private static final int DRINK = 7;
    private static final int SPLASH = 8;
    private static final int LOOK_HOME = 9;
    private static final int FLAP = 10;
    private static final int FLY_HOME = 11;

    private final String[] passages = {
            "The little dragon woke up inside his cave.",
            "He stretched his wings and stepped outside.",
            "A bright butterfly fluttered through the trees.",
            "The dragon followed it into the forest.",
            "He found a sparkling blue stream.",
            "He took a little drink and splashed the water.",
            "Then the dragon looked up and saw his home in the distance.",
            "He flapped his wings and flew happily home."
    };

    private final Handler handler = new Handler(Looper.getMainLooper());

    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private PowerManager.WakeLock screenWakeLock;
    private boolean wantsListening = false;
    private boolean restartScheduled = false;
    private boolean transitioning = false;
    private int sessionCount = 0;

    private StorySceneView sceneView;
    private TextView passageNumber;
    private TextView storyText;
    private TextView heardText;
    private TextView statusText;
    private TextView debugText;
    private TextView hintText;
    private ProgressBar progressBar;
    private Button micButton;
    private Button nextButton;

    private int passage = 0;
    private String committed = "";
    private String latestPartial = "";
    private boolean passageComplete = false;
    private final Set<Integer> firedEvents = new HashSet<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(5, 12, 24));
        getWindow().setNavigationBarColor(Color.rgb(5, 12, 24));
        // Reading is an eyes-on-screen activity. Keep the display awake while
        // this Activity is visible; Android releases the flag automatically
        // when the app is backgrounded or closed.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            screenWakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                    "PhantomReading:ForegroundScreen");
            screenWakeLock.setReferenceCounted(false);
        }

        buildUi();
        setupSpeech();
        showPassage(0, "Story ready");
    }

    private int dp(float n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView makeText(String value, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setPadding(dp(3), dp(2), dp(3), dp(2));
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button makeButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setMinHeight(dp(50));
        return b;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(10), dp(14), dp(12));
        root.setBackgroundColor(Color.rgb(5, 12, 24));
        root.setKeepScreenOn(true);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout brandBox = new LinearLayout(this);
        brandBox.setOrientation(LinearLayout.VERTICAL);

        TextView title = makeText("Phantom Reading Lab", 19, Color.WHITE, true);
        TextView version = makeText("Child reading prototype · 0.2.2", 11, Color.rgb(145, 164, 191), false);
        brandBox.addView(title);
        brandBox.addView(version);

        statusText = makeText("Mic off", 12, Color.rgb(226, 232, 240), true);
        statusText.setGravity(Gravity.CENTER);
        statusText.setBackgroundColor(Color.rgb(20, 34, 56));

        top.addView(brandBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(statusText, new LinearLayout.LayoutParams(dp(118), dp(38)));
        root.addView(top);

        sceneView = new StorySceneView(this);
        LinearLayout.LayoutParams stageLp =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        stageLp.topMargin = dp(8);
        stageLp.bottomMargin = dp(8);
        root.addView(sceneView, stageLp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(11), dp(14), dp(11));
        card.setBackgroundColor(Color.rgb(17, 28, 47));

        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);

        passageNumber = makeText("Passage 1 of 8", 12, Color.rgb(125, 211, 252), true);
        hintText = makeText("READ ALOUD", 11, Color.rgb(134, 239, 172), true);
        hintText.setGravity(Gravity.RIGHT);

        metaRow.addView(passageNumber,
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        metaRow.addView(hintText,
                new LinearLayout.LayoutParams(dp(120), LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(metaRow);

        storyText = makeText(passages[0], 25, Color.WHITE, true);
        storyText.setLineSpacing(dp(3), 1.0f);
        storyText.setPadding(dp(3), dp(8), dp(3), dp(7));
        card.addView(storyText);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        card.addView(progressBar,
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12)));

        heardText = makeText("Tap Start reading, then read the sentence naturally.", 12,
                Color.rgb(163, 181, 207), false);
        heardText.setPadding(dp(3), dp(7), dp(3), dp(2));
        card.addView(heardText);

        root.addView(card);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(0, dp(8), 0, 0);

        Button backButton = makeButton("← Back");
        micButton = makeButton("🎙 Start reading");
        nextButton = makeButton("Next →");

        controls.addView(backButton, new LinearLayout.LayoutParams(0, dp(54), 1f));
        controls.addView(micButton, new LinearLayout.LayoutParams(0, dp(54), 1.45f));
        controls.addView(nextButton, new LinearLayout.LayoutParams(0, dp(54), 1f));
        root.addView(controls);

        Button resetButton = makeButton("Restart story");
        root.addView(resetButton,
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(47)));

        debugText = makeText("", 10, Color.rgb(148, 163, 184), false);
        debugText.setVisibility(View.GONE);
        ScrollView debugScroll = new ScrollView(this);
        debugScroll.addView(debugText);
        root.addView(debugScroll,
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(76)));

        title.setOnLongClickListener(v -> {
            debugText.setVisibility(debugText.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            updateDebug("Debug toggled");
            return true;
        });

        backButton.setOnClickListener(v -> showPassage(passage - 1, "Manual back"));
        nextButton.setOnClickListener(v -> {
            if (passage < passages.length - 1) showPassage(passage + 1, "Manual next");
            else finishStory();
        });
        resetButton.setOnClickListener(v -> showPassage(0, "Story restarted"));

        micButton.setOnClickListener(v -> {
            if (wantsListening) stopListening();
            else requestAndStart();
        });

        setContentView(root);
    }

    private void setupSpeech() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.setText("Voice unavailable");
            heardText.setText("No Android speech recogniser found. Use Back / Next to test the story.");
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
            @Override
            public void onReadyForSpeech(Bundle params) {
                if (transitioning) return;
                statusText.setText("● Listening");
                micButton.setText("■ Stop");
            }

            @Override
            public void onBeginningOfSpeech() {
                if (!transitioning) statusText.setText("● Reading");
            }

            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                if (!transitioning) statusText.setText("Checking…");
            }

            @Override
            public void onError(int error) {
                if (!wantsListening || transitioning) return;

                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    stopListening();
                    heardText.setText("Microphone permission is required.");
                    return;
                }

                long delay = error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 500 : 190;
                scheduleRestart(delay);
            }

            @Override
            public void onResults(Bundle results) {
                handleResults(results, true);
                if (wantsListening && !transitioning) scheduleRestart(130);
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                handleResults(partialResults, false);
            }

            @Override public void onEvent(int eventType, Bundle params) {}
        });

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-GB");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
    }

    private void requestAndStart() {
        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }

        wantsListening = true;
        startSession();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_AUDIO &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            wantsListening = true;
            startSession();
        } else if (requestCode == REQ_AUDIO) {
            heardText.setText("Microphone permission was not granted.");
        }
    }

    private void startSession() {
        if (!wantsListening || recognizer == null || transitioning) return;

        restartScheduled = false;

        try {
            recognizer.cancel();
        } catch (Exception ignored) {}

        handler.postDelayed(() -> {
            if (!wantsListening || transitioning) return;

            try {
                sessionCount++;
                recognizer.startListening(recognizerIntent);
                updateDebug("Speech session " + sessionCount);
            } catch (Exception e) {
                scheduleRestart(400);
            }
        }, 90);
    }

    private void scheduleRestart(long delayMs) {
        if (!wantsListening || restartScheduled || transitioning) return;

        restartScheduled = true;
        handler.postDelayed(this::startSession, delayMs);
    }

    private void stopListening() {
        wantsListening = false;
        restartScheduled = false;
        transitioning = false;
        handler.removeCallbacksAndMessages(null);

        if (recognizer != null) {
            try {
                recognizer.cancel();
            } catch (Exception ignored) {}
        }

        statusText.setText("Mic off");
        micButton.setText("🎙 Start reading");
    }

    private void handleResults(Bundle bundle, boolean isFinal) {
        if (bundle == null || passageComplete || transitioning) return;

        ArrayList<String> alternatives =
                bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

        if (alternatives == null || alternatives.isEmpty()) return;

        String best = alternatives.get(0).trim();

        if (isFinal) {
            if (!best.isEmpty()) {
                committed = trimWindow((committed + " " + best).trim());
                latestPartial = "";
            }
        } else {
            latestPartial = best;
        }

        String combined = (committed + " " + latestPartial).trim();

        if (!best.isEmpty()) {
            heardText.setText((isFinal ? "Heard: " : "Hearing: ") + "“" + best + "”");
        }

        evaluatePassage(combined);
    }

    private String trimWindow(String value) {
        String[] words = normalize(value).split(" ");
        if (words.length <= 36) return value;

        StringBuilder b = new StringBuilder();
        for (int i = words.length - 36; i < words.length; i++) {
            if (b.length() > 0) b.append(' ');
            b.append(words[i]);
        }
        return b.toString();
    }

    private void evaluatePassage(String heard) {
        List<String> expected = contentTokens(passages[passage]);
        List<String> actual = contentTokens(heard);

        boolean[] matched = new boolean[expected.size()];
        int matchCount = 0;

        for (int i = 0; i < expected.size(); i++) {
            for (String word : actual) {
                if (sameish(expected.get(i), word)) {
                    matched[i] = true;
                    matchCount++;
                    break;
                }
            }
        }

        float coverage = expected.isEmpty() ? 0f : (float) matchCount / expected.size();
        int lcs = approximateLcs(expected, actual);
        float order = expected.isEmpty() ? 0f : (float) lcs / expected.size();

        int percent = Math.min(100, Math.round(Math.max(coverage, order * 0.92f) * 100f));

        progressBar.setProgress(percent);
        renderHighlightedPassage(matched);
        applyCueMatches(heard);

        updateDebug("Coverage " + Math.round(coverage * 100) +
                "% · order " + Math.round(order * 100) +
                "% · " + percent + "%");

        int minimumHeard = expected.size() <= 3 ? 2 : Math.min(3, expected.size());
        boolean enoughWords = actual.size() >= minimumHeard;

        float shortSentenceThreshold = expected.size() <= 3 ? 0.66f : 0.72f;

        boolean complete =
                enoughWords &&
                        ((coverage >= shortSentenceThreshold && order >= 0.50f) ||
                                coverage >= 0.86f ||
                                order >= 0.82f);

        if (complete) {
            completePassage();
        }
    }

    private void renderHighlightedPassage(boolean[] matchedContent) {
        String sentence = passages[passage];
        SpannableString span = new SpannableString(sentence);

        List<String> content = contentTokens(sentence);
        int contentIndex = 0;

        String lower = sentence.toLowerCase(Locale.UK);
        int i = 0;

        while (i < lower.length()) {
            while (i < lower.length() && !Character.isLetterOrDigit(lower.charAt(i))) i++;
            if (i >= lower.length()) break;

            int start = i;
            while (i < lower.length() &&
                    (Character.isLetterOrDigit(lower.charAt(i)) || lower.charAt(i) == 39)) i++;
            int end = i;

            String rawWord = normalize(lower.substring(start, end));

            if (!rawWord.isEmpty() && !isStopWord(rawWord)) {
                if (contentIndex < matchedContent.length && matchedContent[contentIndex]) {
                    span.setSpan(new ForegroundColorSpan(Color.rgb(134, 239, 172)),
                            start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    span.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
                            start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                contentIndex++;
            }
        }

        storyText.setText(span);
    }

    private List<String> contentTokens(String source) {
        String norm = normalize(source);
        List<String> out = new ArrayList<>();

        if (norm.isEmpty()) return out;

        for (String word : norm.split(" ")) {
            if (word.length() < 2) continue;
            if (isStopWord(word)) continue;
            out.add(word);
        }

        return out;
    }

    private boolean isStopWord(String word) {
        return STOP_WORDS.contains(word);
    }

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "the", "a", "an", "and", "his", "her", "he", "she", "it", "up",
            "in", "into", "inside", "through", "to", "of", "then", "little"
    ));

    private String normalize(String source) {
        if (source == null) return "";

        return source.toLowerCase(Locale.UK)
                .replace("’", "'")
                .replaceAll("[^a-z0-9' ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean sameish(String a, String b) {
        if (a.equals(b)) return true;

        String aa = stem(a);
        String bb = stem(b);

        if (aa.equals(bb)) return true;

        if (aa.length() >= 5 && bb.length() >= 5) {
            int min = Math.min(aa.length(), bb.length());

            if (aa.substring(0, Math.min(5, min))
                    .equals(bb.substring(0, Math.min(5, min)))) {
                return true;
            }
        }

        return false;
    }

    private String stem(String w) {
        if (w.length() > 5 && w.endsWith("ing")) return w.substring(0, w.length() - 3);
        if (w.length() > 4 && w.endsWith("ed")) return w.substring(0, w.length() - 2);
        if (w.length() > 4 && w.endsWith("es")) return w.substring(0, w.length() - 2);
        if (w.length() > 3 && w.endsWith("s")) return w.substring(0, w.length() - 1);
        return w;
    }

    private int approximateLcs(List<String> expected, List<String> actual) {
        int[][] dp = new int[expected.size() + 1][actual.size() + 1];

        for (int i = 1; i <= expected.size(); i++) {
            for (int j = 1; j <= actual.size(); j++) {
                if (sameish(expected.get(i - 1), actual.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        return dp[expected.size()][actual.size()];
    }

    private boolean heardAll(String source, String... required) {
        List<String> heard = contentTokens(source);

        for (String req : required) {
            boolean found = false;

            for (String word : heard) {
                if (sameish(req, word)) {
                    found = true;
                    break;
                }
            }

            if (!found) return false;
        }

        return true;
    }

    private void fireOnce(int event) {
        if (firedEvents.contains(event)) return;

        firedEvents.add(event);
        sceneView.fireEvent(event);
        vibrate(20);
    }

    private void applyCueMatches(String heard) {
        switch (passage) {
            case 0:
                if (heardAll(heard, "dragon", "woke")) fireOnce(WAKE);
                break;

            case 1:
                if (heardAll(heard, "stretched", "wings")) fireOnce(WINGS);
                if (heardAll(heard, "stepped", "outside")) fireOnce(OUTSIDE);
                break;

            case 2:
                if (heardAll(heard, "bright", "butterfly") ||
                        heardAll(heard, "butterfly", "fluttered")) {
                    fireOnce(BUTTERFLY);
                }
                break;

            case 3:
                if (heardAll(heard, "dragon", "followed") ||
                        heardAll(heard, "followed", "forest")) {
                    fireOnce(FOLLOW);
                }
                break;

            case 4:
                if (heardAll(heard, "sparkling", "stream") ||
                        heardAll(heard, "blue", "stream")) {
                    fireOnce(STREAM);
                }
                break;

            case 5:
                if (heardAll(heard, "drink")) fireOnce(DRINK);
                if (heardAll(heard, "splashed", "water")) fireOnce(SPLASH);
                break;

            case 6:
                if (heardAll(heard, "home", "distance") ||
                        heardAll(heard, "saw", "home")) {
                    fireOnce(LOOK_HOME);
                }
                break;

            case 7:
                if (heardAll(heard, "flapped", "wings")) fireOnce(FLAP);
                if (heardAll(heard, "flew", "home") ||
                        heardAll(heard, "happily", "home")) {
                    fireOnce(FLY_HOME);
                }
                break;
        }
    }

    private void completePassage() {
        if (passageComplete) return;

        ensureFinalStoryState();
        passageComplete = true;
        progressBar.setProgress(100);
        hintText.setText("✓ COMPLETE");
        hintText.setTextColor(Color.rgb(134, 239, 172));
        heardText.setText("Great — the story moved because you read it.");
        statusText.setText("✓ Complete");
        vibrate(45);

        if (passage == passages.length - 1) {
            handler.postDelayed(this::finishStory, 1100);
            return;
        }

        transitioning = true;

        if (recognizer != null) {
            try {
                recognizer.cancel();
            } catch (Exception ignored) {}
        }

        handler.postDelayed(() -> showPassage(passage + 1, "Auto advance"), 1450);
    }

    private void ensureFinalStoryState() {
        switch (passage) {
            case 0:
                fireOnce(WAKE);
                break;
            case 1:
                fireOnce(WINGS);
                fireOnce(OUTSIDE);
                break;
            case 2:
                fireOnce(BUTTERFLY);
                break;
            case 3:
                fireOnce(FOLLOW);
                break;
            case 4:
                fireOnce(STREAM);
                break;
            case 5:
                fireOnce(DRINK);
                fireOnce(SPLASH);
                break;
            case 6:
                fireOnce(LOOK_HOME);
                break;
            case 7:
                fireOnce(FLAP);
                fireOnce(FLY_HOME);
                break;
        }
    }

    private void showPassage(int requested, String reason) {
        passage = Math.max(0, Math.min(passages.length - 1, requested));
        committed = "";
        latestPartial = "";
        passageComplete = false;
        transitioning = false;
        firedEvents.clear();

        passageNumber.setText("Passage " + (passage + 1) + " of " + passages.length);
        storyText.setText(passages[passage]);
        progressBar.setProgress(0);
        hintText.setText("READ ALOUD");
        hintText.setTextColor(Color.rgb(134, 239, 172));
        heardText.setText("Read the sentence naturally. Key moments will react while you speak.");
        nextButton.setText(passage == passages.length - 1 ? "Finish" : "Next →");

        sceneView.setPassage(passage);
        updateDebug(reason);

        if (wantsListening) {
            if (recognizer != null) {
                try {
                    recognizer.cancel();
                } catch (Exception ignored) {}
            }
            scheduleRestart(220);
        }
    }

    private void finishStory() {
        sceneView.storyComplete = true;
        sceneView.invalidate();

        passageComplete = true;
        progressBar.setProgress(100);
        hintText.setText("STORY COMPLETE");
        hintText.setTextColor(Color.rgb(253, 224, 71));
        heardText.setText("The reading created the story. That is the mechanic we are testing.");
        statusText.setText("★ Finished");
        nextButton.setText("Finished");
        vibrate(80);

        if (wantsListening && recognizer != null) {
            transitioning = true;
            try {
                recognizer.cancel();
            } catch (Exception ignored) {}
        }
    }

    private void vibrate(long duration) {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(
                            VibrationEffect.createOneShot(duration,
                                    VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(duration);
                }
            }
        } catch (Exception ignored) {}
    }

    private void updateDebug(String last) {
        if (debugText == null) return;

        debugText.setText(
                "Passage: " + (passage + 1) + "/" + passages.length +
                        "\nMic wanted: " + wantsListening +
                        " · sessions: " + sessionCount +
                        "\nCommitted: " + (committed.isEmpty() ? "—" : committed) +
                        "\nLast: " + last
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (screenWakeLock != null && !screenWakeLock.isHeld()) {
            try {
                screenWakeLock.acquire();
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onPause() {
        if (screenWakeLock != null && screenWakeLock.isHeld()) {
            try {
                screenWakeLock.release();
            } catch (Exception ignored) {}
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        wantsListening = false;
        handler.removeCallbacksAndMessages(null);

        if (recognizer != null) {
            try {
                recognizer.destroy();
            } catch (Exception ignored) {}
        }

        super.onDestroy();
    }

    static class StorySceneView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

        int passage = 0;
        boolean awake = false;
        boolean wingsOpen = false;
        boolean butterflyVisible = false;
        boolean streamVisible = false;
        boolean drinking = false;
        boolean splash = false;
        boolean homeVisible = false;
        boolean storyComplete = false;

        float dragonX = 0.30f;
        float dragonY = 0.67f;
        float butterflyX = 0.70f;
        float butterflyY = 0.39f;
        float splashPhase = 0f;

        ValueAnimator moveAnimator;
        ValueAnimator splashAnimator;

        StorySceneView(Activity context) {
            super(context);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
        }

        void setPassage(int p) {
            passage = p;
            storyComplete = false;
            splash = false;
            splashPhase = 0f;
            drinking = false;

            awake = p >= 1;
            wingsOpen = false;
            butterflyVisible = p >= 3;
            streamVisible = p >= 5;
            homeVisible = p >= 7;

            switch (p) {
                case 0:
                    dragonX = 0.30f;
                    dragonY = 0.68f;
                    break;
                case 1:
                    dragonX = 0.31f;
                    dragonY = 0.68f;
                    break;
                case 2:
                    dragonX = 0.23f;
                    dragonY = 0.68f;
                    break;
                case 3:
                    dragonX = 0.24f;
                    dragonY = 0.68f;
                    break;
                case 4:
                    dragonX = 0.52f;
                    dragonY = 0.68f;
                    break;
                case 5:
                    dragonX = 0.51f;
                    dragonY = 0.67f;
                    break;
                case 6:
                    dragonX = 0.52f;
                    dragonY = 0.67f;
                    break;
                default:
                    dragonX = 0.50f;
                    dragonY = 0.62f;
            }

            invalidate();
        }

        void fireEvent(int event) {
            switch (event) {
                case WAKE:
                    awake = true;
                    invalidate();
                    break;

                case WINGS:
                    wingsOpen = true;
                    invalidate();
                    break;

                case OUTSIDE:
                    animateDragonTo(0.61f, 0.68f, 650);
                    break;

                case BUTTERFLY:
                    butterflyVisible = true;
                    animateButterfly();
                    break;

                case FOLLOW:
                    animateDragonTo(0.58f, 0.68f, 780);
                    break;

                case STREAM:
                    streamVisible = true;
                    invalidate();
                    break;

                case DRINK:
                    drinking = true;
                    dragonY = 0.71f;
                    invalidate();
                    break;

                case SPLASH:
                    splash = true;
                    runSplash();
                    break;

                case LOOK_HOME:
                    homeVisible = true;
                    drinking = false;
                    dragonY = 0.66f;
                    invalidate();
                    break;

                case FLAP:
                    wingsOpen = true;
                    invalidate();
                    break;

                case FLY_HOME:
                    wingsOpen = true;
                    animateDragonTo(0.78f, 0.35f, 1050);
                    break;
            }
        }

        void animateDragonTo(float targetX, float targetY, long duration) {
            if (moveAnimator != null) moveAnimator.cancel();

            final float startX = dragonX;
            final float startY = dragonY;

            moveAnimator = ValueAnimator.ofFloat(0f, 1f);
            moveAnimator.setDuration(duration);

            moveAnimator.addUpdateListener(animation -> {
                float t = (float) animation.getAnimatedValue();
                float eased = t * t * (3f - 2f * t);
                dragonX = startX + (targetX - startX) * eased;
                dragonY = startY + (targetY - startY) * eased;
                invalidate();
            });

            moveAnimator.start();
        }

        void animateButterfly() {
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            final float sx = butterflyX;
            final float sy = butterflyY;

            a.setDuration(1000);
            a.addUpdateListener(animation -> {
                float t = (float) animation.getAnimatedValue();
                butterflyX = sx - 0.14f * t;
                butterflyY = sy + (float) Math.sin(t * Math.PI * 3f) * 0.045f;
                invalidate();
            });
            a.start();
        }

        void runSplash() {
            if (splashAnimator != null) splashAnimator.cancel();

            splashAnimator = ValueAnimator.ofFloat(0f, 1f);
            splashAnimator.setDuration(850);
            splashAnimator.addUpdateListener(animation -> {
                splashPhase = (float) animation.getAnimatedValue();
                invalidate();
            });
            splashAnimator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float w = getWidth();
            float h = getHeight();

            drawSky(canvas, w, h);

            if (passage <= 1) {
                drawCaveWorld(canvas, w, h);
            } else {
                drawForestWorld(canvas, w, h);
            }

            if (streamVisible || passage >= 5) drawStream(canvas, w, h);
            if (homeVisible) drawDistantHome(canvas, w, h);
            if (butterflyVisible) drawButterfly(canvas, w, h);
            drawDragon(canvas, w, h);

            if (splash) drawSplash(canvas, w, h);

            if (storyComplete) drawCelebration(canvas, w, h);
        }

        private void drawSky(Canvas c, float w, float h) {
            fill.setColor(Color.rgb(124, 194, 238));
            c.drawRect(0, 0, w, h * 0.65f, fill);

            fill.setColor(Color.rgb(253, 224, 71));
            c.drawCircle(w * 0.86f, h * 0.14f, Math.min(w, h) * 0.07f, fill);

            fill.setColor(Color.rgb(126, 190, 93));
            c.drawRect(0, h * 0.62f, w, h, fill);
        }

        private void drawCaveWorld(Canvas c, float w, float h) {
            fill.setColor(Color.rgb(88, 76, 72));
            Path mountain = new Path();
            mountain.moveTo(0, h * 0.24f);
            mountain.lineTo(w * 0.32f, h * 0.11f);
            mountain.lineTo(w * 0.57f, h * 0.62f);
            mountain.lineTo(0, h * 0.72f);
            mountain.close();
            c.drawPath(mountain, fill);

            fill.setColor(Color.rgb(32, 38, 43));
            c.drawOval(new RectF(w * 0.10f, h * 0.39f, w * 0.43f, h * 0.78f), fill);

            fill.setColor(Color.rgb(116, 88, 60));
            c.drawRect(w * 0.44f, h * 0.66f, w, h * 0.73f, fill);
        }

        private void drawForestWorld(Canvas c, float w, float h) {
            for (int i = 0; i < 6; i++) {
                float x = w * (0.05f + i * 0.19f);
                float base = h * (0.66f + (i % 2) * 0.03f);

                fill.setColor(Color.rgb(111, 78, 55));
                c.drawRect(x, base - h * 0.25f, x + w * 0.035f, base, fill);

                fill.setColor(i % 2 == 0 ? Color.rgb(45, 132, 82) : Color.rgb(57, 147, 88));
                c.drawCircle(x + w * 0.018f, base - h * 0.27f, w * 0.085f, fill);
                c.drawCircle(x - w * 0.025f, base - h * 0.20f, w * 0.070f, fill);
                c.drawCircle(x + w * 0.065f, base - h * 0.20f, w * 0.070f, fill);
            }

            fill.setColor(Color.rgb(180, 143, 84));
            Path path = new Path();
            path.moveTo(0, h * 0.81f);
            path.cubicTo(w * 0.3f, h * 0.70f, w * 0.55f, h * 0.91f, w, h * 0.78f);
            path.lineTo(w, h);
            path.lineTo(0, h);
            path.close();
            c.drawPath(path, fill);
        }

        private void drawStream(Canvas c, float w, float h) {
            fill.setColor(Color.rgb(56, 163, 224));
            Path stream = new Path();
            stream.moveTo(0, h * 0.86f);
            stream.cubicTo(w * 0.25f, h * 0.76f, w * 0.46f, h * 0.84f, w, h * 0.73f);
            stream.lineTo(w, h);
            stream.lineTo(0, h);
            stream.close();
            c.drawPath(stream, fill);

            stroke.setColor(Color.rgb(185, 232, 255));
            stroke.setStrokeWidth(4);
            for (int i = 0; i < 4; i++) {
                float y = h * (0.82f + i * 0.035f);
                c.drawLine(w * 0.12f, y, w * (0.35f + i * 0.08f), y - h * 0.015f, stroke);
            }
        }

        private void drawDistantHome(Canvas c, float w, float h) {
            float x = w * 0.82f;
            float y = h * 0.31f;

            fill.setColor(Color.rgb(90, 76, 72));
            c.drawCircle(x, y, w * 0.095f, fill);

            fill.setColor(Color.rgb(34, 40, 45));
            c.drawOval(new RectF(x - w * 0.045f, y, x + w * 0.055f, y + h * 0.10f), fill);

            fill.setColor(Color.rgb(253, 224, 71));
            c.drawCircle(x + w * 0.01f, y + h * 0.045f, w * 0.012f, fill);
        }

        private void drawButterfly(Canvas c, float w, float h) {
            float x = w * butterflyX;
            float y = h * butterflyY;

            fill.setColor(Color.rgb(251, 113, 133));
            c.drawOval(new RectF(x - w * 0.045f, y - h * 0.035f,
                    x - w * 0.004f, y + h * 0.018f), fill);
            c.drawOval(new RectF(x + w * 0.004f, y - h * 0.035f,
                    x + w * 0.045f, y + h * 0.018f), fill);

            stroke.setColor(Color.rgb(71, 45, 56));
            stroke.setStrokeWidth(4);
            c.drawLine(x, y - h * 0.01f, x, y + h * 0.045f, stroke);
        }

        private void drawDragon(Canvas c, float w, float h) {
            float x = w * dragonX;
            float y = h * dragonY;
            float scale = Math.min(w, h) * 0.0042f;

            if (wingsOpen) {
                fill.setColor(Color.rgb(74, 170, 118));

                Path leftWing = new Path();
                leftWing.moveTo(x - 5 * scale, y - 10 * scale);
                leftWing.lineTo(x - 34 * scale, y - 35 * scale);
                leftWing.lineTo(x - 27 * scale, y + 2 * scale);
                leftWing.close();
                c.drawPath(leftWing, fill);

                Path rightWing = new Path();
                rightWing.moveTo(x + 5 * scale, y - 10 * scale);
                rightWing.lineTo(x + 35 * scale, y - 37 * scale);
                rightWing.lineTo(x + 28 * scale, y + 2 * scale);
                rightWing.close();
                c.drawPath(rightWing, fill);
            }

            fill.setColor(Color.rgb(60, 154, 100));
            c.drawOval(new RectF(x - 26 * scale, y - 10 * scale,
                    x + 28 * scale, y + 25 * scale), fill);

            float headY = drinking ? y + 11 * scale : y - 26 * scale;

            fill.setColor(Color.rgb(73, 174, 111));
            c.drawCircle(x + 23 * scale, headY, 19 * scale, fill);

            Path tail = new Path();
            tail.moveTo(x - 23 * scale, y + 4 * scale);
            tail.quadTo(x - 48 * scale, y + 5 * scale, x - 58 * scale, y - 9 * scale);
            stroke.setColor(Color.rgb(60, 154, 100));
            stroke.setStrokeWidth(9 * scale);
            c.drawPath(tail, stroke);

            stroke.setColor(Color.rgb(44, 112, 74));
            stroke.setStrokeWidth(7 * scale);
            c.drawLine(x - 14 * scale, y + 18 * scale,
                    x - 18 * scale, y + 35 * scale, stroke);
            c.drawLine(x + 11 * scale, y + 18 * scale,
                    x + 13 * scale, y + 35 * scale, stroke);

            fill.setColor(Color.rgb(234, 179, 8));
            Path horn1 = new Path();
            horn1.moveTo(x + 15 * scale, headY - 14 * scale);
            horn1.lineTo(x + 8 * scale, headY - 32 * scale);
            horn1.lineTo(x + 24 * scale, headY - 19 * scale);
            horn1.close();
            c.drawPath(horn1, fill);

            Path horn2 = new Path();
            horn2.moveTo(x + 30 * scale, headY - 12 * scale);
            horn2.lineTo(x + 32 * scale, headY - 30 * scale);
            horn2.lineTo(x + 39 * scale, headY - 14 * scale);
            horn2.close();
            c.drawPath(horn2, fill);

            if (awake) {
                fill.setColor(Color.WHITE);
                c.drawCircle(x + 29 * scale, headY - 3 * scale, 5 * scale, fill);
                fill.setColor(Color.rgb(17, 24, 39));
                c.drawCircle(x + 31 * scale, headY - 3 * scale, 2.2f * scale, fill);
            } else {
                stroke.setColor(Color.rgb(17, 24, 39));
                stroke.setStrokeWidth(2.4f * scale);
                c.drawLine(x + 25 * scale, headY - 2 * scale,
                        x + 34 * scale, headY - 2 * scale, stroke);
            }

            stroke.setColor(Color.rgb(34, 85, 59));
            stroke.setStrokeWidth(2.5f * scale);
            c.drawArc(new RectF(x + 22 * scale, headY + 1 * scale,
                    x + 42 * scale, headY + 13 * scale),
                    10, 150, false, stroke);
        }

        private void drawSplash(Canvas c, float w, float h) {
            float x = w * (dragonX + 0.10f);
            float y = h * 0.82f;
            float radius = (20f + 65f * splashPhase);

            stroke.setColor(Color.argb(
                    Math.max(0, 220 - (int) (190 * splashPhase)),
                    210, 244, 255));
            stroke.setStrokeWidth(5);
            c.drawCircle(x, y, radius, stroke);
            c.drawCircle(x + radius * 0.4f, y - radius * 0.35f, radius * 0.28f, stroke);
        }

        private void drawCelebration(Canvas c, float w, float h) {
            fill.setColor(Color.argb(155, 6, 15, 27));
            c.drawRoundRect(new RectF(w * 0.08f, h * 0.10f, w * 0.92f, h * 0.30f),
                    24, 24, fill);

            Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
            text.setColor(Color.WHITE);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            text.setTextSize(Math.min(w, h) * 0.075f);
            c.drawText("Story complete!", w * 0.50f, h * 0.20f, text);

            text.setColor(Color.rgb(253, 224, 71));
            text.setTextSize(Math.min(w, h) * 0.045f);
            c.drawText("★  ★  ★", w * 0.50f, h * 0.265f, text);
        }
    }
}
