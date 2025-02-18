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
import io.flutter.embedding.engine.loader.FlutterCallbackInformation;
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
        // Ensure isolate is running and callback has been invoked
        return isInitialized() && getSingletonInstance().mIsIsolateRunning;
    }

    public void startBackgroundIsolate(Context context) {
        // Get shared preferences handle
        SharedPreferences sharedPreferences = PushyPersistence.getSettings(context);

        // Retrieve previously-stored callback handle IDs
        long isolateCallbackId = sharedPreferences.getLong(PushySharedPrefs.FLUTTER_ISOLATE_ID, 0);
        long notificationHandlerCallbackId = sharedPreferences.getLong(PushySharedPrefs.FLUTTER_NOTIFICATION_HANDLER_ID, 0);

        // Check for null values before continuing
        if (isolateCallbackId == 0 || notificationHandlerCallbackId == 0) {
            Log.e(PushyLogging.TAG, "Isolate / notification callback IDs are missing from SharedPreferences");
            return;
        }

        // Start isolate with persisted callback IDs
        startBackgroundIsolate(context, isolateCallbackId, notificationHandlerCallbackId);
    }

    public void startBackgroundIsolate(Context context, long isolateCallbackId, long notificationHandlerCallbackId) {
        // Additional check to ensure isolate not already started
        if (mBackgroundFlutterEngine != null || isRunning()) {
            Log.e(PushyLogging.TAG, "Background isolate already started / running");
            return;
        }

        // Log initialization
        Log.d(PushyLogging.TAG, "Initializing FlutterBackgroundExecutor background isolate");

        // Store context
        mContext = context;

        // Persist callback handles in SharedPreferences for when process is terminated
        persistCallbackHandleIds(context, isolateCallbackId, notificationHandlerCallbackId);

        // Get assets and app bundle path
        AssetManager assets = context.getAssets();

        // Start initialization using the new API provided by FlutterInjector
        FlutterInjector.instance().flutterLoader().startInitialization(context);

        // Get app bundle path as string
        String appBundlePath = FlutterInjector.instance().flutterLoader().findAppBundlePath();

        // Null safety check
        if (appBundlePath != null) {
            // Create an instance of FlutterEngine before looking up the callback.
            mBackgroundFlutterEngine = new FlutterEngine(context);

            // Look up the _isolate() callback by its handle.
            // Note: Make sure to update the import so that FlutterCallbackInformation comes
            // from io.flutter.embedding.engine.loader instead of the old package.
            FlutterCallbackInformation flutterCallback = FlutterCallbackInformation.lookupCallbackInformation(isolateCallbackId);
            if (flutterCallback == null) {
                Log.e(PushyLogging.TAG, "Failed to locate _isolate() callback");
                return;
            }

            // Get the dart executor of the new engine.
            DartExecutor executor = mBackgroundFlutterEngine.getDartExecutor();

            // Initialize a dedicated channel for communicating with the background isolate.
            initializeBackgroundMethodChannel(executor);

            // Execute the callback which in turn will initialize the Dart background isolate.
            executor.executeDartCallback(new DartCallback(assets, appBundlePath, flutterCallback));
        }
    }

    public static void persistCallbackHandleIds(Context context, long isolateCallbackId, long notificationHandlerCallbackId) {
        // Get shared preferences handle.
        SharedPreferences sharedPreferences = PushyPersistence.getSettings(context);

        // Store callback handle IDs.
        sharedPreferences.edit().putLong(PushySharedPrefs.FLUTTER_ISOLATE_ID, isolateCallbackId).apply();
        sharedPreferences.edit().putLong(PushySharedPrefs.FLUTTER_NOTIFICATION_HANDLER_ID, notificationHandlerCallbackId).apply();
    }

    private void initializeBackgroundMethodChannel(BinaryMessenger messenger) {
        // Initialize a dedicated channel for communicating with the background isolate.
        mBackgroundChannel = new MethodChannel(messenger, PushyChannels.BACKGROUND_CHANNEL, JSONMethodCodec.INSTANCE);

        // Handle method calls in this class.
        mBackgroundChannel.setMethodCallHandler(this);
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        if ("notificationCallbackReady".equals(call.method)) {
            // Notification callback ready event.
            Log.d(PushyLogging.TAG, "Isolate called notificationCallbackReady()");

            // Mark the isolate as running.
            onIsolateInitialized();

            // Send back success.
            result.success(true);
        }
    }

    private void onIsolateInitialized() {
        mIsIsolateRunning = true;
        // Attempt to deliver any pending notifications if necessary.
        PushyPlugin.deliverPendingNotifications(mContext);
    }

    public void invokeDartNotificationHandler(JSONObject notification, Context context) {
        // Get shared preferences handle.
        SharedPreferences sharedPreferences = PushyPersistence.getSettings(context);

        // Retrieve stored notification handler callback handle ID.
        long notificationHandlerCallbackId = sharedPreferences.getLong(PushySharedPrefs.FLUTTER_NOTIFICATION_HANDLER_ID, 0);

        // Invoke the method on the background channel to handle the notification.
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
