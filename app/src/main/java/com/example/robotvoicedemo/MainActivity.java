package com.example.robotvoicedemo;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.robot.hw.RobotDevices;
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

    private String mLastResponse = "";

    private TtsListener mTtsListener = new TtsListener() {
        @Override
        public void onBegin(int requestId) {
            mTtsStatus.setText("TTS Status: Speaking, requestId: " + requestId);
        }

        @Override
        public void onEnd(int requestId) {
            mTtsStatus.setText("TTS Status: Finished, requestId: " + requestId);
        }

        @Override
        public void onError(int error) {
            mTtsStatus.setText("TTS Status: Error " + error);
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

        if (mSpeechManager != null) {
            mSpeechManager.setTtsListener(null);
        }
    }

    private void initData() {
        mSpeechManager = (SpeechManager) getSystemService(SpeechService.SERVICE_NAME);
        mInputMethodManager = (InputMethodManager) getApplicationContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
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

        mConnectionStatus.setText("Status: Sending prompt to Ollama...");
        mResponse.setText("Thinking...");
        mTtsStatus.setText("TTS Status: Waiting for response");

        final String requestServerUrl = getCleanServerUrl();

        askOllama(userText, requestServerUrl);
    }

    private void askOllama(final String userText, final String requestServerUrl) {
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
                            performRobotAction(action);

                            mLastResponse = speech;
                            mResponse.setText("Action: " + action + "\n\n" + speech);

                            if (responseWasRepaired) {
                                mConnectionStatus.setText("Status: Response repaired and received");
                            }

                            speakText(speech);
                        }
                    });

                } catch (final Exception e) {
                    Log.e(TAG, "Ollama request failed", e);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
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

    private String buildRobotPrompt(String userText) {
        return "You are iPal, a small robot assistant in the MDC robotics lab. "
                + "You can control your body using one allowed action. "
                + "Allowed actions are: none, nod_head, shake_head, smile, sad, cry, shy, angry, blink, frown, default_face, reset_motors, right_arm_small_wave, left_arm_small_wave, both_arms_small_wave. "
                + "Choose exactly one action. "
                + "Use nod_head for agreement, yes, approval, or understanding. "
                + "Use shake_head for no, disagreement, refusal, or dramatic rejection. "
                + "Use smile for happy, friendly, greeting, or joking responses. "
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
                + "Allowed actions are: none, nod_head, shake_head, smile, sad, cry, shy, angry, blink, frown, default_face, reset_motors, right_arm_small_wave, left_arm_small_wave, both_arms_small_wave. "
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

        if ("reset_motors".equals(safeAction)) {
            return "Done.";
        }

        if ("right_arm_small_wave".equals(safeAction)) {
            return "Hello.";
        }

        if ("left_arm_small_wave".equals(safeAction)) {
            return "Hello.";
        }

        if ("nod_head".equals(safeAction)) {
            return "Yes.";
        }

        if ("shake_head".equals(safeAction)) {
            return "No.";
        }

        if ("smile".equals(safeAction)) {
            return "Hello.";
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
        if (TextUtils.isEmpty(action)) {
            return false;
        }

        String safeAction = action.toLowerCase().trim();

        return "none".equals(safeAction)
                || "nod_head".equals(safeAction)
                || "shake_head".equals(safeAction)
                || "smile".equals(safeAction)
                || "sad".equals(safeAction)
                || "cry".equals(safeAction)
                || "shy".equals(safeAction)
                || "angry".equals(safeAction)
                || "blink".equals(safeAction)
                || "frown".equals(safeAction)
                || "default_face".equals(safeAction)
                || "reset_motors".equals(safeAction)
                || "right_arm_small_wave".equals(safeAction)
                || "left_arm_small_wave".equals(safeAction)
                || "both_arms_small_wave".equals(safeAction);
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

    private void performRobotAction(String action) {
        if (TextUtils.isEmpty(action) || mRobotMotion == null) {
            mConnectionStatus.setText("Status: Response received");
            return;
        }

        String safeAction = action.toLowerCase().trim();

        try {
            if ("none".equals(safeAction)) {
                mConnectionStatus.setText("Status: Response received");
                return;
            }

            if ("nod_head".equals(safeAction)) {
                mRobotMotion.nodHead();
                mConnectionStatus.setText("Status: Action performed: nod head");
                return;
            }

            if ("shake_head".equals(safeAction)) {
                mRobotMotion.shakeHead();
                mConnectionStatus.setText("Status: Action performed: shake head");
                return;
            }

            if ("smile".equals(safeAction)) {
                mRobotMotion.emoji(RobotMotion.Emoji.SMILE);
                mConnectionStatus.setText("Status: Action performed: smile");
                return;
            }

            if ("sad".equals(safeAction)) {
                mRobotMotion.emoji(RobotMotion.Emoji.SAD);
                mConnectionStatus.setText("Status: Action performed: sad");
                return;
            }

            if ("cry".equals(safeAction)) {
                mRobotMotion.emoji(RobotMotion.Emoji.CRY);
                mConnectionStatus.setText("Status: Action performed: cry");
                return;
            }

            if ("shy".equals(safeAction)) {
                mRobotMotion.emoji(RobotMotion.Emoji.SHY);
                mConnectionStatus.setText("Status: Action performed: shy");
                return;
            }

            if ("angry".equals(safeAction)) {
                mRobotMotion.emoji(RobotMotion.Emoji.ANGRY);
                mConnectionStatus.setText("Status: Action performed: angry");
                return;
            }

            if ("blink".equals(safeAction)) {
                mRobotMotion.emoji(RobotMotion.Emoji.BLINK);
                mConnectionStatus.setText("Status: Action performed: blink");
                return;
            }

            if ("frown".equals(safeAction)) {
                mRobotMotion.emoji(RobotMotion.Emoji.FROWN);
                mConnectionStatus.setText("Status: Action performed: frown");
                return;
            }

            if ("default_face".equals(safeAction)) {
                mRobotMotion.emoji(RobotMotion.Emoji.DEFAULT);
                mConnectionStatus.setText("Status: Action performed: default face");
                return;
            }

            if ("reset_motors".equals(safeAction)) {
                resetAllMotors();
                mConnectionStatus.setText("Status: Action performed: reset motors");
                return;
            }

            if ("right_arm_small_wave".equals(safeAction)) {
                rightArmSmallWave();
                mConnectionStatus.setText("Status: Action performed: right arm small wave");
                return;
            }

            if ("left_arm_small_wave".equals(safeAction)) {
                leftArmSmallWave();
                mConnectionStatus.setText("Status: Action performed: left arm small wave");
                return;
            }

            if ("both_arms_small_wave".equals(safeAction)) {
                bothArmsSmallWave();
                mConnectionStatus.setText("Status: Action performed: both arms small wave");
                return;
            }
            mConnectionStatus.setText("Status: Ignored unsafe/unknown action: " + safeAction);

        } catch (Exception e) {
            Log.e(TAG, "Robot action failed", e);
            mConnectionStatus.setText("Status: Action failed: " + e.getMessage());
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
            mTtsStatus.setText("TTS Status: Speaking request sent");
            mSpeechManager.startSpeaking(text);
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











