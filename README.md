# iPal Ollama Chat

Android app for an AvatarMind iPal robot that connects to a local Ollama server, sends user prompts to a local language model, receives a structured robot reply, performs a safe robot action, and speaks the response through the iPal text-to-speech system.

The current version is a working lab prototype for an iPal robot running the AvatarMind Android SDK.

## Current behavior

The app can:

- Connect to a local Ollama server over Wi-Fi.
- Send prompts to the `llama3.2:3b` model.
- Force the model to return a JSON reply with an `action` and `speech` field.
- Validate the requested action against a fixed whitelist.
- Trigger safe iPal gestures and facial expressions through `RobotMotion`.
- Speak only the clean `speech` text through the iPal TTS system.
- Display the selected action and spoken answer in the app UI.

Example model response expected by the app:

```json
{
  "action": "shake_head",
  "speech": "Absolutely not. Even my circuits have standards."
}
```

The app performs the action physically, then speaks only the `speech` value.

## Supported robot actions

Current safe action whitelist:

| Action | Robot behavior |
|---|---|
| `none` | No movement |
| `nod_head` | Nod head |
| `shake_head` | Shake head |
| `smile` | Smile face |
| `sad` | Sad face |
| `cry` | Cry face |
| `shy` | Shy face |
| `angry` | Angry face |
| `blink` | Blink face |
| `frown` | Frown face |
| `default_face` | Reset/default face |

The model may choose an action, but the app only executes actions in this whitelist.

## Project setup

This project was built for the AvatarMind iPal SDK environment, not a normal Android emulator setup.

Expected development environment:

- Windows development machine.
- Android Studio 3.5.x.
- JDK 8.
- AvatarMind modified Android SDK / Android API 25 platform.
- Gradle wrapper included in the repository.
- iPal robot connected by USB for install/debug.
- Ollama running on the same local network as the robot.

Current Android build configuration:

- Application ID: `com.yabrag.ipalollamachat`
- `compileSdkVersion`: `25`
- `targetSdkVersion`: `25`
- `minSdkVersion`: `19`
- Build tools: `28.0.3`
- Android Gradle Plugin: `3.5.2`
- Gradle wrapper: `5.4.1`

## Ollama setup

The app expects an Ollama server reachable from the robot over the local network.

Current default server URL in the app:

```text
http://192.168.2.36:11434
```

Current model:

```text
llama3.2:3b
```

Start Ollama in network mode from PowerShell:

```powershell
Stop-Process -Name "ollama" -Force -ErrorAction SilentlyContinue
$env:OLLAMA_HOST = "0.0.0.0:11434"
Start-Process "ollama" -ArgumentList "serve"
Start-Sleep -Seconds 2
netstat -ano | findstr ":11434"
```

Test from the Windows machine:

```powershell
Invoke-RestMethod "http://192.168.2.36:11434"
```

Expected result:

```text
Ollama is running
```

## Build and install

From PowerShell:

```powershell
cd "C:\iPalDev\Projects\iPalOllamaChat"

$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

.\gradlew.bat clean assembleDebug

adb -s AECHBPBDL22110016 install -r ".\app\build\outputs\apk\debug\app-debug.apk"
```

## Useful test prompts

```text
Say no in a dramatic way.
```

```text
Smile and introduce yourself.
```

```text
What is Miami Dade College?
```

```text
Roast me.
```

## Repository notes

Do not commit local SDKs, Android Studio installations, APKs, signing keys, build outputs, or local backup folders.

Ignored examples:

- `.gradle/`
- `build/`
- `app/build/`
- `local.properties`
- `*.apk`
- `*.jks`
- `*.keystore`
- `_backups/`
- Android Studio folders
- AvatarMind SDK folders

## Known limitations

- Server URL is currently hardcoded as a default value in `MainActivity.java`.
- The app does not yet save the server URL between launches.
- The app uses one-shot prompt/response calls, not a persistent conversation memory.
- JSON parsing has a fallback, but small local models may occasionally return malformed JSON.
- Only safe head and face actions are enabled.
- Wheel/base movement is intentionally not enabled yet.
- Face recognition/NUI support is not implemented yet.
- MDC-specific knowledge is currently prompt-based only; no local MDC document database is connected yet.

## Planned features

Near-term:

- Add a saved server URL preference.
- Add a README-backed development checklist.
- Improve JSON reliability with stronger parsing and retry behavior.
- Add more safe body actions after inspecting AvatarMind motion APIs.

Later:

- Add speech input.
- Add NUI / face recognition support.
- Add named-user greetings.
- Add MDC knowledge documents or retrieval.
- Add safe wheel movement only after collision/safety behavior is understood.

## Status

Working prototype. Current milestone: local Ollama chat, JSON action routing, RobotMotion gestures, iPal TTS, and sassy lab-assistant personality.
