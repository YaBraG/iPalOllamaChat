package com.example.robotvoicedemo;

import android.os.Handler;
import android.os.Looper;
import android.robot.hw.RobotDevices;
import android.robot.motion.RobotMotion;
import android.util.Log;

import org.json.JSONObject;

public class ArmPoseController {

    private static final String TAG = "iPalOllamaChat";

    public static final int DEFAULT_ARM_MOVE_DURATION_MS = 2500;
    public static final int DEFAULT_ARM_HOLD_DURATION_MS = 4000;

    public interface Callback {
        void onArmPoseStatusChanged(String statusText);
    }

    private final RobotMotion mRobotMotion;
    private Callback mCallback;
    private Handler mMainHandler;
    private Runnable mMotorResetRunnable;

    public ArmPoseController(
            RobotMotion robotMotion,
            Callback callback) {

        mRobotMotion = robotMotion;
        mCallback = callback;
        mMainHandler = new Handler(Looper.getMainLooper());
        mMotorResetRunnable = new Runnable() {
            @Override
            public void run() {
                resetAllMotors();
                reportStatus("Status: Motors auto-reset after gesture");
            }
        };
    }

    public boolean performCustomArmPose(JSONObject armPose) {
        if (mRobotMotion == null || armPose == null) {
            return false;
        }

        String side = armPose.optString("side", "").trim().toLowerCase();
        int armRotation = armPose.optInt("arm_rotation");
        int armSwing = armPose.optInt("arm_swing");
        int forearmRotation = armPose.optInt("forearm_rotation");
        int forearmSwing = armPose.optInt("forearm_swing");
        int wrist = armPose.optInt("wrist");
        int durationMs = armPose.optInt("duration_ms", DEFAULT_ARM_MOVE_DURATION_MS);
        int holdMs = armPose.optInt("hold_ms", DEFAULT_ARM_HOLD_DURATION_MS);

        if (!isArmPoseValid(
                armRotation,
                armSwing,
                forearmRotation,
                forearmSwing,
                wrist,
                durationMs,
                holdMs)) {

            Log.w(TAG, "Rejected unsafe custom arm pose.");
            reportStatus("Status: Rejected unsafe custom arm pose");
            return false;
        }

        cancelScheduledReset();

        if ("right".equals(side)) {
            startRightArmPose(
                    armRotation,
                    armSwing,
                    forearmRotation,
                    forearmSwing,
                    wrist,
                    durationMs
            );
        } else if ("left".equals(side)) {
            startLeftArmPose(
                    armRotation,
                    armSwing,
                    forearmRotation,
                    forearmSwing,
                    wrist,
                    durationMs
            );
        } else {
            reportStatus("Status: Invalid arm side");
            return false;
        }

        int resetDelayMs = durationMs + holdMs;
        scheduleMotorResetAfterGesture(resetDelayMs);

        Log.i(TAG,
                "Custom arm pose started: side=" + side
                        + ", armRotation=" + armRotation
                        + ", armSwing=" + armSwing
                        + ", forearmRotation=" + forearmRotation
                        + ", forearmSwing=" + forearmSwing
                        + ", wrist=" + wrist
                        + ", durationMs=" + durationMs
                        + ", holdMs=" + holdMs
                        + ", resetDelayMs=" + resetDelayMs);

        reportStatus(
                "Status: Custom " + side + " arm pose; reset in " + resetDelayMs + " ms"
        );

        return true;
    }

    public void cancelScheduledReset() {
        if (mMainHandler != null && mMotorResetRunnable != null) {
            mMainHandler.removeCallbacks(mMotorResetRunnable);
        }
    }

    public void resetAllMotors() {
        if (mRobotMotion == null) {
            return;
        }

        mRobotMotion.reset((int) RobotDevices.Units.ALL_MOTORS);
    }

    public void destroy() {
        cancelScheduledReset();
        mMotorResetRunnable = null;
        mMainHandler = null;
        mCallback = null;
    }

    public static boolean isArmPoseValid(
            int armRotation,
            int armSwing,
            int forearmRotation,
            int forearmSwing,
            int wrist,
            int durationMs,
            int holdMs) {

        return armRotation >= -25
                && armRotation <= 175
                && armSwing >= 0
                && armSwing <= 65
                && forearmRotation >= -80
                && forearmRotation <= 80
                && forearmSwing >= 0
                && forearmSwing <= 90
                && wrist >= -80
                && wrist <= 80
                && durationMs >= 1000
                && durationMs <= 5000
                && holdMs >= 1000
                && holdMs <= 8000;
    }

    private void scheduleMotorResetAfterGesture(int delayMs) {
        if (mMainHandler == null || mMotorResetRunnable == null) {
            return;
        }

        mMainHandler.removeCallbacks(mMotorResetRunnable);
        mMainHandler.postDelayed(mMotorResetRunnable, delayMs);
    }

    private void startRightArmPose(
            int armRotation,
            int armSwing,
            int forearmRotation,
            int forearmSwing,
            int wrist,
            int durationMs) {

        mRobotMotion.startMotor(
                (int) RobotDevices.Motors.ARM_ROTATION_RIGHT,
                armRotation,
                durationMs,
                1
        );
        mRobotMotion.startMotor(
                (int) RobotDevices.Motors.ARM_SWING_RIGHT,
                armSwing,
                durationMs,
                1
        );
        mRobotMotion.startMotor(
                (int) RobotDevices.Motors.FOREARM_ROTATION_RIGHT,
                forearmRotation,
                durationMs,
                1
        );
        mRobotMotion.startMotor(
                (int) RobotDevices.Motors.FOREARM_SWING_RIGHT,
                forearmSwing,
                durationMs,
                1
        );
        mRobotMotion.startMotor(
                (int) RobotDevices.Motors.WRIST_RIGHT,
                wrist,
                durationMs,
                1
        );
    }

    private void startLeftArmPose(
            int armRotation,
            int armSwing,
            int forearmRotation,
            int forearmSwing,
            int wrist,
            int durationMs) {

        mRobotMotion.startMotor(
                (int) RobotDevices.Motors.ARM_ROTATION_LEFT,
                armRotation,
                durationMs,
                1
        );
        mRobotMotion.startMotor(
                (int) RobotDevices.Motors.ARM_SWING_LEFT,
                armSwing,
                durationMs,
                1
        );
        mRobotMotion.startMotor(
                (int) RobotDevices.Motors.FOREARM_ROTATION_LEFT,
                forearmRotation,
                durationMs,
                1
        );
        mRobotMotion.startMotor(
                (int) RobotDevices.Motors.FOREARM_SWING_LEFT,
                forearmSwing,
                durationMs,
                1
        );
        mRobotMotion.startMotor(
                (int) RobotDevices.Motors.WRIST_LEFT,
                wrist,
                durationMs,
                1
        );
    }

    private void reportStatus(String statusText) {
        if (mCallback != null) {
            mCallback.onArmPoseStatusChanged(statusText);
        }
    }
}
