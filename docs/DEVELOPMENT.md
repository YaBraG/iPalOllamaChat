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
  -> App extracts action + speech
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

Builds the prompt, sends it to Ollama, receives the model response, parses JSON, and sends the result back to the UI thread.

### `parseRobotReply(String rawText)`

Attempts to extract a JSON object from the model output. If parsing fails, the app falls back to action `none` and cleans the raw text for speech.

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

1. Save server URL using `SharedPreferences`.
2. Add stronger JSON retry behavior when the model ignores JSON format.
3. Add more safe gestures from AvatarMind motion demos.
4. Inspect NUI / face recognition APIs.
5. Add local MDC knowledge documents.
6. Add speech input.
7. Add safe movement only after wheel APIs and obstacle behavior are fully understood.
