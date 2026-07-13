# Motor control and telemetry

## Scope

The app supports one constrained static arm pose at a time through the `custom_arm_pose` action. It does not expose arbitrary motor IDs, wheel movement, base movement, or free-form motor sequences to Ollama.

## JSON schema

```json
{
  "action": "custom_arm_pose",
  "speech": "short spoken answer",
  "arm_pose": {
    "side": "right",
    "arm_rotation": 30,
    "arm_swing": 20,
    "forearm_rotation": -50,
    "forearm_swing": 0,
    "wrist": -40,
    "duration_ms": 3500,
    "hold_ms": 4500
  }
}
```

Required fields:

- `side`: `left` or `right`
- `arm_rotation`
- `arm_swing`
- `forearm_rotation`
- `forearm_swing`
- `wrist`

Optional fields with defaults:

- `duration_ms`: default `2500`
- `hold_ms`: default `4000`

## Neutral reference

Angle `0` is treated as the known human-like standby pose:

- arm points fully downward;
- elbow points toward the back;
- forearm is straight;
- fist is neutral;
- thumb points forward.

This reference is included in the Ollama prompt so generated poses are reasoned relative to a known physical posture.

## Validated limits

| Joint | Minimum | Maximum |
|---|---:|---:|
| Arm rotation | -25 | 175 |
| Arm swing | 0 | 65 |
| Forearm rotation | -80 | 80 |
| Forearm swing | 0 | 90 |
| Wrist | -80 | 80 |
| Movement duration | 1000 ms | 5000 ms |
| Hold duration | 1000 ms | 8000 ms |

`validateAndCleanArmPose()` and `isArmPoseValid()` reject missing, malformed, or out-of-range values before movement begins.

## Runtime flow

```text
Ollama reply
    -> strict JSON parse
    -> validate side and every arm field
    -> reject any out-of-range value
    -> command five motors on one selected arm
    -> hold the pose
    -> reset all motors after duration_ms + hold_ms
```

The current implementation creates one static pose. It is not a repeated wave or an animation sequence.

## Motor mapping

Right arm:

- `RobotDevices.Motors.ARM_ROTATION_RIGHT`
- `RobotDevices.Motors.ARM_SWING_RIGHT`
- `RobotDevices.Motors.FOREARM_ROTATION_RIGHT`
- `RobotDevices.Motors.FOREARM_SWING_RIGHT`
- `RobotDevices.Motors.WRIST_RIGHT`

Left arm:

- `RobotDevices.Motors.ARM_ROTATION_LEFT`
- `RobotDevices.Motors.ARM_SWING_LEFT`
- `RobotDevices.Motors.FOREARM_ROTATION_LEFT`
- `RobotDevices.Motors.FOREARM_SWING_LEFT`
- `RobotDevices.Motors.WRIST_LEFT`

Movement uses:

```java
mRobotMotion.startMotor(motorId, angle, durationMs, 1);
```

## Live telemetry

The app displays ten motor readings:

- five left-arm joints;
- five right-arm joints.

A foreground polling loop runs every 1000 ms and requests each motor through:

```java
mRobotMotion.getStatus(motorId, callback);
```

The callback provides:

- motor ID;
- angle;
- direction;
- speed.

The UI currently displays the reported angle. Direction and speed remain available in logs.

Polling lifecycle:

```text
onResume()
    -> startMotorStatusPolling()
    -> readAllArmMotorStatuses() once per second

onPause()
    -> stopMotorStatusPolling()
```

This avoids continuous polling while the app is not visible.

## Feedback limitation

The AvatarMind SDK exposes reported motor status, but the available documentation does not establish whether the angle is derived from:

- a true physical encoder;
- internal servo feedback;
- a potentiometer;
- controller state;
- the last commanded target.

Until confirmed experimentally or through hardware documentation, the UI should describe these values as SDK-reported motor positions rather than guaranteed encoder measurements.

## Safety constraints

The model cannot select:

- arbitrary motor IDs;
- out-of-range angles;
- motor speed;
- wheel movement;
- base movement;
- simultaneous custom poses for both arms;
- repeated motor sequences.

Motor reset remains centralized. Avoid manually forcing powered joints during testing.

## Planned work

- Record physical direction semantics for positive and negative values on every signed joint.
- Add an optional lively gesture or expression after responses when appropriate.
- Preserve `none` when movement would be distracting or unnecessary.
- Add safe multi-step gesture sequences after individual poses are fully characterized.
- Determine whether SDK status angles represent true measured feedback.