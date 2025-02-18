package me.pushy.sdk.flutter.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;

import me.pushy.sdk.config.PushyLogging;
import me.pushy.sdk.util.PushySingleton;

public class PushyPersistence {
    public static final String NOTIFICATION_ICON = "pushyNotificationIcon";
    public static final String PENDING_NOTIFICATIONS = "pushyPendingNotifications";
    private static final Object sLock = new Object();

    
    public static void persistNotification(@NonNull JSONObject notification, @NonNull Context context) {
        synchronized (sLock) {
            JSONArray pendingNotifications = getPendingNotifications(context);
            pendingNotifications.put(notification);
            
            SharedPreferences.Editor editor = getSettings(context).edit();
            editor.putString(PENDING_NOTIFICATIONS, pendingNotifications.toString());
            editor.apply(); // Use apply() instead of commit() for better performance
        }
    }

    public static SharedPreferences getSettings(Context context) {
        // Get default app SharedPreferences
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static void persistNotification(@NonNull JSONObject notification, @NonNull Context context) {
        synchronized (sLock) {
            JSONArray pendingNotifications = getPendingNotifications(context);
            pendingNotifications.put(notification);
            
            SharedPreferences.Editor editor = getSettings(context).edit();
            editor.putString(PENDING_NOTIFICATIONS, pendingNotifications.toString());
            editor.apply(); // Use apply() instead of commit() for better performance
        }
    }
    public static void setNotificationIcon(String icon, Context context) {
        // Store notification icon in SharedPreferences
        getSettings(context).edit().putString(PushyPersistence.NOTIFICATION_ICON, icon).commit();
    }

    public static String getNotificationIcon( Context context) {
        // Get notification icon from SharedPreferences
        return getSettings(context).getString(PushyPersistence.NOTIFICATION_ICON, null);
    }

    @NonNull
    public static JSONArray getPendingNotifications(@NonNull Context context) {
        synchronized (sLock) {
            String pendingNotifications = PushySingleton.getSettings(context)
                .getString(PENDING_NOTIFICATIONS, "[]");
            
            try {
                return new JSONArray(pendingNotifications);
            } catch (JSONException e) {
                Log.e(PushyLogging.TAG, "Failed to parse pending notifications", e);
                return new JSONArray();
            }
        }
    }

    public static void clearPendingNotifications(Context context) {
        // Clear the pending notifications from SharedPreferences
        PushySingleton.getSettings(context).edit().remove(PENDING_NOTIFICATIONS).commit();
    }

    public static JSONObject getJSONObjectFromIntentExtras(Intent intent) {
        // Prepare JSON object containing the notification payload
        JSONObject json = new JSONObject();

        // Get intent extras
        Bundle bundle = intent.getExtras();

        // Get JSON key names
        Set<String> keys = bundle.keySet();

        // Traverse keys
        for (String key : keys) {
            try {
                // Attempt to insert the key and its value into the JSONObject
                json.put(key, bundle.get(key));
            }
            catch (JSONException e) {
                // Log error to logcat and stop execution
                Log.e(PushyLogging.TAG, "Failed to insert intent extra into JSONObject:" + e.getMessage(), e);
                return json;
            }
        }

        // All done
        return json;
    }

    public static void cleanup(@NonNull Context context) {
        synchronized (sLock) {
            SharedPreferences.Editor editor = getSettings(context).edit();
            editor.remove(PENDING_NOTIFICATIONS);
            editor.apply();
        }
    }

}
