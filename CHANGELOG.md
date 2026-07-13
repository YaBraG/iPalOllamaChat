# Changelog

All notable project changes are documented here.

## Unreleased

### Planned

- Add an optional lively gesture or expression after most responses when appropriate.
- Preserve `none` for responses where movement is unnecessary.
- Calibrate positive and negative physical direction semantics for each arm joint.
- Inject `VisionEventBridge.getVisionContextForPrompt()` into Ollama prompts.
- Add stable named-user greeting behavior.
- Add local MDC knowledge retrieval.
- Improve user-visible vision status.
- Add safe multi-step arm gesture sequences.

## 2026-07-13

### Added

- Stable `VisionEventBridge` integration with AvatarMind RobotVision.
- Dynamic loading of `RobotVisionClient` from installed robot packages.
- App-private JNI library copy for `libRVF_Listener_JNI.so`.
- Face detection and registered-user recognition.
- Parsed recognition confidence, person ID, and face bounding box.
- `getVisionContextForPrompt()`, `getLastFaceState()`, and `getLastFaceEventRaw()`.
- Parsed and throttled face-state logging.
- Validated `custom_arm_pose` JSON action for one static left or right arm pose.
- Per-joint range validation for arm rotation, arm swing, forearm rotation, forearm swing, and wrist.
- Configurable arm movement and hold durations with centralized automatic reset.
- Neutral human-like standby pose knowledge in the Ollama prompt.
- Commanded arm-angle display in the response area.
- Continuous one-second polling of all ten arm motors while the app is visible.
- Dedicated left/right arm motor-position blocks in the UI.
- Compact control layout with server testing and prompt controls arranged side by side.
- Motor control documentation in `docs/MOTOR_CONTROL.md`.

### Changed

- Replaced fixed right, left, and both-arm wave actions with constrained model-selected static arm poses.
- Updated the prompt so angle `0` represents the known standby pose instead of an arbitrary midpoint.
- Updated the app layout to reserve space for live motor telemetry.
- Motor polling now starts in `onResume()` and stops in `onPause()`.

### Fixed

- Face events now recover after leaving and reopening the app.
- Face-event requests are reset on resume and disconnect.
- Duplicate face-event requests are avoided during one active connection.
- High-frequency raw app-side face-event logs are suppressed without dropping callbacks.
- The model no longer copies one fixed greeting pose for every arm request.

### Verified

Physically tested on the iPal robot:

- Local Ollama chat.
- Strict JSON reply handling and repair retry.
- iPal TTS.
- Head-touch cancellation and speech stop.
- Shoulder touch reactions.
- Head and face actions.
- Custom left and right arm poses.
- Joint limits and neutral standby behavior.
- Automatic arm reset after movement and hold time.
- Commanded-angle display.
- Continuous live motor-position display.
- Compact UI layout.
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