/* File: PushyFlutterBackgroundExecutor.java */
package me.pushy.sdk.flutter.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.util.Log;

import org.json.JSONObject;

import io.flutter.FlutterInjector;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.dart.DartExecutor;
import io.flutter.embedding.engine.dart.DartExecutor.DartCallback;
import io.flutter.embedding.engine.loader.FlutterCallbackInformation; // Ensure your Flutter SDK is 1.12.13+hotfix.6 or later.
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.JSONMethodCodec;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import me.pushy.sdk.config.PushyLogging;
import me.pushy.sdk.flutter.PushyPlugin;
import me.pushy.sdk.flutter.config.PushyChannels;
import me.pushy.sdk.flutter.config.PushySharedPrefs;

public class PushyFlutterBackgroundExecutor implements MethodCallHandler {

    private boolean mIsIsolateRunning;
    private MethodChannel mBackgroundChannel;
    private FlutterEngine mBackgroundFlutterEngine;

    private static Context mContext;
    private static PushyFlutterBackgroundExecutor mInstance;

    public static boolean isRunning() {
        return isInitialized() && getSingletonInstance().mIsIsolateRunning;
    }

    public void startBackgroundIsolate(Context context) {
        SharedPreferences sharedPreferences = PushyPersistence.getSettings(context);
        long isolateCallbackId = sharedPreferences.getLong(PushySharedPrefs.FLUTTER_ISOLATE_ID, 0);
        long notificationHandlerCallbackId = sharedPreferences.getLong(PushySharedPrefs.FLUTTER_NOTIFICATION_HANDLER_ID, 0);
        if (isolateCallbackId == 0 || notificationHandlerCallbackId == 0) {
            Log.e(PushyLogging.TAG, "Isolate / notification callback IDs are missing from SharedPreferences");
            return;
        }
        startBackgroundIsolate(context, isolateCallbackId, notificationHandlerCallbackId);
    }

    public void startBackgroundIsolate(Context context, long isolateCallbackId, long notificationHandlerCallbackId) {
        if (mBackgroundFlutterEngine != null || isRunning()) {
            Log.e(PushyLogging.TAG, "Background isolate already started / running");
            return;
        }
        Log.d(PushyLogging.TAG, "Initializing FlutterBackgroundExecutor background isolate");
        mContext = context;
        persistCallbackHandleIds(context, isolateCallbackId, notificationHandlerCallbackId);

        AssetManager assets = context.getAssets();
        FlutterInjector.instance().flutterLoader().startInitialization(context);
        String appBundlePath = FlutterInjector.instance().flutterLoader().findAppBundlePath();
        if (appBundlePath != null) {
            mBackgroundFlutterEngine = new FlutterEngine(context);
            FlutterCallbackInformation flutterCallback = FlutterInjector.instance().flutterLoader().getCallbackInformation(isolateCallbackId);
            if (flutterCallback == null) {
                Log.e(PushyLogging.TAG, "Failed to locate _isolate() callback");
                return;
            }
            DartExecutor executor = mBackgroundFlutterEngine.getDartExecutor();
            initializeBackgroundMethodChannel(executor);
            executor.executeDartCallback(new DartCallback(assets, appBundlePath, flutterCallback));
        }
    }

    public static void persistCallbackHandleIds(Context context, long isolateCallbackId, long notificationHandlerCallbackId) {
        SharedPreferences sharedPreferences = PushyPersistence.getSettings(context);
        sharedPreferences.edit().putLong(PushySharedPrefs.FLUTTER_ISOLATE_ID, isolateCallbackId).apply();
        sharedPreferences.edit().putLong(PushySharedPrefs.FLUTTER_NOTIFICATION_HANDLER_ID, notificationHandlerCallbackId).apply();
    }

    private void initializeBackgroundMethodChannel(BinaryMessenger messenger) {
        mBackgroundChannel = new MethodChannel(messenger, PushyChannels.BACKGROUND_CHANNEL, JSONMethodCodec.INSTANCE);
        mBackgroundChannel.setMethodCallHandler(this);
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        if ("notificationCallbackReady".equals(call.method)) {
            Log.d(PushyLogging.TAG, "Isolate called notificationCallbackReady()");
            onIsolateInitialized();
            result.success(true);
        }
    }

    private void onIsolateInitialized() {
        mIsIsolateRunning = true;
        PushyPlugin.deliverPendingNotifications(mContext);
    }

    public void invokeDartNotificationHandler(JSONObject notification, Context context) {
        SharedPreferences sharedPreferences = PushyPersistence.getSettings(context);
        long notificationHandlerCallbackId = sharedPreferences.getLong(PushySharedPrefs.FLUTTER_NOTIFICATION_HANDLER_ID, 0);
        mBackgroundChannel.invokeMethod("onNotificationReceived",
                new Object[] {notificationHandlerCallbackId, notification.toString()}, null);
    }

    private static boolean isInitialized() {
        return mInstance != null;
    }

    public static PushyFlutterBackgroundExecutor getSingletonInstance() {
        if (mInstance != null) {
            return mInstance;
        }
        mInstance = new PushyFlutterBackgroundExecutor();
        return mInstance;
    }
}

/* File: PushyPlugin.java */
package me.pushy.sdk.flutter;

import androidx.annotation.NonNull;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;

public class PushyPlugin implements FlutterPlugin, MethodCallHandler {

    private MethodChannel channel;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPlugin.FlutterPluginBinding binding) {
        channel = new MethodChannel(binding.getBinaryMessenger(), "pushy_flutter");
        channel.setMethodCallHandler(this);
    }

    @Override
    public void onMethodCall(MethodCall call, MethodChannel.Result result) {
        // Implement your plugin functionality here.
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPlugin.FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
    }

    // Remove the static registerWith(Registrar) method since the new embedding doesn't require it.
}