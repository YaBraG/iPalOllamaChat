# Vision bridge implementation notes

## Purpose

`VisionEventBridge` connects the app to AvatarMind's installed RobotVision stack without opening the camera directly from `MainActivity`.

The camera remains owned by the system vision service. The app dynamically loads the client API from the robot's installed FaceTrack package and subscribes to face events.

## Runtime flow

```text
MainActivity.onCreate/onResume
    -> VisionEventBridge.start()/resume()
    -> locate FaceTrack.apk
    -> prepare private JNI library copy
    -> DexClassLoader loads RobotVisionClient
    -> create FaceEventListener proxy
    -> wait for onConnectionStatus(true)
    -> TurnEvent("face", true)
    -> parse face callbacks
```

On pause:

```text
MainActivity.onPause
    -> VisionEventBridge.pause()
    -> RobotVisionClient.onPause()
    -> onConnectionStatus(false)
    -> request flag reset
```

On resume:

```text
VisionEventBridge.resume()
    -> request flag reset
    -> RobotVisionClient.onResume()
    -> onConnectionStatus(true)
    -> TurnEvent("face", true)
```

This reset is necessary because the system service can turn face events off while the app is backgrounded.

## Dynamic loading

The bridge checks candidate packages in this order:

1. `com.avatarmind.robot.facetrack`
2. `com.avatar.wsclservice`
3. `com.avatarmind.childcare.vision`
4. `com.avatarmind.robot.faceagenda`
5. `com.avatarmind.robotvisionservice`

The tested robot resolves the client from:

```text
/system/app/FaceTrack.apk
```

`RobotVisionClient` is loaded with `DexClassLoader`.

## Native library handling

The dynamically loaded client expects:

```text
libRVF_Listener_JNI.so
```

The bridge copies the correct system library into app-private storage:

```text
/system/lib64/libRVF_Listener_JNI.so
```

or, for a 32-bit process:

```text
/system/lib/libRVF_Listener_JNI.so
```

The private directory is passed to `DexClassLoader` as the native library search path.

Do not remove this workaround unless the robot SDK packaging changes and the dynamically loaded client can resolve its JNI dependency another way.

## RobotVisionClient construction

The client is created with:

```java
withCamera = false
withEventlistener = true
```

The app therefore listens for vision events but does not claim direct camera ownership.

## Face event request policy

The bridge requests face events only after:

```text
onConnectionStatus(true)
```

It calls:

```java
TurnEvent("face", true)
```

The `mFaceEventsRequested` flag prevents duplicate requests during one active connection. The flag is reset:

- on resume;
- when connection status is not true;
- during destroy.

## Parsed state

`FaceState` stores:

- `detected`
- `name`
- `displayName`
- `gender`
- `age`
- `personId`
- `confidence`
- face bounding box: `x`, `y`, `width`, `height`
- raw event text

Recognition is considered reliable for prompt context when:

```text
name is not empty
name is not "unknown"
confidence >= 80
```

## Prompt context

`getVisionContextForPrompt()` returns one of these forms:

```text
Vision: no face detected.
```

```text
Vision: face detected. Person is unknown. Face box: x=..., y=..., width=..., height=....
```

```text
Vision: face detected. Recognized person: Erick. Confidence: 93. Face box: x=..., y=..., width=..., height=....
```

The method exists but is not yet included in the Ollama prompt.

## Logging policy

Raw face events arrive many times per second. The bridge processes every event but suppresses raw `face;...` log lines.

Parsed summaries are logged:

- when detection changes;
- when the parsed name changes;
- approximately once per second while state remains active.

System-service logs such as `FaceTrackManager` debug output are outside the app process and cannot be suppressed here.

## Verified behavior

Physically tested on the iPal robot:

- first launch connects successfully;
- face events are requested once;
- face detection works;
- registered user `Erick` is recognized;
- confidence and person ID are parsed;
- leaving the app disables the listener;
- reopening the app re-requests face events;
- no JNI load crash occurs;
- no raw app-side face-event spam remains;
- normal chat, TTS, gestures, and touch controls still work.

## Useful log filter

```powershell
adb -s AECHBPBDL22110016 logcat -d -v time |
    findstr /i "iPalVisionBridge Resume detected onConnectionStatus Requesting face events once TurnEvent Parsed face FATAL JNI UnsatisfiedLinkError"
```

## Do not change casually

These pieces are required by the tested robot environment:

- candidate-package search;
- `DexClassLoader` loading;
- private JNI copy;
- `withCamera=false`;
- waiting for `onConnectionStatus(true)`;
- resetting `mFaceEventsRequested` across pause/resume;
- raw face-log suppression while still processing callbacks.
