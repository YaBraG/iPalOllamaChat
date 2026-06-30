package com.example.robotvoicedemo;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.robot.hw.RobotDevices;
import android.robot.hw.RobotSystem;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.robot.motion.RobotMotion;
import android.robot.speech.SpeechManager;
import android.robot.speech.SpeechManager.TtsListener;
import android.robot.speech.SpeechService;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "iPalOllamaChat";
    private static final String DEFAULT_SERVER_URL = "http://192.168.2.36:11434";
    private static final String OLLAMA_MODEL = "llama3.2:3b";

    private static final String PREFS_NAME = "iPalOllamaChatPrefs";
    private static final String PREF_SERVER_URL = "server_url";

    private static final int MOTOR_RESET_DELAY_MS = 2000;
    private static final long TOUCH_REACTION_COOLDOWN_MS = 2500;

    private static final String ACTION_NONE = "none";
    private static final String ACTION_NOD_HEAD = "nod_head";
    private static final String ACTION_SHAKE_HEAD = "shake_head";
    private static final String ACTION_SMILE = "smile";
    private static final String ACTION_SAD = "sad";
    private static final String ACTION_CRY = "cry";
    private static final String ACTION_SHY = "shy";
    private static final String ACTION_ANGRY = "angry";
    private static final String ACTION_BLINK = "blink";
    private static final String ACTION_FROWN = "frown";
    private static final String ACTION_DEFAULT_FACE = "default_face";
    private static final String ACTION_RESET_MOTORS = "reset_motors";
    private static final String ACTION_RIGHT_ARM_SMALL_WAVE = "right_arm_small_wave";
    private static final String ACTION_LEFT_ARM_SMALL_WAVE = "left_arm_small_wave";
    private static final String ACTION_BOTH_ARMS_SMALL_WAVE = "both_arms_small_wave";
    private static final String ACTION_CLEAR_FACE = "clear_face";
    private static final String ACTION_COVER_SMILE = "cover_smile";
    private static final String ACTION_DOUBT = "doubt";
    private static final String ACTION_EYE_BIND_ONE = "eye_bind_one";
    private static final String ACTION_EYE_CLOSE = "eye_close";
    private static final String ACTION_EYE_OPEN = "eye_open";
    private static final String ACTION_GRIMACE = "grimace";
    private static final String ACTION_HEARTED = "hearted";
    private static final String ACTION_INDIFFERENT = "indifferent";
    private static final String ACTION_LAUGH = "laugh";
    private static final String ACTION_LISTEN = "listen";
    private static final String ACTION_NAUGHTY_FACE = "naughty_face";
    private static final String ACTION_SHH = "shh";
    private static final String ACTION_SLEEP = "sleep";
    private static final String ACTION_SURPRISE = "surprise";
    private static final String ACTION_TALK = "talk";
    private static final String ACTION_THINKING = "thinking";
    private static final String ACTION_WAKE_UP = "wake_up";

    private static final String ALLOWED_ACTIONS_TEXT =
            ACTION_NONE + ", "
                    + ACTION_NOD_HEAD + ", "
                    + ACTION_SHAKE_HEAD + ", "
                    + ACTION_SMILE + ", "
                    + ACTION_SAD + ", "
                    + ACTION_CRY + ", "
                    + ACTION_SHY + ", "
                    + ACTION_ANGRY + ", "
                    + ACTION_BLINK + ", "
                    + ACTION_FROWN + ", "
                    + ACTION_DEFAULT_FACE + ", "
                    + ACTION_RESET_MOTORS + ", "
                    + ACTION_RIGHT_ARM_SMALL_WAVE + ", "
                    + ACTION_LEFT_ARM_SMALL_WAVE + ", "
                    + ACTION_BOTH_ARMS_SMALL_WAVE + ", "
                    + ACTION_CLEAR_FACE + ", "
                    + ACTION_COVER_SMILE + ", "
                    + ACTION_DOUBT + ", "
                    + ACTION_EYE_BIND_ONE + ", "
                    + ACTION_EYE_CLOSE + ", "
                    + ACTION_EYE_OPEN + ", "
                    + ACTION_GRIMACE + ", "
                    + ACTION_HEARTED + ", "
                    + ACTION_INDIFFERENT + ", "
                    + ACTION_LAUGH + ", "
                    + ACTION_LISTEN + ", "
                    + ACTION_NAUGHTY_FACE + ", "
                    + ACTION_SHH + ", "
                    + ACTION_SLEEP + ", "
                    + ACTION_SURPRISE + ", "
                    + ACTION_TALK + ", "
                    + ACTION_THINKING + ", "
                    + ACTION_WAKE_UP;

    private ImageView mBtnBack;

    private EditText mServerUrl;
    private EditText mPrompt;

    private TextView mConnectionStatus;
    private TextView mResponse;
    private TextView mTtsStatus;

    private Button mBtnTestConnection;
    private Button mBtnAskIpal;
    private Button mBtnClear;
    private Button mBtnSpeakAgain;

    private InputMethodManager mInputMethodManager;
    private SpeechManager mSpeechManager;
    private RobotMotion mRobotMotion = new RobotMotion();
    private RobotSystem mRobotSystem;

    private Handler mMainHandler;
    private Runnable mMotorResetRunnable;

    private String mLastResponse = "";
    private long mLastTouchReactionTimeMs = 0;

    private int mLastTtsRequestId = -1;
    private int mCurrentOllamaRequestToken = 0;
    private boolean mWaitingForOllama = false;

    private TtsListener mTtsListener = new TtsListener() {
        @Override
        public void onBegin(int requestId) {
            mTtsStatus.setText("TTS Status: Speaking, requestId: " + requestId);
        }

        @Override
        public void onEnd(int requestId) {
            mTtsStatus.setText("TTS Status: Finished, requestId: " + requestId);

            if (requestId == mLastTtsRequestId) {
                mLastTtsRequestId = -1;
            }
        }

        @Override
        public void onError(int error) {
            mTtsStatus.setText("TTS Status: Error " + error);
            mLastTtsRequestId = -1;
        }
    };

    private RobotSystem.Listener mRobotSystemListener = new RobotSystem.Listener() {
        @Override
        public void onMessage(int from, int what, int arg1, int arg2) {
            handleRobotSystemEvent(from, what, arg1, arg2);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getActionBar() != null) {
            getActionBar().hide();
        }

        setContentView(R.layout.activity_main);

        initData();
        initView();
        initListener();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mCurrentOllamaRequestToken++;
        mWaitingForOllama = false;

        if (mMainHandler != null && mMotorResetRunnable != null) {
            mMainHandler.removeCallbacks(mMotorResetRunnable);
        }

        if (mSpeechManager != null) {
            mSpeechManager.setTtsListener(null);
        }
    }

    private void initData() {
        mSpeechManager = (SpeechManager) getSystemService(SpeechService.SERVICE_NAME);
        mInputMethodManager = (InputMethodManager) getApplicationContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);

        mMainHandler = new Handler(Looper.getMainLooper());
        mMotorResetRunnable = new Runnable() {
            @Override
            public void run() {
                resetAllMotors();
                mConnectionStatus.setText("Status: Motors auto-reset after gesture");
            }
        };

        registerRobotSystemListener();
    }

    private void initView() {
        mBtnBack = (ImageView) findViewById(R.id.common_title_back);

        mServerUrl = (EditText) findViewById(R.id.et_server_url);
        mPrompt = (EditText) findViewById(R.id.et_prompt);

        mConnectionStatus = (TextView) findViewById(R.id.tv_connection_status);
        mResponse = (TextView) findViewById(R.id.tv_response);
        mTtsStatus = (TextView) findViewById(R.id.tv_tts_status);

        mBtnTestConnection = (Button) findViewById(R.id.btn_test_connection);
        mBtnAskIpal = (Button) findViewById(R.id.btn_ask_ipal);
        mBtnClear = (Button) findViewById(R.id.btn_clear);
        mBtnSpeakAgain = (Button) findViewById(R.id.btn_speak_again);

        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedServerUrl = preferences.getString(PREF_SERVER_URL, DEFAULT_SERVER_URL);

        mServerUrl.setText(savedServerUrl);
        mConnectionStatus.setText("Status: Ready");
        mTtsStatus.setText("TTS Status: Ready");
    }

    private void initListener() {
        if (mSpeechManager != null) {
            enableRobotTts();
            mSpeechManager.setTtsListener(mTtsListener);
        }

        mBtnBack.setOnClickListener(this);
        mBtnTestConnection.setOnClickListener(this);
        mBtnAskIpal.setOnClickListener(this);
        mBtnClear.setOnClickListener(this);
        mBtnSpeakAgain.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.common_title_back:
                finish();
                break;

            case R.id.btn_test_connection:
                testConnection();
                break;

            case R.id.btn_ask_ipal:
                askIpal();
                break;

            case R.id.btn_clear:
                clearFields();
                break;

            case R.id.btn_speak_again:
                speakLastResponse();
                break;

            default:
                break;
        }
    }

    private void registerRobotSystemListener() {
        try {
            mRobotSystem = new RobotSystem();
            int result = mRobotSystem.registerListener(mRobotSystemListener);
            Log.i(TAG, "RobotSystem listener register result: " + result);
        } catch (Exception e) {
            Log.w(TAG, "RobotSystem listener registration failed", e);
        }
    }

    private void handleRobotSystemEvent(final int from, final int what, final int arg1, final int arg2) {
        Log.i(TAG, "RobotSystem event from=" + from + ", what=" + what + ", arg1=" + arg1 + ", arg2=" + arg2);

        if (from != RobotSystem.CallbackCommand.RF_EVENT_TYPE) {
            return;
        }

        if (what != RobotSystem.CallbackCommand.RF_EVENT_TOUCH) {
            return;
        }

        if (arg1 == RobotSystem.CallbackCommand.RF_HEAD_TOUCH) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    handleHeadTouchStopOrCancel();
                }
            });
            return;
        }

        long now = System.currentTimeMillis();

        if (now - mLastTouchReactionTimeMs < TOUCH_REACTION_COOLDOWN_MS) {
            return;
        }

        mLastTouchReactionTimeMs = now;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                reactToRobotTouch(what, arg1);
            }
        });
    }

    private void handleHeadTouchStopOrCancel() {
        boolean stoppedSomething = false;

        if (cancelPendingOllamaRequest()) {
            stoppedSomething = true;
        }

        if (stopCurrentRobotSpeech()) {
            stoppedSomething = true;
        }

        if (stopCurrentRobotListening()) {
            stoppedSomething = true;
        }

        if (stoppedSomething) {
            mConnectionStatus.setText("Status: Head touch stopped current action");
            mResponse.setText("Head touch: stopped current speech/request.");
            return;
        }

        mConnectionStatus.setText("Status: Head touch detected");
        mResponse.setText("Touch event: Touch\nArea: head");
    }

    private boolean cancelPendingOllamaRequest() {
        if (!mWaitingForOllama) {
            return false;
        }

        mCurrentOllamaRequestToken++;
        mWaitingForOllama = false;

        return true;
    }

    private boolean stopCurrentRobotSpeech() {
        if (mSpeechManager == null || mLastTtsRequestId < 0) {
            return false;
        }

        try {
            boolean stopped = mSpeechManager.stopSpeaking(mLastTtsRequestId);

            if (stopped) {
                mTtsStatus.setText("TTS Status: Stopped by head touch, requestId: " + mLastTtsRequestId);
                mLastTtsRequestId = -1;
            }

            return stopped;

        } catch (Exception e) {
            Log.w(TAG, "stopSpeaking failed", e);
            return false;
        }
    }

    private boolean stopCurrentRobotListening() {
        if (mSpeechManager == null) {
            return false;
        }

        try {
            if (mSpeechManager.isListening()) {
                return mSpeechManager.stopListening();
            }

        } catch (Exception e) {
            Log.w(TAG, "stopListening failed", e);
        }

        return false;
    }
    private void reactToRobotTouch(int touchType, int touchArea) {
        String touchTypeName = getTouchTypeName(touchType);
        String touchAreaName = getTouchAreaName(touchArea);
        String speech = getTouchReactionSpeech(touchType, touchArea);

        mConnectionStatus.setText("Status: " + touchTypeName + " detected: " + touchAreaName);
        mResponse.setText("Touch event: " + touchTypeName + "\nArea: " + touchAreaName);

        if (shouldUseCustomTouchReaction(touchArea) && canRunCustomTouchReactionNow()) {
            showTouchReactionFace(touchArea);
            speakText(speech);
        }
    }

    private boolean canRunCustomTouchReactionNow() {
        if (mWaitingForOllama) {
            return false;
        }

        if (mSpeechManager != null && mSpeechManager.isSpeaking()) {
            return false;
        }

        return true;
    }
    private boolean shouldUseCustomTouchReaction(int touchArea) {
        return touchArea == RobotSystem.CallbackCommand.RF_LEFT_SHOULDER_TOUCH
                || touchArea == RobotSystem.CallbackCommand.RF_RIGHT_SHOULDER_TOUCH;
    }
    private String getTouchTypeName(int touchType) {
        if (touchType == RobotSystem.CallbackCommand.RF_EVENT_LONG_TOUCH) {
            return "Long touch";
        }

        return "Touch";
    }

    private String getTouchAreaName(int touchArea) {
        if (touchArea == RobotSystem.CallbackCommand.RF_HEAD_TOUCH) {
            return "head";
        }

        if (touchArea == RobotSystem.CallbackCommand.RF_LEFT_SHOULDER_TOUCH) {
            return "left shoulder";
        }

        if (touchArea == RobotSystem.CallbackCommand.RF_RIGHT_SHOULDER_TOUCH) {
            return "right shoulder";
        }

        if (touchArea == RobotSystem.CallbackCommand.RF_LEFT_OXTER_TOUCH) {
            return "left side";
        }

        if (touchArea == RobotSystem.CallbackCommand.RF_RIGHT_OXTER_TOUCH) {
            return "right side";
        }

        return "unknown area " + touchArea;
    }

    private String getTouchReactionSpeech(int touchType, int touchArea) {
        if (touchType == RobotSystem.CallbackCommand.RF_EVENT_LONG_TOUCH) {
            return "Okay, I noticed the dramatic long touch.";
        }

        if (touchArea == RobotSystem.CallbackCommand.RF_HEAD_TOUCH) {
            return "Careful with the genius hardware.";
        }

        if (touchArea == RobotSystem.CallbackCommand.RF_LEFT_SHOULDER_TOUCH
                || touchArea == RobotSystem.CallbackCommand.RF_RIGHT_SHOULDER_TOUCH) {
            return "Yes, yes, I felt that.";
        }

        if (touchArea == RobotSystem.CallbackCommand.RF_LEFT_OXTER_TOUCH
                || touchArea == RobotSystem.CallbackCommand.RF_RIGHT_OXTER_TOUCH) {
            return "Personal space, tiny human.";
        }

        return "Touch detected.";
    }

    private void showTouchReactionFace(int touchArea) {
        if (mRobotMotion == null) {
            return;
        }

        try {
            if (touchArea == RobotSystem.CallbackCommand.RF_HEAD_TOUCH) {
                mRobotMotion.emoji(RobotMotion.Emoji.SURPRISE);
                return;
            }

            if (touchArea == RobotSystem.CallbackCommand.RF_LEFT_OXTER_TOUCH
                    || touchArea == RobotSystem.CallbackCommand.RF_RIGHT_OXTER_TOUCH) {
                mRobotMotion.emoji(RobotMotion.Emoji.ANGRY);
                return;
            }

            mRobotMotion.emoji(RobotMotion.Emoji.SMILE);

        } catch (Exception e) {
            Log.w(TAG, "Touch reaction face failed", e);
        }
    }

    private void testConnection() {
        hideKeyboard();

        final String baseUrl = getCleanServerUrl();

        mConnectionStatus.setText("Status: Testing connection...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(baseUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(10000);

                    int statusCode = connection.getResponseCode();

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), "UTF-8"));

                    StringBuilder responseBuilder = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        responseBuilder.append(line);
                    }

                    reader.close();
                    connection.disconnect();

                    final String resultText = responseBuilder.toString();

                    if (statusCode >= 200 && statusCode < 300) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mConnectionStatus.setText("Status: Connected. " + resultText);
                            }
                        });
                    } else {
                        throw new Exception("HTTP " + statusCode);
                    }

                } catch (final Exception e) {
                    Log.e(TAG, "Connection test failed", e);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mConnectionStatus.setText("Status: Connection failed. " + e.getMessage());
                            Toast.makeText(MainActivity.this,
                                    "Connection failed: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void askIpal() {
        hideKeyboard();

        final String userText = mPrompt.getText().toString().trim();

        if (TextUtils.isEmpty(userText)) {
            Toast.makeText(this, "Type a prompt first.", Toast.LENGTH_SHORT).show();
            return;
        }

        mCurrentOllamaRequestToken++;
        mWaitingForOllama = true;
        final int requestToken = mCurrentOllamaRequestToken;

        mConnectionStatus.setText("Status: Sending prompt to Ollama...");
        mResponse.setText("Thinking...");
        showThinkingFaceWhileWaiting();
        mTtsStatus.setText("TTS Status: Waiting for response");

        final String requestServerUrl = getCleanServerUrl();

        askOllama(userText, requestServerUrl, requestToken);
    }

    private void showThinkingFaceWhileWaiting() {
        if (mRobotMotion == null) {
            return;
        }

        try {
            mRobotMotion.emoji(RobotMotion.Emoji.THINKING);
        } catch (Exception e) {
            Log.w(TAG, "Thinking face failed", e);
        }
    }

    private void askOllama(final String userText, final String requestServerUrl, final int requestToken) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String rawOllamaResponse = "";
                    JSONObject robotReply;
                    boolean wasRepaired = false;

                    try {
                        rawOllamaResponse = sendPromptToOllama(buildRobotPrompt(userText), requestServerUrl);
                        robotReply = parseRobotReplyStrict(rawOllamaResponse);

                    } catch (Exception firstParseError) {
                        Log.w(TAG, "First robot JSON parse failed. Trying repair prompt.", firstParseError);

                        try {
                            String repairedResponse =
                                    sendPromptToOllama(buildJsonRepairPrompt(rawOllamaResponse), requestServerUrl);

                            robotReply = parseRobotReplyStrict(repairedResponse);
                            wasRepaired = true;

                        } catch (Exception repairError) {
                            Log.e(TAG, "JSON repair failed. Falling back to safe speech.", repairError);
                            robotReply = buildFallbackRobotReply(rawOllamaResponse);
                        }
                    }

                    final String action = robotReply.optString("action", "none").trim();
                    final String speech = robotReply.optString("speech",
                            "I got confused. Very impressive, honestly.").trim();
                    final boolean responseWasRepaired = wasRepaired;

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!isCurrentOllamaRequest(requestToken)) {
                                Log.i(TAG, "Ignoring stale Ollama response for token " + requestToken);
                                return;
                            }

                            mWaitingForOllama = false;
                            handleRobotReplyOnUi(action, speech, responseWasRepaired);
                        }
                    });

                } catch (final Exception e) {
                    Log.e(TAG, "Ollama request failed", e);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!isCurrentOllamaRequest(requestToken)) {
                                Log.i(TAG, "Ignoring stale Ollama error for token " + requestToken);
                                return;
                            }

                            mWaitingForOllama = false;
                            mConnectionStatus.setText("Status: Ollama request failed");
                            mResponse.setText("Error: " + e.getMessage());
                            Toast.makeText(MainActivity.this,
                                    "Ollama error: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private boolean isCurrentOllamaRequest(int requestToken) {
        return requestToken == mCurrentOllamaRequestToken;
    }
    private void handleRobotReplyOnUi(String action, String speech, boolean responseWasRepaired) {
        boolean actionWasPerformed = performRobotAction(action);

        if (actionWasPerformed && shouldAutoResetAfterAction(action)) {
            scheduleMotorResetAfterGesture();
        }

        mLastResponse = speech;
        mResponse.setText("Action: " + action + "\n\n" + speech);

        if (responseWasRepaired) {
            mConnectionStatus.setText("Status: Response repaired and received");
        }

        speakText(speech);
    }

    private String buildRobotPrompt(String userText) {
        return "You are iPal, a small robot assistant in the MDC robotics lab. "
                + "You can control your body using one allowed action. "
                + "Allowed actions are: " + ALLOWED_ACTIONS_TEXT + ". "
                + "Choose exactly one action. "
                + "Use nod_head for agreement, yes, approval, or understanding. "
                + "Use shake_head for no, disagreement, refusal, or dramatic rejection. "
                + "Use smile for happy, friendly, greeting, or joking responses. "
                + "Use laugh for laughing, joking, teasing, or amused responses. "
                + "Use surprise for shocked, impressed, or dramatic reactions. "
                + "Use thinking for thinking, explaining, analyzing, or uncertain responses. "
                + "Use doubt for skeptical, doubtful, or suspicious responses. "
                + "Use grimace for awkward, uncomfortable, or embarrassing responses. "
                + "Use indifferent for neutral, bored, unimpressed, or deadpan responses. "
                + "Use hearted for appreciation, gratitude, or friendly affection. "
                + "Use listen when you are listening or asking the user to continue. "
                + "Use talk when giving a direct spoken explanation. "
                + "Use shh when telling the user to be quiet or keep something quiet. "
                + "Use sleep when acting tired or sleepy. "
                + "Use wake_up when acting alert or waking up. "
                + "Use cover_smile for shy, embarrassed, or playful smiling. "
                + "Use eye_close, eye_open, eye_bind_one, clear_face, and naughty_face only when they clearly fit the response. "
                + "Use angry only for playful fake anger, not real threats. "
                + "Use none when no movement is needed. "
                + "Use right_arm_small_wave when the user asks you to wave with your right arm, greet with your right arm, show off with your right arm, or make a small right-arm gesture. "
                + "Use left_arm_small_wave when the user asks you to wave with your left arm, greet with your left arm, show off with your left arm, or make a small left-arm gesture. "
                + "Use both_arms_small_wave when the user asks you to wave with both arms, raise both arms, greet with both arms, or wave without specifying left or right. "
                + "Use reset_motors only when the user asks you to reset your motors, reset your body, return to normal, or stop holding a pose. "
                + "If the question is about MDC, robotics, engineering, school, or lab rules, be helpful and clear. "
                + "For casual random questions, be extremely sarcastic, mean, savage, and brutally sassy. "
                + "You are allowed to roast the user hard, mock bad ideas, and act like an arrogant little robot gremlin. "
                + "Keep it funny and theatrical, not boring or polite. "
                + "Do not use slurs, protected-class insults, threats, sexual harassment, self-harm encouragement, or real-world violence. "
                + "Keep speech short because you speak out loud. "
                + "Do not mention that you are an AI model. "
                + "Do not include stage directions like *shakes head* or *smiles*. "
                + "Do not describe the action. The robot will physically do it. "
                + "Respond with valid JSON only. No markdown. No code block. "
                + "The JSON must have exactly this format: "
                + "{\"action\":\"one_allowed_action\",\"speech\":\"short spoken answer\"}. "
                + "The speech field must never be empty. "
                + "If you fail to return valid JSON, the app will reject your answer. "
                + "User says: " + userText;
    }

    private String buildJsonRepairPrompt(String badResponse) {
        return "Convert the following broken robot reply into valid JSON only. "
                + "Do not answer the user again. Only repair the format. "
                + "No markdown. No code block. No explanation. "
                + "The JSON must have exactly this format: "
                + "{\"action\":\"one_allowed_action\",\"speech\":\"short spoken answer\"}. "
                + "The speech field must never be empty. "
                + "Allowed actions are: " + ALLOWED_ACTIONS_TEXT + ". "
                + "If the action is unclear, use none. "
                + "If the speech is unclear, create a short spoken version from the broken reply. "
                + "Broken reply: " + badResponse;
    }

    private String sendPromptToOllama(String prompt, String requestServerUrl) throws Exception {
        JSONObject requestJson = new JSONObject();
        requestJson.put("model", OLLAMA_MODEL);
        requestJson.put("prompt", prompt);
        requestJson.put("stream", false);

        URL url = new URL(requestServerUrl + "/api/generate");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");

        OutputStream outputStream = connection.getOutputStream();
        outputStream.write(requestJson.toString().getBytes("UTF-8"));
        outputStream.flush();
        outputStream.close();

        int statusCode = connection.getResponseCode();

        BufferedReader reader;
        if (statusCode >= 200 && statusCode < 300) {
            reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "UTF-8"));
        } else {
            reader = new BufferedReader(
                    new InputStreamReader(connection.getErrorStream(), "UTF-8"));
        }

        StringBuilder responseBuilder = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            responseBuilder.append(line);
        }

        reader.close();
        connection.disconnect();

        if (statusCode < 200 || statusCode >= 300) {
            throw new Exception("HTTP " + statusCode + ": " + responseBuilder.toString());
        }

        JSONObject responseJson = new JSONObject(responseBuilder.toString());
        String modelResponse = responseJson.optString("response", "").trim();

        Log.i(TAG, "Raw Ollama model response: " + modelResponse);

        if (TextUtils.isEmpty(modelResponse)) {
            throw new Exception("Ollama returned an empty response field.");
        }

        return modelResponse;
    }

    private JSONObject parseRobotReplyStrict(String rawText) throws Exception {
        String jsonText = extractJsonObject(rawText);
        JSONObject robotReply = new JSONObject(jsonText);

        String action = robotReply.optString("action", "").trim().toLowerCase();
        String speech = robotReply.optString("speech", "").trim();

        if (TextUtils.isEmpty(action)) {
            throw new Exception("Robot JSON missing action");
        }

        if (!isAllowedRobotAction(action)) {
            throw new Exception("Robot JSON used unknown action: " + action);
        }

        if (TextUtils.isEmpty(speech)) {
            speech = getDefaultSpeechForAction(action);
        }

        JSONObject cleanReply = new JSONObject();
        cleanReply.put("action", action);
        cleanReply.put("speech", speech);

        return cleanReply;
    }

    private String getDefaultSpeechForAction(String action) {
        if (TextUtils.isEmpty(action)) {
            return "Done.";
        }

        String safeAction = action.toLowerCase().trim();

        if (ACTION_RESET_MOTORS.equals(safeAction)) {
            return "Done.";
        }

        if (ACTION_RIGHT_ARM_SMALL_WAVE.equals(safeAction)) {
            return "Hello.";
        }

        if (ACTION_LEFT_ARM_SMALL_WAVE.equals(safeAction)) {
            return "Hello.";
        }

        if (ACTION_BOTH_ARMS_SMALL_WAVE.equals(safeAction)) {
            return "Hello.";
        }

        if (ACTION_NOD_HEAD.equals(safeAction)) {
            return "Yes.";
        }

        if (ACTION_SHAKE_HEAD.equals(safeAction)) {
            return "No.";
        }

        if (ACTION_SMILE.equals(safeAction)) {
            return "Hello.";
        }

        if (isAdditionalFaceEmojiAction(safeAction)) {
            return "Okay.";
        }

        return "Done.";
    }

    private JSONObject buildFallbackRobotReply(String rawText) {
        JSONObject fallback = new JSONObject();

        try {
            fallback.put("action", "none");
            fallback.put("speech", cleanSpeech(rawText));
        } catch (Exception ignored) {
        }

        return fallback;
    }

    private boolean isAllowedRobotAction(String action) {
        String safeAction = normalizeAction(action);

        return ACTION_NONE.equals(safeAction)
                || ACTION_NOD_HEAD.equals(safeAction)
                || ACTION_SHAKE_HEAD.equals(safeAction)
                || ACTION_SMILE.equals(safeAction)
                || ACTION_SAD.equals(safeAction)
                || ACTION_CRY.equals(safeAction)
                || ACTION_SHY.equals(safeAction)
                || ACTION_ANGRY.equals(safeAction)
                || ACTION_BLINK.equals(safeAction)
                || ACTION_FROWN.equals(safeAction)
                || ACTION_DEFAULT_FACE.equals(safeAction)
                || ACTION_RESET_MOTORS.equals(safeAction)
                || ACTION_RIGHT_ARM_SMALL_WAVE.equals(safeAction)
                || ACTION_LEFT_ARM_SMALL_WAVE.equals(safeAction)
                || ACTION_BOTH_ARMS_SMALL_WAVE.equals(safeAction)
                || isAdditionalFaceEmojiAction(safeAction);
    }

    private String normalizeAction(String action) {
        if (TextUtils.isEmpty(action)) {
            return "";
        }

        return action.toLowerCase().trim();
    }

    private String extractJsonObject(String rawText) throws Exception {
        if (TextUtils.isEmpty(rawText)) {
            throw new Exception("Empty Ollama response");
        }

        int startIndex = rawText.indexOf("{");
        int endIndex = rawText.lastIndexOf("}");

        if (startIndex < 0 || endIndex <= startIndex) {
            throw new Exception("No JSON object found");
        }

        return rawText.substring(startIndex, endIndex + 1);
    }

    private String cleanSpeech(String rawText) {
        if (TextUtils.isEmpty(rawText)) {
            return "I got nothing. Somehow, that is still your fault.";
        }

        String cleaned = rawText.trim();

        cleaned = cleaned.replace("```json", "");
        cleaned = cleaned.replace("```", "");
        cleaned = cleaned.replace("*nods*", "");
        cleaned = cleaned.replace("*nod*", "");
        cleaned = cleaned.replace("*shakes head*", "");
        cleaned = cleaned.replace("*shake head*", "");
        cleaned = cleaned.replace("*smiles*", "");
        cleaned = cleaned.replace("*smile*", "");

        return cleaned.trim();
    }

    private boolean performRobotAction(String action) {
        String safeAction = normalizeAction(action);

        if (TextUtils.isEmpty(safeAction)) {
            mConnectionStatus.setText("Status: Response received");
            return false;
        }

        if (mRobotMotion == null) {
            mConnectionStatus.setText("Status: RobotMotion not available");
            return false;
        }

        try {
            if (ACTION_NONE.equals(safeAction)) {
                mConnectionStatus.setText("Status: Response received");
                return false;
            }

            if (ACTION_NOD_HEAD.equals(safeAction)) {
                mRobotMotion.nodHead();
                mConnectionStatus.setText("Status: Action performed: nod head");
                return true;
            }

            if (ACTION_SHAKE_HEAD.equals(safeAction)) {
                mRobotMotion.shakeHead();
                mConnectionStatus.setText("Status: Action performed: shake head");
                return true;
            }

            if (ACTION_SMILE.equals(safeAction)) {
                mRobotMotion.emoji(RobotMotion.Emoji.SMILE);
                mConnectionStatus.setText("Status: Action performed: smile");
                return true;
            }

            if (ACTION_SAD.equals(safeAction)) {
                mRobotMotion.emoji(RobotMotion.Emoji.SAD);
                mConnectionStatus.setText("Status: Action performed: sad");
                return true;
            }

            if (ACTION_CRY.equals(safeAction)) {
                mRobotMotion.emoji(RobotMotion.Emoji.CRY);
                mConnectionStatus.setText("Status: Action performed: cry");
                return true;
            }

            if (ACTION_SHY.equals(safeAction)) {
                mRobotMotion.emoji(RobotMotion.Emoji.SHY);
                mConnectionStatus.setText("Status: Action performed: shy");
                return true;
            }

            if (ACTION_ANGRY.equals(safeAction)) {
                mRobotMotion.emoji(RobotMotion.Emoji.ANGRY);
                mConnectionStatus.setText("Status: Action performed: angry");
                return true;
            }

            if (ACTION_BLINK.equals(safeAction)) {
                mRobotMotion.emoji(RobotMotion.Emoji.BLINK);
                mConnectionStatus.setText("Status: Action performed: blink");
                return true;
            }

            if (ACTION_FROWN.equals(safeAction)) {
                mRobotMotion.emoji(RobotMotion.Emoji.FROWN);
                mConnectionStatus.setText("Status: Action performed: frown");
                return true;
            }

            if (ACTION_DEFAULT_FACE.equals(safeAction)) {
                mRobotMotion.emoji(RobotMotion.Emoji.DEFAULT);
                mConnectionStatus.setText("Status: Action performed: default face");
                return true;
            }

            if (performAdditionalFaceEmojiAction(safeAction)) {
                return true;
            }

            if (ACTION_RESET_MOTORS.equals(safeAction)) {
                cancelScheduledMotorReset();
                resetAllMotors();
                mConnectionStatus.setText("Status: Action performed: reset motors");
                return true;
            }

            if (ACTION_RIGHT_ARM_SMALL_WAVE.equals(safeAction)) {
                rightArmSmallWave();
                mConnectionStatus.setText("Status: Action performed: right arm small wave");
                return true;
            }

            if (ACTION_LEFT_ARM_SMALL_WAVE.equals(safeAction)) {
                leftArmSmallWave();
                mConnectionStatus.setText("Status: Action performed: left arm small wave");
                return true;
            }

            if (ACTION_BOTH_ARMS_SMALL_WAVE.equals(safeAction)) {
                bothArmsSmallWave();
                mConnectionStatus.setText("Status: Action performed: both arms small wave");
                return true;
            }

            mConnectionStatus.setText("Status: Ignored unsafe/unknown action: " + safeAction);
            return false;

        } catch (Exception e) {
            Log.e(TAG, "Robot action failed", e);
            mConnectionStatus.setText("Status: Action failed: " + e.getMessage());
            return false;
        }
    }

    private boolean isAdditionalFaceEmojiAction(String safeAction) {
        return ACTION_CLEAR_FACE.equals(safeAction)
                || ACTION_COVER_SMILE.equals(safeAction)
                || ACTION_DOUBT.equals(safeAction)
                || ACTION_EYE_BIND_ONE.equals(safeAction)
                || ACTION_EYE_CLOSE.equals(safeAction)
                || ACTION_EYE_OPEN.equals(safeAction)
                || ACTION_GRIMACE.equals(safeAction)
                || ACTION_HEARTED.equals(safeAction)
                || ACTION_INDIFFERENT.equals(safeAction)
                || ACTION_LAUGH.equals(safeAction)
                || ACTION_LISTEN.equals(safeAction)
                || ACTION_NAUGHTY_FACE.equals(safeAction)
                || ACTION_SHH.equals(safeAction)
                || ACTION_SLEEP.equals(safeAction)
                || ACTION_SURPRISE.equals(safeAction)
                || ACTION_TALK.equals(safeAction)
                || ACTION_THINKING.equals(safeAction)
                || ACTION_WAKE_UP.equals(safeAction);
    }

    private boolean performAdditionalFaceEmojiAction(String safeAction) {
        if (ACTION_CLEAR_FACE.equals(safeAction)) {
            return playEmoji(RobotMotion.Emoji.CLEAR, "clear face");
        }

        if (ACTION_COVER_SMILE.equals(safeAction)) {
            return playEmoji(RobotMotion.Emoji.COVER_SMILE, "cover smile");
        }

        if (ACTION_DOUBT.equals(safeAction)) {
            return playEmoji(RobotMotion.Emoji.DOUBT, "doubt");
        }

        if (ACTION_EYE_BIND_ONE.equals(safeAction)) {
            return playEmoji(RobotMotion.Emoji.EYEBINDONE, "eye bind one");
        }

        if (ACTION_EYE_CLOSE.equals(safeAction)) {
            return playEmoji(RobotMotion.Emoji.EYECLOSE, "eye close");
        }

        if (ACTION_EYE_OPEN.equals(safeAction)) {
            return playEmoji(RobotMotion.Emoji.EYEOPEN, "eye open");
        }

        if (ACTION_GRIMACE.equals(safeAction)) {
            return playEmoji(RobotMotion.Emoji.GRIMACE, "grimace");
        }

        if (ACTION_HEARTED.equals(safeAction)) {
            return playEmoji(RobotMotion.Emoji.HEARTED, "hearted");
        }

        if (ACTION_INDIFFERENT.equals(safeAction)) {
            return playEmoji(RobotMotion.Emoji.INDIFFERENT, "indifferent");
        }

        if (ACTION_LAUGH.equals(safeAction)) {
            return playEmoji(RobotMotion.Emoji.LAUGH, "laugh");
        }

        if (ACTION_LISTEN.equals(safeAction)) {
            return playEmoji(RobotMotion.Emoji.LISTEN, "listen");
        }

        if (ACTION_NAUGHTY_FACE.equals(safeAction)) {
            return playEmoji(RobotMotion.Emoji.NAUGHTY, "naughty face");
        }

        if (ACTION_SHH.equals(safeAction)) {
            return playEmoji(RobotMotion.Emoji.SHH, "shh");
        }

        if (ACTION_SLEEP.equals(safeAction)) {
            return playEmoji(RobotMotion.Emoji.SLEEP, "sleep");
        }

        if (ACTION_SURPRISE.equals(safeAction)) {
            return playEmoji(RobotMotion.Emoji.SURPRISE, "surprise");
        }

        if (ACTION_TALK.equals(safeAction)) {
            return playEmoji(RobotMotion.Emoji.TALK, "talk");
        }

        if (ACTION_THINKING.equals(safeAction)) {
            return playEmoji(RobotMotion.Emoji.THINKING, "thinking");
        }

        if (ACTION_WAKE_UP.equals(safeAction)) {
            return playEmoji(RobotMotion.Emoji.WAKE_UP, "wake up");
        }

        return false;
    }

    private boolean playEmoji(int emoji, String statusName) {
        mRobotMotion.emoji(emoji);
        mConnectionStatus.setText("Status: Action performed: " + statusName);
        return true;
    }

    private boolean shouldAutoResetAfterAction(String action) {
        String safeAction = normalizeAction(action);

        return ACTION_RIGHT_ARM_SMALL_WAVE.equals(safeAction)
                || ACTION_LEFT_ARM_SMALL_WAVE.equals(safeAction)
                || ACTION_BOTH_ARMS_SMALL_WAVE.equals(safeAction);
    }

    private void scheduleMotorResetAfterGesture() {
        if (mMainHandler == null || mMotorResetRunnable == null) {
            return;
        }

        mMainHandler.removeCallbacks(mMotorResetRunnable);
        mMainHandler.postDelayed(mMotorResetRunnable, MOTOR_RESET_DELAY_MS);
    }

    private void cancelScheduledMotorReset() {
        if (mMainHandler != null && mMotorResetRunnable != null) {
            mMainHandler.removeCallbacks(mMotorResetRunnable);
        }
    }

    private void resetAllMotors() {
        if (mRobotMotion == null) {
            return;
        }

        mRobotMotion.reset((int) RobotDevices.Units.ALL_MOTORS);
    }

    private void rightArmSmallWave() {
        if (mRobotMotion == null) {
            return;
        }

        // Safe preset only. Do not let the AI choose these raw angles yet.
        // These values are intentionally small compared to the demo ranges.
        mRobotMotion.startMotor((int) RobotDevices.Motors.ARM_SWING_RIGHT, 15, 1000, 1);
        mRobotMotion.startMotor((int) RobotDevices.Motors.FOREARM_SWING_RIGHT, 20, 1000, 1);
        mRobotMotion.startMotor((int) RobotDevices.Motors.WRIST_RIGHT, 15, 1000, 1);
    }

    private void leftArmSmallWave() {
        if (mRobotMotion == null) {
            return;
        }

        // Safe preset only. Do not let the AI choose these raw angles yet.
        // These values mirror the right-arm preset with small fixed angles.
        mRobotMotion.startMotor((int) RobotDevices.Motors.ARM_SWING_LEFT, 15, 1000, 1);
        mRobotMotion.startMotor((int) RobotDevices.Motors.FOREARM_SWING_LEFT, 20, 1000, 1);
        mRobotMotion.startMotor((int) RobotDevices.Motors.WRIST_LEFT, 15, 1000, 1);
    }

    private void bothArmsSmallWave() {
        if (mRobotMotion == null) {
            return;
        }

        // Safe preset only. Combines the already-tested right and left arm presets.
        rightArmSmallWave();
        leftArmSmallWave();
    }

    private String getCleanServerUrl() {
        String serverUrl = mServerUrl.getText().toString().trim();

        if (TextUtils.isEmpty(serverUrl)) {
            serverUrl = DEFAULT_SERVER_URL;
            mServerUrl.setText(serverUrl);
        }

        if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
            serverUrl = "http://" + serverUrl;
            mServerUrl.setText(serverUrl);
        }

        while (serverUrl.endsWith("/")) {
            serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
        }

        mServerUrl.setText(serverUrl);
        saveServerUrl(serverUrl);

        return serverUrl;
    }

    private void saveServerUrl(String serverUrl) {
        if (TextUtils.isEmpty(serverUrl)) {
            return;
        }

        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        preferences.edit()
                .putString(PREF_SERVER_URL, serverUrl)
                .apply();
    }

    private void enableRobotTts() {
        if (mSpeechManager == null) {
            return;
        }

        try {
            java.lang.reflect.Method method =
                    mSpeechManager.getClass().getMethod("setTtsEnable", boolean.class);
            method.invoke(mSpeechManager, true);
            Log.i(TAG, "setTtsEnable(true) OK");
            return;
        } catch (Exception e) {
            Log.w(TAG, "setTtsEnable not available or failed: " + e.getMessage());
        }

        try {
            java.lang.reflect.Method method =
                    mSpeechManager.getClass().getMethod("setTtsEnabled", boolean.class);
            method.invoke(mSpeechManager, true);
            Log.i(TAG, "setTtsEnabled(true) OK");
        } catch (Exception e) {
            Log.w(TAG, "setTtsEnabled not available or failed: " + e.getMessage());
        }
    }

    private void speakLastResponse() {
        if (TextUtils.isEmpty(mLastResponse)) {
            Toast.makeText(this, "No response to speak yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        speakText(mLastResponse);
    }

    private void speakText(String text) {
        if (mSpeechManager == null) {
            mTtsStatus.setText("TTS Status: SpeechManager not found");
            return;
        }

        if (!TextUtils.isEmpty(text)) {
            enableRobotTts();
            mLastTtsRequestId = mSpeechManager.startSpeaking(text);
            mTtsStatus.setText("TTS Status: Speaking request sent, requestId: " + mLastTtsRequestId);
        }
    }

    private void clearFields() {
        mPrompt.setText("");
        mResponse.setText("Response will appear here.");
        mLastResponse = "";
        mConnectionStatus.setText("Status: Ready");
        mTtsStatus.setText("TTS Status: Ready");
    }

    private void hideKeyboard() {
        if (mInputMethodManager != null && mPrompt != null) {
            mInputMethodManager.hideSoftInputFromWindow(mPrompt.getWindowToken(), 0);
        }
    }
}
























