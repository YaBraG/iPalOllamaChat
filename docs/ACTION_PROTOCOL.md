# Robot Action Protocol

The app uses a structured JSON protocol so the local language model can choose a robot action without mixing movement instructions into the spoken answer.

## Why JSON

The first working motion version used Java keyword detection, such as checking if the user prompt contained words like `nod`, `shake head`, or `smile`.

That worked, but it does not scale well.

The current version lets the model decide the action, then the app validates that action before executing it.

This keeps the system flexible while avoiding unsafe arbitrary execution.

## Required model output

The model must return one JSON object only:

```json
{
  "action": "one_allowed_action",
  "speech": "short spoken answer"
}
```

No markdown, no code block, and no stage directions.

Bad:

```text
*shakes head* Absolutely not.
```

Good:

```json
{
  "action": "shake_head",
  "speech": "Absolutely not. Even my circuits have standards."
}
```

## Allowed actions

| Action | Meaning |
|---|---|
| `none` | No robot action |
| `nod_head` | Agreement, yes, understanding, approval |
| `shake_head` | No, disagreement, refusal, dramatic rejection |
| `smile` | Greeting, happy, friendly, joking |
| `sad` | Sad expression |
| `cry` | Crying expression |
| `shy` | Shy expression |
| `angry` | Playful fake anger |
| `blink` | Blink expression |
| `frown` | Frown expression |
| `default_face` | Reset/default face |

## Execution rule

The model suggests the action.

The app decides whether to execute it.

If the model returns an action outside the whitelist, the app ignores it.

Example unsafe or unknown action:

```json
{
  "action": "drive_forward",
  "speech": "Trust me, this will be fine."
}
```

Current result:

```text
Action ignored because drive_forward is not whitelisted.
```

## Adding a new action

To add a new action safely:

1. Confirm the AvatarMind robot API call exists.
2. Test the action in a small isolated demo first.
3. Add the action name to the prompt's allowed action list.
4. Add a matching case in `performRobotAction(String action)`.
5. Build and install on the robot.
6. Test with simple prompts.
7. Commit only after the robot action works reliably.

## Current safety policy

Safe now:

- Head nodding.
- Head shaking.
- Face/emoji expressions.

Not enabled yet:

- Wheel movement.
- Base movement.
- Navigation.
- Arm/body motor movement.
- Physical interactions with people or objects.

Movement actions should stay disabled until wheel APIs, stop behavior, and obstacle behavior are understood.

## Example prompts

```text
Say no in a dramatic way.
```

Expected:

```json
{
  "action": "shake_head",
  "speech": "Absolutely not. Even my circuits have standards."
}
```

```text
Smile and introduce yourself.
```

Expected:

```json
{
  "action": "smile",
  "speech": "I'm iPal, the tiny lab gremlin supervising this circus."
}
```

```text
What is Miami Dade College?
```

Expected:

```json
{
  "action": "none",
  "speech": "Miami Dade College is a public college in Miami, Florida, with many academic and technical programs."
}
```
