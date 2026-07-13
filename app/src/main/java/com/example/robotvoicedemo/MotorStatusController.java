package com.example.robotvoicedemo;

import android.os.Handler;
import android.os.Looper;
import android.robot.hw.RobotDevices;
import android.robot.motion.RobotMotion;
import android.util.Log;

public class MotorStatusController {

    private static final String TAG = "iPalOllamaChat";
    private static final int MOTOR_STATUS_POLL_INTERVAL_MS = 1000;

    public interface Callback {
        void onMotorStatusUpdated(int motorId, int angle, int direction, int speed);
    }

    private final RobotMotion mRobotMotion;
    private Handler mHandler;
    private Callback mCallback;
    private boolean mStarted;

    private final Runnable mMotorStatusPollingRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mStarted) {
                return;
            }

            readAllArmMotorStatuses();

            if (mHandler != null && mStarted) {
                mHandler.postDelayed(this, MOTOR_STATUS_POLL_INTERVAL_MS);
            }
        }
    };

    public MotorStatusController(RobotMotion robotMotion, Callback callback) {
        mRobotMotion = robotMotion;
        mCallback = callback;
        mHandler = new Handler(Looper.getMainLooper());
    }

    public void start() {
        if (mRobotMotion == null
                || mHandler == null
                || mCallback == null
                || mMotorStatusPollingRunnable == null) {
            return;
        }

        mStarted = true;
        mHandler.removeCallbacks(mMotorStatusPollingRunnable);
        mHandler.post(mMotorStatusPollingRunnable);
    }

    public void stop() {
        mStarted = false;

        if (mHandler != null && mMotorStatusPollingRunnable != null) {
            mHandler.removeCallbacks(mMotorStatusPollingRunnable);
        }
    }

    public void destroy() {
        stop();

        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }

        mCallback = null;
        mHandler = null;
    }

    private void readAllArmMotorStatuses() {
        if (mRobotMotion == null || mCallback == null) {
            return;
        }

        requestMotorStatus(
                (int) RobotDevices.Motors.ARM_ROTATION_LEFT,
                "Left arm rotation"
        );
        requestMotorStatus(
                (int) RobotDevices.Motors.ARM_SWING_LEFT,
                "Left arm swing"
        );
        requestMotorStatus(
                (int) RobotDevices.Motors.FOREARM_ROTATION_LEFT,
                "Left forearm rotation"
        );
        requestMotorStatus(
                (int) RobotDevices.Motors.FOREARM_SWING_LEFT,
                "Left forearm swing"
        );
        requestMotorStatus(
                (int) RobotDevices.Motors.WRIST_LEFT,
                "Left wrist"
        );

        requestMotorStatus(
                (int) RobotDevices.Motors.ARM_ROTATION_RIGHT,
                "Right arm rotation"
        );
        requestMotorStatus(
                (int) RobotDevices.Motors.ARM_SWING_RIGHT,
                "Right arm swing"
        );
        requestMotorStatus(
                (int) RobotDevices.Motors.FOREARM_ROTATION_RIGHT,
                "Right forearm rotation"
        );
        requestMotorStatus(
                (int) RobotDevices.Motors.FOREARM_SWING_RIGHT,
                "Right forearm swing"
        );
        requestMotorStatus(
                (int) RobotDevices.Motors.WRIST_RIGHT,
                "Right wrist"
        );
    }

    private void requestMotorStatus(final int motorId, final String motorName) {
        if (mRobotMotion == null || mHandler == null || mCallback == null) {
            return;
        }

        try {
            mRobotMotion.getStatus(
                    motorId,
                    new RobotMotion.OnResult() {
                        @Override
                        public void onCompleted(
                                final int id,
                                final int angle,
                                final int direction,
                                final int speed) {

                            Log.d(TAG,
                                    "Motor status: name=" + motorName
                                            + ", id=" + id
                                            + ", angle=" + angle
                                            + ", direction=" + direction
                                            + ", speed=" + speed);

                            if (Looper.myLooper() == Looper.getMainLooper()) {
                                notifyCallback(id, angle, direction, speed);
                                return;
                            }

                            final Handler handler = mHandler;
                            if (handler == null) {
                                return;
                            }

                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    notifyCallback(id, angle, direction, speed);
                                }
                            });
                        }
                    }
            );

        } catch (Exception e) {
            Log.w(TAG, "Motor status request failed for " + motorName, e);
        }
    }

    private void notifyCallback(int motorId, int angle, int direction, int speed) {
        Callback callback = mCallback;
        if (callback != null) {
            callback.onMotorStatusUpdated(motorId, angle, direction, speed);
        }
    }
}
