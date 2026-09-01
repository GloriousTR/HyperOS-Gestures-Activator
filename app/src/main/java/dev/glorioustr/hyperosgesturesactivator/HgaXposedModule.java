package dev.glorioustr.hyperosgesturesactivator;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * Read-only navigation diagnostics for the first HGA milestone.
 *
 * <p>This version deliberately observes state and SystemUI decisions without changing return
 * values, settings, overlays, or navigation bar visibility.</p>
 */
public final class HgaXposedModule extends XposedModule {
    private static final String TAG = "HGA/Diagnostics";
    private static final String BUILD_MARK = "v0.1.0-navigation-diagnostics";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String NAVIGATION_MODE_CONTROLLER =
            "com.android.systemui.navigationbar.NavigationModeController";
    private static final String SYSTEM_UI_APPLICATION =
            "com.android.systemui.SystemUIApplication";
    private static final String ACTION_PREFERRED_ACTIVITY_CHANGED =
            "android.intent.action.ACTION_PREFERRED_ACTIVITY_CHANGED";

    private static final String[] OBSERVED_METHOD_NAMES = {
            "updateCurrentInteractionMode",
            "updateInteractionMode",
            "onDefaultDisplayChanged",
            "onOverlayChanged",
            "deferGesturalNavOverlayIfNecessary",
            "restoreGesturalNavOverlayIfNecessary",
            "setModeOverlay",
            "switchFromGestureNavModeIfNotSupportedByDefaultLauncher",
            "isGestureNavSupportedByDefaultLauncher",
            "onNavigationModeChanged"
    };

    private static final SettingSpec[] OBSERVED_SETTINGS = {
            new SettingSpec(SettingSpec.SECURE, "force_fsg_nav_bar"),
            new SettingSpec(SettingSpec.SECURE, "navigation_mode"),
            new SettingSpec(SettingSpec.SECURE, "navigation_bar_mode"),
            new SettingSpec(SettingSpec.SECURE, "miui_fullscreen_gesture"),
            new SettingSpec(SettingSpec.SECURE, "system_navigation_keys_enabled"),
            new SettingSpec(SettingSpec.SECURE, "gesture_navigation_bar"),
            new SettingSpec(SettingSpec.GLOBAL, "force_fsg_nav_bar"),
            new SettingSpec(SettingSpec.GLOBAL, "navigation_mode"),
            new SettingSpec(SettingSpec.GLOBAL, "policy_control"),
            new SettingSpec(SettingSpec.GLOBAL, "navigationbar_is_min"),
            new SettingSpec(SettingSpec.SYSTEM, "force_fsg_nav_bar")
    };

    private final List<XposedInterface.HookHandle> hookHandles = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Context systemUiContext;
    private Object navigationModeController;
    private ContentObserver settingsObserver;
    private BroadcastReceiver launcherReceiver;
    private boolean systemUiHooksInstalled;
    private boolean diagnosticsAttached;
    private boolean snapshotScheduled;

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        log(Log.INFO, TAG, "Module loaded, build=" + BUILD_MARK
                + ", process=" + param.getProcessName()
                + ", systemServer=" + param.isSystemServer());
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!SYSTEM_UI.equals(param.getPackageName()) || systemUiHooksInstalled) {
            return;
        }
        systemUiHooksInstalled = true;
        ClassLoader classLoader = param.getDefaultClassLoader();
        log(Log.INFO, TAG, "SystemUI loaded, source=" + param.getApplicationInfo().sourceDir);
        installSystemUiApplicationHook(classLoader);
        installNavigationModeControllerHooks(classLoader);
    }

    private void installSystemUiApplicationHook(ClassLoader classLoader) {
        try {
            Class<?> applicationClass = Class.forName(
                    SYSTEM_UI_APPLICATION, false, classLoader);
            Method onCreate = applicationClass.getDeclaredMethod("onCreate");
            onCreate.setAccessible(true);
            record(hook(onCreate)
                    .setId("hga_systemui_application_on_create")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        if (chain.getThisObject() instanceof Context) {
                            attachDiagnostics((Context) chain.getThisObject());
                        } else {
                            log(Log.WARN, TAG, "SystemUIApplication did not expose Context");
                        }
                        return result;
                    }));
            log(Log.INFO, TAG, "Hooked SystemUIApplication.onCreate");
        } catch (Throwable throwable) {
            log(Log.ERROR, TAG, "Failed to hook SystemUIApplication.onCreate", throwable);
        }
    }

    private void installNavigationModeControllerHooks(ClassLoader classLoader) {
        try {
            Class<?> controllerClass = Class.forName(
                    NAVIGATION_MODE_CONTROLLER, false, classLoader);
            int constructorIndex = 0;
            for (Constructor<?> constructor : controllerClass.getDeclaredConstructors()) {
                constructor.setAccessible(true);
                final int index = constructorIndex++;
                record(hook(constructor)
                        .setId("hga_navigation_controller_ctor_" + index)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            navigationModeController = chain.getThisObject();
                            log(Log.INFO, TAG, "NavigationModeController constructed: "
                                    + describeController(navigationModeController));
                            scheduleSnapshot("controller-constructed");
                            return result;
                        }));
            }

            int observedCount = 0;
            for (Method method : controllerClass.getDeclaredMethods()) {
                if (!isObservedMethod(method.getName())) {
                    continue;
                }
                method.setAccessible(true);
                String hookId = "hga_navigation_" + method.getName()
                        + "_" + observedCount++;
                record(hook(method)
                        .setId(hookId)
                        .intercept(chain -> traceNavigationDecision(chain, hookId)));
            }
            log(Log.INFO, TAG, "Installed NavigationModeController diagnostics"
                    + ", constructors=" + constructorIndex
                    + ", methods=" + observedCount);
        } catch (Throwable throwable) {
            log(Log.ERROR, TAG, "NavigationModeController unavailable", throwable);
        }
    }

    private Object traceNavigationDecision(XposedInterface.Chain chain, String hookId)
            throws Throwable {
        log(Log.INFO, TAG, "before " + hookId
                + ", args=" + describeArgs(chain.getArgs())
                + ", controller=" + describeController(chain.getThisObject()));
        Object result = chain.proceed();
        navigationModeController = chain.getThisObject();
        log(Log.INFO, TAG, "after " + hookId
                + ", result=" + shortValue(result)
                + ", controller=" + describeController(navigationModeController));
        scheduleSnapshot(hookId);
        return result;
    }

    private void attachDiagnostics(Context context) {
        if (diagnosticsAttached) {
            return;
        }
        diagnosticsAttached = true;
        systemUiContext = context.getApplicationContext() == null
                ? context : context.getApplicationContext();
        registerSettingsObservers();
        registerLauncherReceiver();
        log(Log.INFO, TAG, "Read-only diagnostics attached to SystemUI");
        scheduleSnapshot("systemui-created");
    }

    private void registerSettingsObservers() {
        settingsObserver = new ContentObserver(mainHandler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                log(Log.INFO, TAG, "Observed navigation setting change, uri=" + uri);
                scheduleSnapshot("setting-changed:" + uri);
            }
        };
        ContentResolver resolver = systemUiContext.getContentResolver();
        for (SettingSpec setting : OBSERVED_SETTINGS) {
            try {
                resolver.registerContentObserver(setting.uri(), false, settingsObserver);
            } catch (Throwable throwable) {
                log(Log.WARN, TAG, "Unable to observe " + setting, throwable);
            }
        }
    }

    private void registerLauncherReceiver() {
        launcherReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent == null ? "null" : intent.getAction();
                log(Log.INFO, TAG, "Launcher-related broadcast: " + action);
                scheduleSnapshot("broadcast:" + action);
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_PREFERRED_ACTIVITY_CHANGED);
        try {
            systemUiContext.registerReceiver(
                    launcherReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "Unable to register launcher receiver", throwable);
        }
    }

    private void scheduleSnapshot(String reason) {
        if (systemUiContext == null) {
            return;
        }
        synchronized (this) {
            if (snapshotScheduled) {
                return;
            }
            snapshotScheduled = true;
        }
        mainHandler.postDelayed(() -> {
            synchronized (HgaXposedModule.this) {
                snapshotScheduled = false;
            }
            logNavigationSnapshot(reason);
        }, 250L);
    }

    private void logNavigationSnapshot(String reason) {
        try {
            log(Log.INFO, TAG, "NAV_SNAPSHOT reason=" + reason
                    + " | home=" + resolveDefaultHome(systemUiContext)
                    + " | settings=" + readSettings(systemUiContext.getContentResolver())
                    + " | overlays=" + readNavigationOverlays(systemUiContext)
                    + " | controller=" + describeController(navigationModeController));
        } catch (Throwable throwable) {
            log(Log.ERROR, TAG, "Failed to capture navigation snapshot", throwable);
        }
    }

    private static String resolveDefaultHome(Context context) {
        try {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME);
            ResolveInfo resolved = context.getPackageManager().resolveActivity(
                    homeIntent,
                    PackageManager.MATCH_DEFAULT_ONLY
                            | PackageManager.MATCH_DIRECT_BOOT_AWARE
                            | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);
            if (resolved == null || resolved.activityInfo == null) {
                return "unresolved";
            }
            ComponentName component = new ComponentName(
                    resolved.activityInfo.packageName, resolved.activityInfo.name);
            return component.flattenToShortString();
        } catch (Throwable throwable) {
            return "error:" + throwable.getClass().getSimpleName();
        }
    }

    private static String readSettings(ContentResolver resolver) {
        StringBuilder result = new StringBuilder("{");
        for (int index = 0; index < OBSERVED_SETTINGS.length; index++) {
            SettingSpec setting = OBSERVED_SETTINGS[index];
            if (index > 0) {
                result.append(", ");
            }
            result.append(setting).append('=').append(setting.read(resolver));
        }
        return result.append('}').toString();
    }

    private static String readNavigationOverlays(Context context) {
        try {
            Method getSystemService = Context.class.getMethod(
                    "getSystemService", String.class);
            Object overlayManager = getSystemService.invoke(context, "overlay");
            if (overlayManager == null) {
                return "overlay-service-null";
            }
            Method query = findMethod(
                    overlayManager.getClass(), "getOverlayInfosForTarget", 2);
            if (query == null) {
                return "query-method-unavailable";
            }
            query.setAccessible(true);
            UserHandle user = UserHandle.getUserHandleForUid(Process.myUid());
            Object raw = query.invoke(overlayManager, "android", user);
            if (!(raw instanceof Collection<?>)) {
                return "unexpected:" + shortValue(raw);
            }
            List<String> navigationOverlays = new ArrayList<>();
            for (Object info : (Collection<?>) raw) {
                String packageName = String.valueOf(invokeNoArg(info, "getPackageName"));
                String category = String.valueOf(invokeNoArg(info, "getCategory"));
                String searchable = (packageName + ' ' + category).toLowerCase(Locale.ROOT);
                if (!searchable.contains("navbar") && !searchable.contains("navigation")) {
                    continue;
                }
                Object enabled = invokeNoArg(info, "isEnabled");
                Object state = invokeNoArg(info, "getState");
                navigationOverlays.add(packageName + "{enabled=" + enabled
                        + ",state=" + state + ",category=" + category + '}');
            }
            return navigationOverlays.toString();
        } catch (Throwable throwable) {
            return "error:" + throwable.getClass().getSimpleName()
                    + ':' + throwable.getMessage();
        }
    }

    private static String describeController(Object controller) {
        if (controller == null) {
            return "null";
        }
        List<String> fields = new ArrayList<>();
        Class<?> type = controller.getClass();
        while (type != null && fields.size() < 40) {
            for (Field field : type.getDeclaredFields()) {
                String lower = field.getName().toLowerCase(Locale.ROOT);
                if (Modifier.isStatic(field.getModifiers())
                        || (!lower.contains("mode")
                        && !lower.contains("navigation")
                        && !lower.contains("gesture")
                        && !lower.contains("overlay")
                        && !lower.contains("launcher")
                        && !lower.contains("home")
                        && !lower.contains("interaction"))) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    fields.add(field.getName() + '=' + shortValue(field.get(controller)));
                } catch (Throwable ignored) {
                    fields.add(field.getName() + "=<inaccessible>");
                }
            }
            type = type.getSuperclass();
        }
        return controller.getClass().getName() + fields;
    }

    private static boolean isObservedMethod(String methodName) {
        return Arrays.asList(OBSERVED_METHOD_NAMES).contains(methodName);
    }

    private static String describeArgs(List<Object> args) {
        if (args == null || args.isEmpty()) {
            return "[]";
        }
        List<String> values = new ArrayList<>(args.size());
        for (Object arg : args) {
            values.add(shortValue(arg));
        }
        return values.toString();
    }

    private static String shortValue(Object value) {
        if (value == null) {
            return "null";
        }
        String text;
        try {
            text = String.valueOf(value).replace('\n', ' ');
        } catch (Throwable throwable) {
            text = value.getClass().getName();
        }
        return text.length() > 180 ? text.substring(0, 180) + "…" : text;
    }

    private static Object invokeNoArg(Object target, String methodName) throws Exception {
        if (target == null) {
            return null;
        }
        Method method = findMethod(target.getClass(), methodName, 0);
        if (method == null) {
            throw new NoSuchMethodException(target.getClass().getName() + '.' + methodName);
        }
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Method findMethod(Class<?> type, String methodName, int parameterCount) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (methodName.equals(method.getName())
                        && method.getParameterCount() == parameterCount) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private void record(XposedInterface.HookHandle handle) {
        if (handle != null) {
            hookHandles.add(handle);
        }
    }

    private static final class SettingSpec {
        static final int SECURE = 0;
        static final int GLOBAL = 1;
        static final int SYSTEM = 2;

        private final int namespace;
        private final String key;

        SettingSpec(int namespace, String key) {
            this.namespace = namespace;
            this.key = key;
        }

        Uri uri() {
            if (namespace == GLOBAL) {
                return Settings.Global.getUriFor(key);
            }
            if (namespace == SYSTEM) {
                return Settings.System.getUriFor(key);
            }
            return Settings.Secure.getUriFor(key);
        }

        String read(ContentResolver resolver) {
            try {
                if (namespace == GLOBAL) {
                    return String.valueOf(Settings.Global.getString(resolver, key));
                }
                if (namespace == SYSTEM) {
                    return String.valueOf(Settings.System.getString(resolver, key));
                }
                return String.valueOf(Settings.Secure.getString(resolver, key));
            } catch (Throwable throwable) {
                return "error:" + throwable.getClass().getSimpleName();
            }
        }

        @Override
        public String toString() {
            String prefix = namespace == GLOBAL ? "global"
                    : namespace == SYSTEM ? "system" : "secure";
            return prefix + '/' + key;
        }
    }
}
