package com.example.robotvoicedemo;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.robot.hw.RobotDevices;
import android.robot.hw.RobotSystem;
import android.os.Bundle;
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
    private static final String ACTION_CUSTOM_ARM_POSE = "custom_arm_pose";
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
                    + ACTION_CUSTOM_ARM_POSE + ", "
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

    private TextView mLeftArmRotation;
    private TextView mLeftArmSwing;
    private TextView mLeftForearmRotation;
    private TextView mLeftForearmSwing;
    private TextView mLeftWrist;

    private TextView mRightArmRotation;
    private TextView mRightArmSwing;
    private TextView mRightForearmRotation;
    private TextView mRightForearmSwing;
    private TextView mRightWrist;

    private Button mBtnTestConnection;
    private Button mBtnAskIpal;
    private Button mBtnClear;
    private Button mBtnSpeakAgain;

    private InputMethodManager mInputMethodManager;
    private SpeechManager mSpeechManager;
    private RobotMotion mRobotMotion = new RobotMotion();
    private RobotSystem mRobotSystem;
    private VisionEventBridge mVisionEventBridge;
    private MotorStatusController mMotorStatusController;
    private ArmPoseController mArmPoseController;
    private RobotReplyParser mRobotReplyParser;

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

    private final MotorStatusController.Callback mMotorStatusCallback =
            new MotorStatusController.Callback() {
                @Override
                public void onMotorStatusUpdated(
                        int motorId,
                        int angle,
                        int direction,
                        int speed) {

                    updateMotorStatusText(motorId, angle);
                }
            };

    private final ArmPoseController.Callback mArmPoseCallback =
            new ArmPoseController.Callback() {
                @Override
                public void onArmPoseStatusChanged(String statusText) {
                    if (mConnectionStatus != null) {
                        mConnectionStatus.setText(statusText);
                    }
                }
            };

    private final RobotReplyParser.ActionPolicy mRobotReplyActionPolicy =
            new RobotReplyParser.ActionPolicy() {
                @Override
                public boolean isAllowedAction(String action) {
                    return MainActivity.this.isAllowedRobotAction(action);
                }

                @Override
                public boolean isCustomArmPoseAction(String action) {
                    return ACTION_CUSTOM_ARM_POSE.equals(normalizeAction(action));
                }

                @Override
                public String getDefaultSpeechForAction(String action) {
                    return MainActivity.this.getDefaultSpeechForAction(action);
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
    protected void onResume() {
        super.onResume();

        if (mVisionEventBridge == null) {
            initVisionEventBridge();
        } else {
            mVisionEventBridge.resume();
        }

        if (mMotorStatusController != null) {
            mMotorStatusController.start();
        }
    }

    @Override
    protected void onPause() {
        if (mMotorStatusController != null) {
            mMotorStatusController.stop();
        }

        if (mVisionEventBridge != null) {
            mVisionEventBridge.pause();
        }

        super.onPause();
    }


    @Override
    protected void onDestroy() {
        if (mVisionEventBridge != null) {
            mVisionEventBridge.destroy();
            mVisionEventBridge = null;
        }

        super.onDestroy();

        mCurrentOllamaRequestToken++;
        mWaitingForOllama = false;

        if (mArmPoseController != null) {
            mArmPoseController.destroy();
            mArmPoseController = null;
        }

        if (mMotorStatusController != null) {
            mMotorStatusController.destroy();
            mMotorStatusController = null;
        }

        if (mSpeechManager != null) {
            mSpeechManager.setTtsListener(null);
        }
    }

    private void initData() {
        mSpeechManager = (SpeechManager) getSystemService(SpeechService.SERVICE_NAME);
        mInputMethodManager = (InputMethodManager) getApplicationContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);

        mRobotReplyParser = new RobotReplyParser(mRobotReplyActionPolicy);
        mArmPoseController = new ArmPoseController(mRobotMotion, mArmPoseCallback);
        mMotorStatusController = new MotorStatusController(mRobotMotion, mMotorStatusCallback);

        registerRobotSystemListener();
        initVisionEventBridge();
    }

    private void initView() {
        mBtnBack = (ImageView) findViewById(R.id.common_title_back);

        mServerUrl = (EditText) findViewById(R.id.et_server_url);
        mPrompt = (EditText) findViewById(R.id.et_prompt);

        mConnectionStatus = (TextView) findViewById(R.id.tv_connection_status);
        mResponse = (TextView) findViewById(R.id.tv_response);
        mTtsStatus = (TextView) findViewById(R.id.tv_tts_status);

        mLeftArmRotation = (TextView) findViewById(R.id.tv_left_arm_rotation);
        mLeftArmSwing = (TextView) findViewById(R.id.tv_left_arm_swing);
        mLeftForearmRotation = (TextView) findViewById(R.id.tv_left_forearm_rotation);
        mLeftForearmSwing = (TextView) findViewById(R.id.tv_left_forearm_swing);
        mLeftWrist = (TextView) findViewById(R.id.tv_left_wrist);

        mRightArmRotation = (TextView) findViewById(R.id.tv_right_arm_rotation);
        mRightArmSwing = (TextView) findViewById(R.id.tv_right_arm_swing);
        mRightForearmRotation = (TextView) findViewById(R.id.tv_right_forearm_rotation);
        mRightForearmSwing = (TextView) findViewById(R.id.tv_right_forearm_swing);
        mRightWrist = (TextView) findViewById(R.id.tv_right_wrist);

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


    private void initVisionEventBridge() {
        if (mVisionEventBridge != null) {
            return;
        }

        mVisionEventBridge = new VisionEventBridge(this);
        mVisionEventBridge.start();
        Log.i(TAG, "VisionEventBridge initialized.");
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
                        robotReply = mRobotReplyParser.parseStrict(rawOllamaResponse);

                    } catch (Exception firstParseError) {
                        Log.w(TAG, "First robot JSON parse failed. Trying repair prompt.", firstParseError);

                        try {
                            String repairedResponse =
                                    sendPromptToOllama(buildJsonRepairPrompt(rawOllamaResponse), requestServerUrl);

                            robotReply = mRobotReplyParser.parseStrict(repairedResponse);
                            wasRepaired = true;

                        } catch (Exception repairError) {
                            Log.e(TAG, "JSON repair failed. Falling back to safe speech.", repairError);
                            robotReply = mRobotReplyParser.buildFallbackReply(rawOllamaResponse);
                        }
                    }

                    final JSONObject finalRobotReply = robotReply;
                    final boolean responseWasRepaired = wasRepaired;

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!isCurrentOllamaRequest(requestToken)) {
                                Log.i(TAG, "Ignoring stale Ollama response for token " + requestToken);
                                return;
                            }

                            mWaitingForOllama = false;
                            handleRobotReplyOnUi(finalRobotReply, responseWasRepaired);
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
    private void handleRobotReplyOnUi(JSONObject robotReply, boolean responseWasRepaired) {
        String action = robotReply.optString("action", ACTION_NONE).trim();
        String speech = robotReply.optString(
                "speech",
                "I got confused. Very impressive, honestly."
        ).trim();

        boolean actionWasPerformed = performRobotAction(robotReply);

        mLastResponse = speech;

        StringBuilder responseText = new StringBuilder();
        responseText.append("Action: ").append(action);
        responseText.append("\n\n").append(speech);

        if (ACTION_CUSTOM_ARM_POSE.equals(normalizeAction(action))) {
            JSONObject armPose = robotReply.optJSONObject("arm_pose");

            if (armPose != null) {
                responseText.append("\n\nCommanded arm pose:");
                responseText.append("\nSide: ")
                        .append(armPose.optString("side", "unknown"));
                responseText.append("\nArm rotation: ")
                        .append(armPose.optInt("arm_rotation"))
                        .append(" deg");
                responseText.append("\nArm swing: ")
                        .append(armPose.optInt("arm_swing"))
                        .append(" deg");
                responseText.append("\nForearm rotation: ")
                        .append(armPose.optInt("forearm_rotation"))
                        .append(" deg");
                responseText.append("\nForearm swing: ")
                        .append(armPose.optInt("forearm_swing"))
                        .append(" deg");
                responseText.append("\nWrist: ")
                        .append(armPose.optInt("wrist"))
                        .append(" deg");
                responseText.append("\nMovement time: ")
                        .append(armPose.optInt(
                                "duration_ms",
                                ArmPoseController.DEFAULT_ARM_MOVE_DURATION_MS))
                        .append(" ms");
                responseText.append("\nHold time: ")
                        .append(armPose.optInt(
                                "hold_ms",
                                ArmPoseController.DEFAULT_ARM_HOLD_DURATION_MS))
                        .append(" ms");
            }
        }

        mResponse.setText(responseText.toString());

        if (responseWasRepaired) {
            mConnectionStatus.setText("Status: Response repaired and received");
        } else if (!actionWasPerformed && ACTION_NONE.equals(normalizeAction(action))) {
            mConnectionStatus.setText("Status: Response received");
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
                + "Use smile for happy, friendly, greeting, or joking responses when arm movement is not needed. "
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
                + "Use eye_close, eye_open, eye_bind_one, clear_face, and naughty_face only when they clearly fit. "
                + "Use angry only for playful fake anger, not real threats. "
                + "Use none when no movement is needed. "
                + "Use custom_arm_pose whenever the user asks you to move, raise, lower, bend, rotate, pose, present, point, greet, or gesture with one arm. "
                + "custom_arm_pose is one static pose, not an animation or repeated wave. "
                + "For custom_arm_pose, include arm_pose with every required field. "
                + "arm_pose.side must be right or left. "
                + "Use these RobotMotionDemo angle limits: arm_rotation -25 to 175; arm_swing 0 to 65; "
                + "forearm_rotation -80 to 80; forearm_swing 0 to 90; wrist -80 to 80. "
                + "The zero-angle reference is the normal human-like standby pose: the arm points fully downward, "
                + "the elbow points toward the back, the forearm is straight, and the fist is neutral with the thumb pointing forward. "
                + "Treat angle 0 as neutral standby, not as an arbitrary midpoint. "
                + "Build requested poses by changing joints away from this known standby reference. "
                + "Choose angles that physically match the requested pose. "
                + "Use duration_ms from 1000 to 5000. Slower, larger poses should use 2500 to 4000. "
                + "Use hold_ms from 1000 to 8000 so the pose remains visible after the motors finish moving. "
                + "Do not copy one fixed arm pose for every request. Choose different joint angles based on the requested meaning. "
                + "For a greeting pose, raise the selected arm and bend the forearm so the hand is visibly elevated. "
                + "Do not create a forward handshake pose unless the user specifically asks to shake hands. "
                + "For pointing, presenting, resting, greeting, and raised-hand poses, choose visibly different joint combinations. "
                + "Use reset_motors only when the user asks you to reset your motors, return to neutral, or stop holding a pose. "
                + "If the question is about MDC, robotics, engineering, school, or lab rules, be helpful and clear. "
                + "For casual random questions, be extremely sarcastic, mean, savage, and brutally sassy. "
                + "You may roast the user hard and act like an arrogant little robot gremlin. "
                + "Keep it funny and theatrical. "
                + "Do not use slurs, protected-class insults, threats, sexual harassment, self-harm encouragement, or real-world violence. "
                + "Keep speech short because you speak out loud. "
                + "Do not mention that you are an AI model. "
                + "Do not include stage directions or describe the action; the robot performs it physically. "
                + "Respond with valid JSON only. No markdown, code block, or extra keys. "
                + "For non-arm actions use exactly: "
                + "{\"action\":\"one_allowed_action\",\"speech\":\"short spoken answer\"}. "
                + "For custom_arm_pose use exactly: "
                + "{\"action\":\"custom_arm_pose\",\"speech\":\"short spoken answer\","
                + "\"arm_pose\":{\"side\":\"right_or_left\",\"arm_rotation\":0,"
                + "\"arm_swing\":0,\"forearm_rotation\":0,\"forearm_swing\":0,"
                + "\"wrist\":0,\"duration_ms\":3000,\"hold_ms\":4000}}. "
                + "The speech field must never be empty. "
                + "User says: " + userText;
    }

    private String buildJsonRepairPrompt(String badResponse) {
        return "Convert the following broken robot reply into valid JSON only. "
                + "Do not answer the user again. Only repair the format. "
                + "No markdown, code block, explanation, or extra keys. "
                + "Allowed actions are: " + ALLOWED_ACTIONS_TEXT + ". "
                + "For non-arm actions use exactly: "
                + "{\"action\":\"one_allowed_action\",\"speech\":\"short spoken answer\"}. "
                + "For custom_arm_pose preserve or create arm_pose using exactly: "
                + "{\"action\":\"custom_arm_pose\",\"speech\":\"short spoken answer\","
                + "\"arm_pose\":{\"side\":\"right_or_left\",\"arm_rotation\":0,"
                + "\"arm_swing\":0,\"forearm_rotation\":0,\"forearm_swing\":0,"
                + "\"wrist\":0,\"duration_ms\":3000,\"hold_ms\":4000}}. "
                + "Limits: arm_rotation -25..175, arm_swing 0..65, forearm_rotation -80..80, "
                + "forearm_swing 0..90, wrist -80..80, duration_ms 1000..5000, hold_ms 1000..8000. "
                + "The speech field must never be empty. "
                + "If the action is unclear, use none. "
                + "If speech is unclear, create a short spoken version from the broken reply. "
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

    private String getDefaultSpeechForAction(String action) {
        if (TextUtils.isEmpty(action)) {
            return "Done.";
        }

        String safeAction = action.toLowerCase().trim();

        if (ACTION_RESET_MOTORS.equals(safeAction)) {
            return "Done.";
        }

        if (ACTION_CUSTOM_ARM_POSE.equals(safeAction)) {
            return "Done.";
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
                || ACTION_CUSTOM_ARM_POSE.equals(safeAction)
                || isAdditionalFaceEmojiAction(safeAction);
    }

    private String normalizeAction(String action) {
        if (TextUtils.isEmpty(action)) {
            return "";
        }

        return action.toLowerCase().trim();
    }

    private boolean performRobotAction(JSONObject robotReply) {
        String safeAction = normalizeAction(robotReply.optString("action", ACTION_NONE));

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
                mArmPoseController.cancelScheduledReset();
                mArmPoseController.resetAllMotors();
                mConnectionStatus.setText("Status: Action performed: reset motors");
                return true;
            }

            if (ACTION_CUSTOM_ARM_POSE.equals(safeAction)) {
                JSONObject armPose = robotReply.optJSONObject("arm_pose");

                if (armPose == null) {
                    mConnectionStatus.setText("Status: custom_arm_pose missing arm_pose");
                    return false;
                }

                return mArmPoseController.performCustomArmPose(armPose);
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

    private void updateMotorStatusText(int motorId, int angle) {
        TextView targetView = null;

        if (motorId == (int) RobotDevices.Motors.ARM_ROTATION_LEFT) {
            targetView = mLeftArmRotation;
        } else if (motorId == (int) RobotDevices.Motors.ARM_SWING_LEFT) {
            targetView = mLeftArmSwing;
        } else if (motorId == (int) RobotDevices.Motors.FOREARM_ROTATION_LEFT) {
            targetView = mLeftForearmRotation;
        } else if (motorId == (int) RobotDevices.Motors.FOREARM_SWING_LEFT) {
            targetView = mLeftForearmSwing;
        } else if (motorId == (int) RobotDevices.Motors.WRIST_LEFT) {
            targetView = mLeftWrist;
        } else if (motorId == (int) RobotDevices.Motors.ARM_ROTATION_RIGHT) {
            targetView = mRightArmRotation;
        } else if (motorId == (int) RobotDevices.Motors.ARM_SWING_RIGHT) {
            targetView = mRightArmSwing;
        } else if (motorId == (int) RobotDevices.Motors.FOREARM_ROTATION_RIGHT) {
            targetView = mRightForearmRotation;
        } else if (motorId == (int) RobotDevices.Motors.FOREARM_SWING_RIGHT) {
            targetView = mRightForearmSwing;
        } else if (motorId == (int) RobotDevices.Motors.WRIST_RIGHT) {
            targetView = mRightWrist;
        }

        if (targetView != null) {
            targetView.setText(angle + "°");
        }
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
