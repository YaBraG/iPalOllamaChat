# Development Notes

This document captures the current working setup for the iPal Ollama Chat app.

## Project identity

- Repository: `YaBraG/iPalOllamaChat`
- App name: `iPal Ollama Chat`
- Android application ID: `com.yabrag.ipalollamachat`
- Java package: `com.example.robotvoicedemo`
- Main activity file: `app/src/main/java/com/example/robotvoicedemo/MainActivity.java`

## Current architecture

```text
User prompt
  -> Android app
  -> App captures/normalizes server URL on the UI thread
  -> Background thread sends prompt to Ollama /api/generate
  -> Local model returns JSON
  -> App strict-parses action + speech
  -> If parse fails, app asks Ollama to repair the reply into JSON
  -> App validates action whitelist
  -> App fills safe default speech if action is valid but speech is empty
  -> UI thread handles the cleaned robot reply
  -> performRobotAction(action) returns whether an action actually ran
  -> If action was an arm gesture, app schedules delayed motor reset
  -> SpeechManager speaks speech
```

The robot action and spoken text are separated intentionally. The model decides what action fits the answer, but the Android app decides whether that action is safe and allowed.

## Action constants

The app now centralizes robot action names as constants:

```java
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
```

The `ALLOWED_ACTIONS_TEXT` string is built from those constants and reused by both the normal prompt and JSON repair prompt.

## JSON action protocol

The model is instructed to return valid JSON only:

```json
{
  "action": "one_allowed_action",
  "speech": "short spoken answer"
}
```

Allowed actions:

```text
none
nod_head
shake_head
smile
sad
cry
shy
angry
blink
frown
default_face
reset_motors
right_arm_small_wave
left_arm_small_wave
both_arms_small_wave
```

The app should never blindly execute arbitrary model output. Any new action must be added to:

1. The action constants.
2. `ALLOWED_ACTIONS_TEXT` if it is safe for the model to choose.
3. `isAllowedRobotAction(String action)`.
4. `performRobotAction(String action)`.
5. `shouldAutoResetAfterAction(String action)` if the action should return to neutral automatically.
6. `getDefaultSpeechForAction(String action)` when useful.

## JSON repair behavior

The current flow is:

```text
send normal robot prompt
try parseRobotReplyStrict(raw response)
if parsing fails:
  send buildJsonRepairPrompt(bad response)
  try parseRobotReplyStrict(repaired response)
if repair fails:
  buildFallbackRobotReply(raw response)
```

This is designed to handle cases where the local model accidentally returns markdown, stage directions, or plain text instead of the required JSON object.

Important methods:

- `buildRobotPrompt(String userText)` creates the normal robot/personality prompt.
- `sendPromptToOllama(String prompt, String requestServerUrl)` sends a prompt to `/api/generate` and returns the raw model response string.
- `parseRobotReplyStrict(String rawText)` extracts JSON, validates `action`, and validates/fills `speech`.
- `buildJsonRepairPrompt(String badResponse)` asks the model to convert a broken response into the required JSON format.
- `buildFallbackRobotReply(String rawText)` uses `action: none` and cleaned text if repair fails.
- `isAllowedRobotAction(String action)` is the action whitelist used during parsing.
- `getDefaultSpeechForAction(String action)` fills safe spoken text when the model returns a valid action with empty speech.

## Server URL persistence

The app saves the last cleaned Ollama server URL using Android `SharedPreferences`.

Relevant constants:

```java
private static final String PREFS_NAME = "iPalOllamaChatPrefs";
private static final String PREF_SERVER_URL = "server_url";
```

Relevant behavior:

- On launch, `initView()` loads the saved URL if one exists.
- `getCleanServerUrl()` normalizes the URL and saves it through `saveServerUrl(String serverUrl)`.
- `askIpal()` captures the cleaned URL on the UI thread before starting the background Ollama request.
- `sendPromptToOllama(String prompt, String requestServerUrl)` uses the captured string and does not touch UI widgets from the background thread.
- The default URL is still used if no saved URL exists.

## Safe motor presets

The app includes four fixed motor preset actions.

```text
reset_motors
right_arm_small_wave
left_arm_small_wave
both_arms_small_wave
```

These are intentionally high-level actions. The model does not choose motor IDs, raw angles, speeds, durations, or wheel movement.

Relevant import:

```java
import android.robot.hw.RobotDevices;
```

Current reset preset behavior:

```java
mRobotMotion.reset((int) RobotDevices.Units.ALL_MOTORS);
```

Current right-arm preset behavior:

```java
mRobotMotion.startMotor((int) RobotDevices.Motors.ARM_SWING_RIGHT, 15, 1000, 1);
mRobotMotion.startMotor((int) RobotDevices.Motors.FOREARM_SWING_RIGHT, 20, 1000, 1);
mRobotMotion.startMotor((int) RobotDevices.Motors.WRIST_RIGHT, 15, 1000, 1);
```

Current left-arm preset behavior:

```java
mRobotMotion.startMotor((int) RobotDevices.Motors.ARM_SWING_LEFT, 15, 1000, 1);
mRobotMotion.startMotor((int) RobotDevices.Motors.FOREARM_SWING_LEFT, 20, 1000, 1);
mRobotMotion.startMotor((int) RobotDevices.Motors.WRIST_LEFT, 15, 1000, 1);
```

Current both-arms preset behavior:

```java
rightArmSmallWave();
leftArmSmallWave();
```

Do not expose arbitrary motor angles to the model until the physical pose safety, range behavior, and reset behavior are better understood.

## Centralized motor auto-reset

Arm gestures return to neutral automatically through centralized logic.

Current behavior:

```text
handleRobotReplyOnUi(action, speech, responseWasRepaired)
  -> performRobotAction(action)
  -> if action ran and shouldAutoResetAfterAction(action)
       scheduleMotorResetAfterGesture()
```

`performRobotAction(String action)` now returns `true` only when a known action actually ran. This prevents auto-reset from being scheduled for unknown or ignored actions.

`shouldAutoResetAfterAction(String action)` currently returns `true` for:

```text
right_arm_small_wave
left_arm_small_wave
both_arms_small_wave
```

`reset_motors` cancels any pending motor reset before resetting immediately.

`mMainHandler` and `mMotorResetRunnable` are created once in `initData()`. Pending reset callbacks are removed before scheduling a new reset and again in `onDestroy()`.

## Personality prompt

Current behavior:

- Helpful and clear for MDC, robotics, engineering, school, and lab-rule questions.
- Extremely sarcastic, mean, savage, and brutally sassy for casual/random questions.
- Uses theatrical roast-style humor.
- Hard limits remain: no slurs, protected-class insults, threats, sexual harassment, self-harm encouragement, or real-world violence.

## Important app methods

### `askIpal()`

Reads the user prompt from the UI, updates status labels, captures the cleaned server URL on the UI thread, and calls `askOllama(userText, requestServerUrl)`.

### `askOllama(String userText, String requestServerUrl)`

Coordinates the request flow: normal prompt, strict JSON parse, optional repair prompt, fallback, and UI handoff.

### `handleRobotReplyOnUi(String action, String speech, boolean responseWasRepaired)`

Runs the safe robot action, schedules auto-reset if needed, updates the response UI, and speaks the cleaned response text.

### `buildRobotPrompt(String userText)`

Builds the main robot prompt, including personality, allowed actions, JSON format requirements, and the user prompt.

### `sendPromptToOllama(String prompt, String requestServerUrl)`

Sends a prompt to the Ollama `/api/generate` endpoint and returns the model's raw `response` string. It receives the already-cleaned server URL as a string to avoid touching the UI from a background thread.

### `parseRobotReplyStrict(String rawText)`

Extracts a JSON object and validates that `action` is present and whitelisted. If `speech` is empty but the action is valid, the app fills safe default speech with `getDefaultSpeechForAction(String action)`.

### `getDefaultSpeechForAction(String action)`

Returns short safe speech such as `Done.`, `Hello.`, `Yes.`, or `No.` when the model returns a valid action but empty speech.

### `buildJsonRepairPrompt(String badResponse)`

Builds the second-chance prompt used when the first model response is not valid robot JSON.

### `buildFallbackRobotReply(String rawText)`

Creates a safe fallback with `action: none` and a cleaned speech string when JSON repair fails.

### `performRobotAction(String action)`

Validates the requested action, calls the matching safe `RobotMotion` API, and returns whether an action actually ran.

### `shouldAutoResetAfterAction(String action)`

Returns whether the completed action should schedule a delayed motor reset.

### `scheduleMotorResetAfterGesture()`

Cancels any previous pending motor reset and schedules `resetAllMotors()` after `MOTOR_RESET_DELAY_MS`.

### `resetAllMotors()`

Runs the fixed all-motors reset preset.

### `rightArmSmallWave()`

Runs the fixed right-arm wave preset using small internal motor angles.

### `leftArmSmallWave()`

Runs the fixed left-arm wave preset using small internal motor angles.

### `bothArmsSmallWave()`

Runs the fixed both-arms wave preset by calling the tested right-arm and left-arm wave presets together.

### `speakText(String text)`

Enables robot TTS and calls `mSpeechManager.startSpeaking(text)`.

### `enableRobotTts()`

Uses reflection to call `setTtsEnable(true)` or `setTtsEnabled(true)` depending on which method exists on the robot SDK.

## Build commands

```powershell
cd "C:\iPalDev\Projects\iPalOllamaChat"

$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

.\gradlew.bat clean assembleDebug
```

## Install command

```powershell
adb -s AECHBPBDL22110016 install -r ".\app\build\outputs\apk\debug\app-debug.apk"
```

## Ollama network mode

Ollama must listen on the LAN, not only localhost.

```powershell
Stop-Process -Name "ollama" -Force -ErrorAction SilentlyContinue
$env:OLLAMA_HOST = "0.0.0.0:11434"
Start-Process "ollama" -ArgumentList "serve"
Start-Sleep -Seconds 2
netstat -ano | findstr ":11434"
```

Expected test result:

```powershell
Invoke-RestMethod "http://192.168.2.36:11434"
```

```text
Ollama is running
```

## Safe development workflow

Before major changes:

```powershell
cd "C:\iPalDev\Projects\iPalOllamaChat"

Copy-Item `
  ".\app\src\main\java\com\example\robotvoicedemo\MainActivity.java" `
  ".\_backups\MainActivity.before-change.java" `
  -Force
```

After a working change:

```powershell
git status
git add .
git commit -m "Describe the working change"
git pull --rebase origin master
git push
```

## Do not commit

Do not commit:

- Android Studio installation folders.
- AvatarMind SDK folders.
- Android SDK folders.
- APK files.
- Signing keys.
- Build output folders.
- Local backup folders.
- `local.properties`.

## Next development targets

Recommended order:

1. Add more fixed safe motor presets from AvatarMind motion demos.
2. Inspect NUI / face recognition APIs.
3. Add local MDC knowledge documents.
4. Add speech input.
5. Add safe movement only after wheel APIs and obstacle behavior are fully understood.
