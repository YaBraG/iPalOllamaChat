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
  -> Ollama /api/generate
  -> Local model returns JSON
  -> App strict-parses action + speech
  -> If parse fails, app asks Ollama to repair the reply into JSON
  -> App validates action whitelist
  -> RobotMotion performs action
  -> SpeechManager speaks speech
```

The robot action and spoken text are separated intentionally. The model decides what action fits the answer, but the Android app decides whether that action is safe and allowed.

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
```

The app should never blindly execute arbitrary model output. Any new action must be added to both:

1. The prompt's allowed action list.
2. The Java whitelist in `performRobotAction(String action)`.
3. The allowed-action validator used by strict JSON parsing.

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
- `sendPromptToOllama(String prompt)` sends a prompt to `/api/generate` and returns the raw model response string.
- `parseRobotReplyStrict(String rawText)` extracts JSON, validates `action`, validates `speech`, and returns cleaned JSON.
- `buildJsonRepairPrompt(String badResponse)` asks the model to convert a broken response into the required JSON format.
- `buildFallbackRobotReply(String rawText)` uses `action: none` and cleaned text if repair fails.
- `isAllowedRobotAction(String action)` is the action whitelist used during parsing.

## Server URL persistence

The app now saves the last cleaned Ollama server URL using Android `SharedPreferences`.

Relevant constants:

```java
private static final String PREFS_NAME = "iPalOllamaChatPrefs";
private static final String PREF_SERVER_URL = "server_url";
```

Relevant behavior:

- On launch, `initView()` loads the saved URL if one exists.
- `getCleanServerUrl()` normalizes the URL and saves it through `saveServerUrl(String serverUrl)`.
- The default URL is still used if no saved URL exists.

## Personality prompt

Current behavior:

- Helpful and clear for MDC, robotics, engineering, school, and lab-rule questions.
- Extremely sarcastic, mean, savage, and brutally sassy for casual/random questions.
- Uses theatrical roast-style humor.
- Hard limits remain: no slurs, protected-class insults, threats, sexual harassment, self-harm encouragement, or real-world violence.

## Important app methods

### `askIpal()`

Reads the user prompt from the UI, updates status labels, and calls `askOllama(userText)`.

### `askOllama(String userText)`

Coordinates the full request flow: normal prompt, strict JSON parse, optional repair prompt, fallback, UI update, robot action, and speech.

### `buildRobotPrompt(String userText)`

Builds the main robot prompt, including personality, allowed actions, JSON format requirements, and the user prompt.

### `sendPromptToOllama(String prompt)`

Sends a prompt to the Ollama `/api/generate` endpoint and returns the model's raw `response` string.

### `parseRobotReplyStrict(String rawText)`

Extracts a JSON object and validates that both `action` and `speech` are present. It also rejects actions not included in the whitelist.

### `buildJsonRepairPrompt(String badResponse)`

Builds the second-chance prompt used when the first model response is not valid robot JSON.

### `buildFallbackRobotReply(String rawText)`

Creates a safe fallback with `action: none` and a cleaned speech string when JSON repair fails.

### `performRobotAction(String action)`

Validates the requested action and calls the matching safe `RobotMotion` API.

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

1. Add more safe gestures from AvatarMind motion demos.
2. Inspect NUI / face recognition APIs.
3. Add local MDC knowledge documents.
4. Add speech input.
5. Add safe movement only after wheel APIs and obstacle behavior are fully understood.
