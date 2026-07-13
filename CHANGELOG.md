# Changelog

All notable project changes are documented here.

## Unreleased

### Planned

- Inject `VisionEventBridge.getVisionContextForPrompt()` into Ollama prompts.
- Add stable named-user greeting behavior.
- Add local MDC knowledge retrieval.
- Improve user-visible vision status.

## 2026-07-13

### Added

- Stable `VisionEventBridge` integration with AvatarMind RobotVision.
- Dynamic loading of `RobotVisionClient` from installed robot packages.
- App-private JNI library copy for `libRVF_Listener_JNI.so`.
- Face detection and registered-user recognition.
- Parsed recognition confidence, person ID, and face bounding box.
- `getVisionContextForPrompt()`, `getLastFaceState()`, and `getLastFaceEventRaw()`.
- Parsed and throttled face-state logging.

### Fixed

- Face events now recover after leaving and reopening the app.
- Face-event requests are reset on resume and disconnect.
- Duplicate face-event requests are avoided during one active connection.
- High-frequency raw app-side face-event logs are suppressed without dropping callbacks.

### Verified

- Local Ollama chat.
- Strict JSON reply handling and repair retry.
- iPal TTS.
- Head-touch cancellation and speech stop.
- Shoulder touch reactions.
- Head and face actions.
- Arm gesture presets and automatic reset.
- Face detection and recognition before and after app resume.
- No JNI or RobotVision bridge crash during physical testing.

## Earlier prototype work

### Added

- Local Ollama `/api/generate` integration using `llama3.2:3b`.
- Saved server URL through Android `SharedPreferences`.
- Strict `action` and `speech` response schema.
- Action whitelist and safe fallback handling.
- JSON repair retry.
- Thinking expression while waiting for a model response.
- Safe RobotMotion emoji actions.
- Fixed arm-wave presets with centralized delayed motor reset.
- Touch handling through `RobotSystem.Listener`.
