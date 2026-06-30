# Touch Behavior Notes

This document tracks the current tested touch behavior for the iPal Ollama Chat app.

## Source of touch events

Touch events are received through `RobotSystem.Listener` and handled in `MainActivity.java`.

Current event flow:

```text
RobotSystem.Listener.onMessage(from, what, arg1, arg2)
  -> handleRobotSystemEvent(from, what, arg1, arg2)
  -> if event is RF_EVENT_TOUCH:
       if area is head:
         handleHeadTouchStopOrCancel()
       else:
         reactToRobotTouch(what, arg1)
```

The app uses the SDK touch area constants from `RobotSystem.CallbackCommand`.

## Current touch mapping

| SDK area | Physical interpretation | Current app behavior |
|---|---|---|
| `RF_HEAD_TOUCH` | Head/top touch sensor | Stop/cancel control |
| `RF_LEFT_SHOULDER_TOUCH` | Left shoulder/arm area | Custom idle reaction only |
| `RF_RIGHT_SHOULDER_TOUCH` | Right shoulder/arm area | Custom idle reaction only |
| `RF_LEFT_OXTER_TOUCH` | Left side/underarm/tickle area | Display/log only |
| `RF_RIGHT_OXTER_TOUCH` | Right side/underarm/tickle area | Display/log only |

`Oxter` means underarm/armpit area. On the physical iPal, these are the side/tickle spots.

## Head touch behavior

Head touch is treated as a local stop/cancel button.

Current behavior:

| State | Head touch result |
|---|---|
| Waiting for Ollama | Cancels the pending request by incrementing the request token and setting `mWaitingForOllama = false` |
| Speaking | Stops the active TTS request with `mSpeechManager.stopSpeaking(mLastTtsRequestId)` |
| Listening | Attempts to stop listening with `mSpeechManager.stopListening()` if `isListening()` is true |
| Idle | Displays/logs head touch only |

The HTTP request to Ollama is not physically killed. Instead, the app invalidates the request token. If the old response arrives later, the UI ignores it.

Relevant fields:

```java
private int mLastTtsRequestId = -1;
private int mCurrentOllamaRequestToken = 0;
private boolean mWaitingForOllama = false;
```

Relevant methods:

```java
handleHeadTouchStopOrCancel()
cancelPendingOllamaRequest()
stopCurrentRobotSpeech()
stopCurrentRobotListening()
isCurrentOllamaRequest(int requestToken)
```

## Shoulder touch behavior

Shoulder touch reactions are allowed only when iPal is idle.

The app checks:

```java
shouldUseCustomTouchReaction(touchArea) && canRunCustomTouchReactionNow()
```

Current custom touch reaction areas:

```text
RF_LEFT_SHOULDER_TOUCH
RF_RIGHT_SHOULDER_TOUCH
```

`canRunCustomTouchReactionNow()` blocks custom touch reactions while:

```text
mWaitingForOllama == true
mSpeechManager.isSpeaking() == true
```

This prevents the app from intentionally speaking over itself or replacing the robot face while a model response is being spoken.

## Side/tickle behavior

Left and right side/tickle touch areas are treated as default-firmware territory.

The app displays/logs the touch, but it does not run custom speech or face actions for those areas.

Observed behavior: touching the side/tickle spots can still make iPal laugh or interrupt the current speech. That appears to come from iPal's default firmware behavior, not from this app.

## Known issue

Touching a shoulder/arm while iPal is speaking may stop the mouth animation while the audio continues.

Observed result:

```text
Speech audio continues.
Mouth animation stops or freezes.
Touch is still detected.
No dangerous movement occurs.
```

This is currently pinned as a known visual issue, not a safety blocker.

Likely cause: a firmware/default touch reaction or face/motion state change interferes with the TTS mouth animation layer.

## Current safety decision

Keep touch behavior conservative:

- Head touch is useful as stop/cancel.
- Shoulder touch reactions are allowed only while idle.
- Side/tickle touch areas are left to default iPal behavior.
- Touch events are not sent to Ollama yet.
- The model does not choose touch reactions.

## Test checklist

Before committing touch-related changes, test:

| Test | Expected result |
|---|---|
| Touch head while `Thinking...` is visible | Pending response is cancelled and late response is ignored |
| Touch head while iPal is speaking | Speech stops |
| Touch head while idle | App displays/logs head touch only |
| Touch left shoulder while idle | Custom shoulder reaction runs |
| Touch right shoulder while idle | Custom shoulder reaction runs |
| Touch shoulder while speaking | App logs/displays touch, but should not start custom speech |
| Touch side/tickle spot while idle | Default tickle/laugh behavior may run |
| Touch side/tickle spot while speaking | Default firmware behavior may interrupt speech |

## Future improvement ideas

Possible future improvements:

- Add a toggle to enable/disable touch reactions from the UI.
- Add a debug-only event log view for `from`, `what`, `arg1`, and `arg2`.
- Investigate whether the shoulder/arm touch mouth-animation issue can be prevented by avoiding any visual state update while TTS is active.
- Investigate whether default firmware touch reactions can be disabled or intercepted safely.
