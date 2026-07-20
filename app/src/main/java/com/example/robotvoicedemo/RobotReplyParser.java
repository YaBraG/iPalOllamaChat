package com.example.robotvoicedemo;

import android.text.TextUtils;

import org.json.JSONObject;

public class RobotReplyParser {

    public interface ActionPolicy {
        boolean isAllowedAction(String action);
        boolean isCustomArmPoseAction(String action);
        String getDefaultSpeechForAction(String action);
    }

    private final ActionPolicy mActionPolicy;

    public RobotReplyParser(ActionPolicy actionPolicy) {
        mActionPolicy = actionPolicy;
    }

    public JSONObject parseStrict(String rawText) throws Exception {
        String jsonText = extractJsonObject(rawText);
        JSONObject robotReply = new JSONObject(jsonText);

        String action = robotReply.optString("action", "").trim().toLowerCase();
        String speech = normalizeEscapedNewlines(
                robotReply.optString("speech", "")
        ).trim();

        if (TextUtils.isEmpty(action)) {
            throw new Exception("Robot JSON missing action");
        }

        if (!mActionPolicy.isAllowedAction(action)) {
            throw new Exception("Robot JSON used unknown action: " + action);
        }

        if (TextUtils.isEmpty(speech)) {
            speech = mActionPolicy.getDefaultSpeechForAction(action);
        }

        JSONObject cleanReply = new JSONObject();
        cleanReply.put("action", action);
        cleanReply.put("speech", speech);

        if (mActionPolicy.isCustomArmPoseAction(action)) {
            JSONObject armPose = robotReply.optJSONObject("arm_pose");

            if (armPose == null) {
                throw new Exception("custom_arm_pose missing arm_pose object");
            }

            JSONObject cleanArmPose = validateAndCleanArmPose(armPose);
            cleanReply.put("arm_pose", cleanArmPose);
        }

        return cleanReply;
    }

    public JSONObject buildFallbackReply(String rawText) {
        JSONObject fallback = new JSONObject();

        try {
            fallback.put("action", "none");
            fallback.put("speech", cleanSpeech(rawText));
        } catch (Exception ignored) {
        }

        return fallback;
    }

    private JSONObject validateAndCleanArmPose(JSONObject armPose) throws Exception {
        String side = armPose.optString("side", "").trim().toLowerCase();

        if (!"right".equals(side) && !"left".equals(side)) {
            throw new Exception("arm_pose.side must be right or left");
        }

        requireArmPoseField(armPose, "arm_rotation");
        requireArmPoseField(armPose, "arm_swing");
        requireArmPoseField(armPose, "forearm_rotation");
        requireArmPoseField(armPose, "forearm_swing");
        requireArmPoseField(armPose, "wrist");

        int armRotation = armPose.getInt("arm_rotation");
        int armSwing = armPose.getInt("arm_swing");
        int forearmRotation = armPose.getInt("forearm_rotation");
        int forearmSwing = armPose.getInt("forearm_swing");
        int wrist = armPose.getInt("wrist");
        int durationMs = armPose.optInt(
                "duration_ms",
                ArmPoseController.DEFAULT_ARM_MOVE_DURATION_MS);
        int holdMs = armPose.optInt(
                "hold_ms",
                ArmPoseController.DEFAULT_ARM_HOLD_DURATION_MS);

        if (!ArmPoseController.isArmPoseValid(
                armRotation,
                armSwing,
                forearmRotation,
                forearmSwing,
                wrist,
                durationMs,
                holdMs)) {

            throw new Exception("arm_pose contains an out-of-range value");
        }

        JSONObject cleanArmPose = new JSONObject();
        cleanArmPose.put("side", side);
        cleanArmPose.put("arm_rotation", armRotation);
        cleanArmPose.put("arm_swing", armSwing);
        cleanArmPose.put("forearm_rotation", forearmRotation);
        cleanArmPose.put("forearm_swing", forearmSwing);
        cleanArmPose.put("wrist", wrist);
        cleanArmPose.put("duration_ms", durationMs);
        cleanArmPose.put("hold_ms", holdMs);

        return cleanArmPose;
    }

    private void requireArmPoseField(JSONObject armPose, String fieldName) throws Exception {
        if (!armPose.has(fieldName)) {
            throw new Exception("arm_pose missing " + fieldName);
        }
    }

    private String extractJsonObject(String rawText) throws Exception {
        if (TextUtils.isEmpty(rawText)) {
            throw new Exception("Empty Ollama response");
        }

        int startIndex = rawText.indexOf("{");
        int endIndex = rawText.lastIndexOf("}");

        if (startIndex < 0 || endIndex <= startIndex) {
            throw new Exception("No JSON object found");
        }

        return rawText.substring(startIndex, endIndex + 1);
    }

    private String normalizeEscapedNewlines(String text) {
        if (TextUtils.isEmpty(text)) {
            return text;
        }

        return text
                .replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\\r", "\n");
    }
    private String cleanSpeech(String rawText) {
        if (TextUtils.isEmpty(rawText)) {
            return "I got nothing. Somehow, that is still your fault.";
        }

        String cleaned = rawText.trim();

        cleaned = cleaned.replace("```json", "");
        cleaned = cleaned.replace("```", "");
        cleaned = cleaned.replace("*nods*", "");
        cleaned = cleaned.replace("*nod*", "");
        cleaned = cleaned.replace("*shakes head*", "");
        cleaned = cleaned.replace("*shake head*", "");
        cleaned = cleaned.replace("*smiles*", "");
        cleaned = cleaned.replace("*smile*", "");

        return cleaned.trim();
    }
}
