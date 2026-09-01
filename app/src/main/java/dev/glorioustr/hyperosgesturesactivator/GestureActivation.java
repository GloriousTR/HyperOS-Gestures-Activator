package dev.glorioustr.hyperosgesturesactivator;

import android.content.Context;
import android.provider.Settings;

final class GestureActivation {
    static final String KEY_ENABLED = "hga_gesture_activation_enabled";
    static final String KEY_SYSTEM_UI_READY = "hga_systemui_hook_ready";
    static final String KEY_LAUNCHER_READY = "hga_launcher_hook_ready";
    static final String KEY_FORCE_FSG_NAV_BAR = "force_fsg_nav_bar";
    static final String KEY_NAVIGATION_MODE = "navigation_mode";

    private static final String READY_VERSION = "v0.2.0";

    private GestureActivation() {
    }

    static boolean isEnabled(Context context) {
        return readGlobalInt(context, KEY_ENABLED, 0) == 1;
    }

    static boolean isSystemUiReady(Context context) {
        return readyToken(context).equals(readGlobalString(context, KEY_SYSTEM_UI_READY));
    }

    static boolean isLauncherReady(Context context) {
        return readyToken(context).equals(readGlobalString(context, KEY_LAUNCHER_READY));
    }

    static boolean markSystemUiReady(Context context) {
        return writeGlobalString(context, KEY_SYSTEM_UI_READY, readyToken(context));
    }

    static boolean markLauncherReady(Context context) {
        return writeGlobalString(context, KEY_LAUNCHER_READY, readyToken(context));
    }

    static int readGlobalInt(Context context, String key, int fallback) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), key, fallback);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    static boolean writeGlobalInt(Context context, String key, int value) {
        try {
            return Settings.Global.putInt(context.getContentResolver(), key, value);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String readyToken(Context context) {
        return READY_VERSION + ':' + readGlobalInt(context, Settings.Global.BOOT_COUNT, -1);
    }

    private static String readGlobalString(Context context, String key) {
        try {
            String value = Settings.Global.getString(context.getContentResolver(), key);
            return value == null ? "" : value;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean writeGlobalString(Context context, String key, String value) {
        try {
            return Settings.Global.putString(context.getContentResolver(), key, value);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
