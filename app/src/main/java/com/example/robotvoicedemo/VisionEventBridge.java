package com.example.robotvoicedemo;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import dalvik.system.DexClassLoader;

public class VisionEventBridge {
    private static final String TAG = "iPalVisionBridge";

    private static final String LOCKER_NAME = "iPalVisionBridge";

    private static final String ROBOT_VISION_CLIENT_CLASS =
            "com.avatarmind.robotvisionservice.RobotVisionClient";

    private static final String FACE_EVENT_LISTENER_CLASS =
            "com.avatarmind.robotvisionservice.RobotVisionClient$FaceEventListener";

    private static final String[] CANDIDATE_PACKAGES = new String[] {
            "com.avatarmind.robot.facetrack",
            "com.avatar.wsclservice",
            "com.avatarmind.childcare.vision",
            "com.avatarmind.robot.faceagenda",
            "com.avatarmind.robotvisionservice"
    };

    private final Context mContext;
    private final Context mAppContext;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private DexClassLoader mDexClassLoader;
    private Class<?> mClientClass;
    private Object mClient;

    private Method mOnResume;
    private Method mOnPause;
    private Method mOnDestroy;
    private Method mTurnEvent;

    private boolean mFaceEventsRequested = false;
    private String mLastFaceEventRaw = "";
    private FaceState mLastFaceState = new FaceState();
    private long mLastFaceLogTimeMs = 0L;
    private String mLastLoggedFaceName = "";
    private boolean mLastLoggedFaceDetected = false;
    private long mLastRawVisionLogTimeMs = 0L;

    public VisionEventBridge(Context context) {
        mContext = context;
        mAppContext = context.getApplicationContext();
    }

    public synchronized void start() {
        if (mClient != null) {
            resume();
            Log.i(TAG, "Vision bridge already started; waiting for connection before requesting face events.");
            return;
        }

        try {
            String sourceApkPath = findVisionClientApkPath();

            if (sourceApkPath == null) {
                Log.w(TAG, "No installed APK found that may contain RobotVisionClient.");
                return;
            }

            File optimizedDir = mAppContext.getDir("robot_vision_dex", Context.MODE_PRIVATE);
            String nativeLibrarySearchPath = prepareNativeLibrarySearchPath();

            mDexClassLoader = new DexClassLoader(
                    sourceApkPath,
                    optimizedDir.getAbsolutePath(),
                    nativeLibrarySearchPath,
                    mAppContext.getClassLoader()
            );

            Log.i(TAG, "Native library search path: " + nativeLibrarySearchPath);

            mClientClass = mDexClassLoader.loadClass(ROBOT_VISION_CLIENT_CLASS);

            Class<?> listenerClass = mDexClassLoader.loadClass(FACE_EVENT_LISTENER_CLASS);
            Object listenerProxy = createFaceEventListenerProxy(listenerClass);

            Constructor<?> constructor = mClientClass.getDeclaredConstructor(
                    String.class,
                    Context.class,
                    listenerClass,
                    Boolean.TYPE,
                    Boolean.TYPE
            );
            constructor.setAccessible(true);

            // withCamera = false
            // withEventlistener = true
            mClient = constructor.newInstance(
                    LOCKER_NAME,
                    mContext,
                    listenerProxy,
                    Boolean.FALSE,
                    Boolean.TRUE
            );

            mOnResume = findMethod(mClientClass, "onResume");
            mOnPause = findMethod(mClientClass, "onPause");
            mOnDestroy = findMethod(mClientClass, "onDestroy");
            mTurnEvent = findMethod(mClientClass, "TurnEvent", String.class, Boolean.TYPE);

            Log.i(TAG, "RobotVisionClient loaded from: " + sourceApkPath);

            resume();
            Log.i(TAG, "Vision bridge start complete; waiting for connection before requesting face events.");

            Log.i(TAG, "Vision event bridge started. Camera is NOT opened directly by this app.");

        } catch (Throwable t) {
            Log.e(TAG, "Failed to start RobotVisionClient bridge.", t);
        }
    }

    public synchronized void resume() {
        if (mClient == null) {
            start();
            return;
        }

        mFaceEventsRequested = false;


        Log.i(TAG, "Resume detected; face event request flag reset.");


        invokeNoArg(mOnResume, "onResume");
        Log.i(TAG, "onResume complete; waiting for connection before requesting face events.");
    }

    public synchronized void pause() {
        if (mClient == null) {
            return;
        }

        invokeNoArg(mOnPause, "onPause");
    }

    public synchronized void destroy() {
        if (mClient == null) {
            return;
        }

        setFaceEventsEnabled(false);
        invokeNoArg(mOnPause, "onPause");
        invokeNoArg(mOnDestroy, "onDestroy");

        mClient = null;
        mClientClass = null;
        mDexClassLoader = null;
        mFaceEventsRequested = false;

        Log.i(TAG, "Vision event bridge destroyed.");
    }

    private String prepareNativeLibrarySearchPath() throws Exception {
        File nativeLibDir = mAppContext.getDir("robot_vision_native_libs", Context.MODE_PRIVATE);

        String sourcePath = isCurrentProcess64Bit()
                ? "/system/lib64/libRVF_Listener_JNI.so"
                : "/system/lib/libRVF_Listener_JNI.so";

        File targetFile = new File(nativeLibDir, "libRVF_Listener_JNI.so");

        copyFileIfNeeded(sourcePath, targetFile);

        targetFile.setReadable(true, false);
        targetFile.setExecutable(true, false);

        Log.i(TAG, "Prepared native library copy from " + sourcePath + " to " + targetFile.getAbsolutePath());

        return nativeLibDir.getAbsolutePath();
    }

    private boolean isCurrentProcess64Bit() {
        try {
            java.lang.reflect.Method method =
                    android.os.Process.class.getMethod("is64Bit");
            Object result = method.invoke(null);

            if (result instanceof Boolean) {
                return ((Boolean) result).booleanValue();
            }

        } catch (Throwable ignored) {
        }

        String arch = System.getProperty("os.arch", "");
        return arch != null && arch.contains("64");
    }

    private void copyFileIfNeeded(String sourcePath, File targetFile) throws Exception {
        File sourceFile = new File(sourcePath);

        if (!sourceFile.exists()) {
            throw new Exception("Native source library missing: " + sourcePath);
        }

        if (targetFile.exists() && targetFile.length() == sourceFile.length()) {
            Log.i(TAG, "Native library copy already exists: " + targetFile.getAbsolutePath());
            return;
        }

        InputStream inputStream = null;
        OutputStream outputStream = null;

        try {
            inputStream = new FileInputStream(sourceFile);
            outputStream = new FileOutputStream(targetFile, false);

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, bytesRead);
            }

            outputStream.flush();

        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception ignored) {
                }
            }

            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (Exception ignored) {
                }
            }
        }

        Log.i(TAG, "Copied native library to app-private storage: " + targetFile.getAbsolutePath());
    }
    private String findVisionClientApkPath() {
        PackageManager packageManager = mAppContext.getPackageManager();

        for (String packageName : CANDIDATE_PACKAGES) {
            try {
                ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);

                if (info != null && info.sourceDir != null) {
                    Log.i(TAG, "Found candidate APK: " + packageName + " -> " + info.sourceDir);
                    return info.sourceDir;
                }

            } catch (PackageManager.NameNotFoundException ignored) {
                Log.d(TAG, "Candidate package not installed: " + packageName);
            }
        }

        return null;
    }

    private Object createFaceEventListenerProxy(Class<?> listenerClass) {
        return Proxy.newProxyInstance(
                listenerClass.getClassLoader(),
                new Class<?>[] { listenerClass },
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        String methodName = method.getName();

                        if ("onConnectionStatus".equals(methodName)) {
                            Log.i(TAG, "onConnectionStatus: " + argsToString(args));

                            if (args != null && args.length > 0 && Boolean.TRUE.equals(args[0])) {
                            enableFaceEventsWithRetries("onConnectionStatus true");
                        } else {
                            mFaceEventsRequested = false;
                            Log.i(TAG, "onConnectionStatus not true; face event request flag reset.");
                        }

                        } else if ("onVisionEvent".equals(methodName)) {
                            String event = argsToString(args);
                            logRawVisionEventIfNeeded(event);
                            handleVisionEvent(event);

                        } else if ("OnRegisterEvent".equals(methodName)) {
                            Log.i(TAG, "OnRegisterEvent: " + argsToString(args));

                            if (args != null && args.length > 0) {
                                String event = String.valueOf(args[0]).toLowerCase();

                                if (event.contains("connected") || event.contains("started")) {
                                    Log.i(TAG, "Vision registration event received; waiting for onConnectionStatus true before requesting face events.");
                                }
                            }

                        } else if ("OnHeadUpdate".equals(methodName)) {
                            Log.i(TAG, "OnHeadUpdate: " + argsToString(args));

                        } else if ("toString".equals(methodName)) {
                            return "VisionEventBridge.FaceEventListenerProxy";

                        } else if ("hashCode".equals(methodName)) {
                            return Integer.valueOf(System.identityHashCode(proxy));

                        } else if ("equals".equals(methodName)) {
                            return Boolean.valueOf(args != null && args.length > 0 && proxy == args[0]);

                        } else {
                            Log.d(TAG, "Other callback: " + methodName + ": " + argsToString(args));
                        }

                        return defaultReturnValue(method.getReturnType());
                    }
                }
        );
    }

    private void enableFaceEventsWithRetries(final String reason) {
        requestFaceEventsOnce(reason);
    }

    private void requestFaceEventsOnce(String reason) {
        if (mFaceEventsRequested) {
            Log.i(TAG, "Face events already requested. Skipping duplicate request. Reason: " + reason);
            return;
        }

        mFaceEventsRequested = true;
        Log.i(TAG, "Requesting face events once. Reason: " + reason);
        setFaceEventsEnabled(true);
    }

    private void logRawVisionEventIfNeeded(String event) {
        if (event == null) {
            return;
        }

        // Face events arrive many times per second. We still process all of them,
        // but the parsed summary log is enough for normal debugging.
        if (event.startsWith("face;")) {
            return;
        }

        long now = System.currentTimeMillis();

        if (now - mLastRawVisionLogTimeMs < 2000L) {
            return;
        }

        mLastRawVisionLogTimeMs = now;
        Log.i(TAG, "onVisionEvent: " + event);
    }
    private void handleVisionEvent(String event) {
        if (event == null) {
            return;
        }

        mLastFaceEventRaw = event;

        if (!event.startsWith("face;")) {
            return;
        }

        FaceState state = parseFaceEvent(event);
        mLastFaceState = state;

        logFaceStateIfNeeded(state);
    }

    private FaceState parseFaceEvent(String event) {
        FaceState state = new FaceState();
        state.raw = event;

        try {
            String[] sections = event.split(";");

            if (sections.length >= 2) {
                String[] attrs = sections[1].split(",");

                state.detected = attrs.length > 0 && "yes".equalsIgnoreCase(attrs[0]);
                state.name = getString(attrs, 1, "unknown");
                state.displayName = getString(attrs, 2, state.name);
                state.gender = getInt(attrs, 3, -1);
                state.age = getInt(attrs, 4, -1);

                // These values move as the face moves. Keep names generic until fully confirmed.
                state.value6 = getInt(attrs, 6, 0);
                state.value7 = getInt(attrs, 7, 0);
                state.value8 = getInt(attrs, 8, 0);

                state.personId = getInt(attrs, 9, -1);
                state.confidence = getInt(attrs, 13, 0);
            }

            if (sections.length >= 8) {
                state.x = parseIntSafe(sections[4], 0);
                state.y = parseIntSafe(sections[5], 0);
                state.width = parseIntSafe(sections[6], 0);
                state.height = parseIntSafe(sections[7], 0);
            }

        } catch (Throwable t) {
            Log.e(TAG, "Failed to parse face event: " + event, t);
        }

        return state;
    }

    private void logFaceStateIfNeeded(FaceState state) {
        if (state == null) {
            return;
        }

        long now = System.currentTimeMillis();
        boolean nameChanged = state.name != null && !state.name.equals(mLastLoggedFaceName);
        boolean detectionChanged = state.detected != mLastLoggedFaceDetected;
        boolean enoughTimePassed = now - mLastFaceLogTimeMs >= 1000L;

        if (!nameChanged && !detectionChanged && !enoughTimePassed) {
            return;
        }

        mLastFaceLogTimeMs = now;
        mLastLoggedFaceName = state.name;
        mLastLoggedFaceDetected = state.detected;

        Log.i(TAG, "Parsed face: detected=" + state.detected
                + ", name=" + state.name
                + ", confidence=" + state.confidence
                + ", personId=" + state.personId
                + ", box=" + state.x + "," + state.y + "," + state.width + "," + state.height);
    }

    public String getVisionContextForPrompt() {
        FaceState state = mLastFaceState;

        if (state == null || !state.detected) {
            return "Vision: no face detected.";
        }

        StringBuilder builder = new StringBuilder();

        builder.append("Vision: face detected.");

        if (state.name != null
                && state.name.length() > 0
                && !"unknown".equalsIgnoreCase(state.name)
                && state.confidence >= 80) {
            builder.append(" Recognized person: ");
            builder.append(state.name);
            builder.append(".");
            builder.append(" Confidence: ");
            builder.append(state.confidence);
            builder.append(".");
        } else {
            builder.append(" Person is unknown.");
        }

        builder.append(" Face box: x=");
        builder.append(state.x);
        builder.append(", y=");
        builder.append(state.y);
        builder.append(", width=");
        builder.append(state.width);
        builder.append(", height=");
        builder.append(state.height);
        builder.append(".");

        return builder.toString();
    }
    public FaceState getLastFaceState() {
        return mLastFaceState;
    }

    public String getLastFaceEventRaw() {
        return mLastFaceEventRaw;
    }

    private static String getString(String[] values, int index, String defaultValue) {
        if (values == null || index < 0 || index >= values.length) {
            return defaultValue;
        }

        String value = values[index];

        if (value == null || value.length() == 0) {
            return defaultValue;
        }

        return value;
    }

    private static int getInt(String[] values, int index, int defaultValue) {
        if (values == null || index < 0 || index >= values.length) {
            return defaultValue;
        }

        return parseIntSafe(values[index], defaultValue);
    }

    private static int parseIntSafe(String value, int defaultValue) {
        try {
            if (value == null) {
                return defaultValue;
            }

            return Integer.parseInt(value.trim());

        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    public static class FaceState {
        public boolean detected = false;
        public String name = "unknown";
        public String displayName = "unknown";
        public int gender = -1;
        public int age = -1;
        public int value6 = 0;
        public int value7 = 0;
        public int value8 = 0;
        public int personId = -1;
        public int confidence = 0;
        public int x = 0;
        public int y = 0;
        public int width = 0;
        public int height = 0;
        public String raw = "";
    }
    private void setFaceEventsEnabled(boolean enabled) {
        if (mClient == null || mTurnEvent == null) {
            return;
        }

        try {
            mTurnEvent.invoke(mClient, "face", Boolean.valueOf(enabled));
            Log.i(TAG, "TurnEvent(\"face\", " + enabled + ") called.");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to call TurnEvent(\"face\", " + enabled + ").", t);
        }
    }

    private void invokeNoArg(Method method, String methodName) {
        if (mClient == null || method == null) {
            return;
        }

        try {
            method.invoke(mClient);
            Log.i(TAG, methodName + " called.");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to call " + methodName + ".", t);
        }
    }

    private static Method findMethod(Class<?> targetClass, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        try {
            Method method = targetClass.getMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            Method method = targetClass.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        }
    }

    private static String argsToString(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }

            builder.append(String.valueOf(args[i]));
        }

        return builder.toString();
    }

    private static Object defaultReturnValue(Class<?> returnType) {
        if (returnType == null || Void.TYPE.equals(returnType)) {
            return null;
        }

        if (Boolean.TYPE.equals(returnType)) {
            return Boolean.FALSE;
        }

        if (Byte.TYPE.equals(returnType)) {
            return Byte.valueOf((byte) 0);
        }

        if (Short.TYPE.equals(returnType)) {
            return Short.valueOf((short) 0);
        }

        if (Integer.TYPE.equals(returnType)) {
            return Integer.valueOf(0);
        }

        if (Long.TYPE.equals(returnType)) {
            return Long.valueOf(0L);
        }

        if (Float.TYPE.equals(returnType)) {
            return Float.valueOf(0f);
        }

        if (Double.TYPE.equals(returnType)) {
            return Double.valueOf(0d);
        }

        if (Character.TYPE.equals(returnType)) {
            return Character.valueOf('\0');
        }

        return null;
    }
}











