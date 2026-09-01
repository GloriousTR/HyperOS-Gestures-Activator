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

/** HyperOS gesture activation guards with persistent live diagnostics. */
public final class HgaXposedModule extends XposedModule {
    private static final String TAG = "HGA/Diagnostics";
    private static final String BUILD_MARK = "v0.2.0-gesture-activation";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String MIUI_LAUNCHER = "com.mi.android.globallauncher";
    private static final String NAVIGATION_MODE_CONTROLLER =
            "com.android.systemui.navigationbar.NavigationModeController";
    private static final String SYSTEM_UI_APPLICATION =
            "com.android.systemui.SystemUIApplication";
    private static final String PHONE_STATE_GESTURE_GUARD =
            "com.android.systemui.assist.PhoneStateMonitorController$2";
    private static final String LAUNCHER_RECENTS =
            "com.miui.home.recents.BaseRecentsImpl";
    private static final String LAUNCHER_NAV_STUB =
            "com.miui.home.recents.NavStubView";
    private static final String OVERVIEW_COMPONENT_OBSERVER =
            "com.miui.home.recents.OverviewComponentObserver";
    private static final String OVERVIEW_COMMAND_HELPER =
            "com.miui.home.recents.OverviewCommandHelper";
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
    private final List<PendingEvent> pendingEvents = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String processName = "unknown";
    private Context systemUiContext;
    private Object navigationModeController;
    private ContentObserver settingsObserver;
    private BroadcastReceiver launcherReceiver;
    private boolean systemUiHooksInstalled;
    private boolean launcherHooksInstalled;
    private boolean diagnosticsAttached;
    private boolean snapshotScheduled;

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        processName = param.getProcessName();
        recordSuccess("module", "module-loaded", "build=" + BUILD_MARK
                + ", process=" + param.getProcessName()
                + ", systemServer=" + param.isSystemServer());
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (SYSTEM_UI.equals(param.getPackageName()) && !systemUiHooksInstalled) {
            installSystemUiHooks(param);
            return;
        }
        if (MIUI_LAUNCHER.equals(param.getPackageName())
                && MIUI_LAUNCHER.equals(processName)
                && !launcherHooksInstalled) {
            installLauncherHooks(param);
        }
    }

    private void installSystemUiHooks(XposedModuleInterface.PackageLoadedParam param) {
        if (systemUiHooksInstalled) {
            return;
        }
        systemUiHooksInstalled = true;
        processName = param.getPackageName();
        ClassLoader classLoader = param.getDefaultClassLoader();
        recordSuccess("lifecycle", "systemui-package-loaded",
                "source=" + param.getApplicationInfo().sourceDir);
        installSystemUiApplicationHook(classLoader);
        installNavigationModeControllerHooks(classLoader);
        installSystemUiGestureGuard(classLoader);
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
                        try {
                            Object result = chain.proceed();
                            if (chain.getThisObject() instanceof Context) {
                                attachDiagnostics((Context) chain.getThisObject());
                                recordSuccess("lifecycle", "systemui-application-created",
                                        "SystemUIApplication.onCreate completed");
                            } else {
                                recordFailure("lifecycle", "resolve-systemui-context",
                                        "SystemUIApplication did not expose Context", null);
                            }
                            return result;
                        } catch (Throwable throwable) {
                            recordFailure("lifecycle", "systemui-application-created",
                                    "SystemUIApplication.onCreate failed", throwable);
                            throw throwable;
                        }
                    }));
            recordSuccess("hook-install", "systemui-application-on-create",
                    "Hook installed");
        } catch (Throwable throwable) {
            recordFailure("hook-install", "systemui-application-on-create",
                    "Hook installation failed", throwable);
        }
    }

    private void installSystemUiGestureGuard(ClassLoader classLoader) {
        try {
            Class<?> guardClass = Class.forName(
                    PHONE_STATE_GESTURE_GUARD, false, classLoader);
            Method run = guardClass.getDeclaredMethod("run");
            run.setAccessible(true);
            record(hook(run)
                    .setId("hga_keep_gesture_mode_for_third_party_home")
                    .intercept(chain -> {
                        Context context = systemUiContext;
                        if (context == null) {
                            context = nestedContext(chain.getThisObject());
                        }
                        if (context != null && GestureActivation.isEnabled(context)) {
                            attachEventContext(context);
                            recordSuccess(
                                    "activation",
                                    "prevent-systemui-gesture-disable",
                                    "Skipped PhoneStateMonitorController forced disable");
                            return null;
                        }
                        return chain.proceed();
                    }));
            recordSuccess(
                    "hook-install",
                    "systemui-third-party-home-guard",
                    run.toGenericString());
        } catch (Throwable throwable) {
            recordFailure(
                    "hook-install",
                    "systemui-third-party-home-guard",
                    PHONE_STATE_GESTURE_GUARD + ".run unavailable",
                    throwable);
        }
    }

    private void installLauncherHooks(XposedModuleInterface.PackageLoadedParam param) {
        launcherHooksInstalled = true;
        processName = MIUI_LAUNCHER;
        ClassLoader classLoader = param.getDefaultClassLoader();
        recordSuccess("lifecycle", "launcher-package-loaded",
                "source=" + param.getApplicationInfo().sourceDir);
        try {
            Class<?> recentsClass = Class.forName(LAUNCHER_RECENTS, false, classLoader);
            Method setDefaultHome = recentsClass.getDeclaredMethod(
                    "setIsUseMiuiHomeAsDefaultHome", boolean.class);
            setDefaultHome.setAccessible(true);
            record(hook(setDefaultHome)
                    .setId("hga_keep_xiaomi_gesture_engine")
                    .intercept(chain -> {
                        Context context = directContext(chain.getThisObject(), "mContext");
                        if (context != null) {
                            attachEventContext(context);
                            boolean ready = GestureActivation.markLauncherReady(context);
                            if (!ready) {
                                recordFailure(
                                        "activation",
                                        "mark-launcher-hook-ready",
                                        "Unable to write launcher readiness token",
                                        null);
                            }
                        }
                        boolean requested = Boolean.TRUE.equals(chain.getArg(0));
                        if (context != null
                                && GestureActivation.isEnabled(context)
                                && !requested) {
                            recordInfo(
                                    "activation",
                                    "override-launcher-gesture-eligibility",
                                    "Changing isUseMiuiHomeAsDefaultHome false -> true");
                            Object result = chain.proceed(new Object[]{true});
                            recordSuccess(
                                    "activation",
                                    "keep-xiaomi-gesture-engine",
                                    "Launcher gesture windows remain eligible");
                            return result;
                        }
                        return chain.proceed();
                    }));
            recordSuccess(
                    "hook-install",
                    "launcher-gesture-engine-guard",
                    setDefaultHome.toGenericString());

            Method updateFsgWindowState = recentsClass.getDeclaredMethod(
                    "updateFsgWindowState");
            updateFsgWindowState.setAccessible(true);
            record(hook(updateFsgWindowState)
                    .setId("hga_refresh_xiaomi_gesture_windows")
                    .intercept(chain -> {
                        Context context = directContext(chain.getThisObject(), "mContext");
                        if (context != null) {
                            attachEventContext(context);
                        }
                        if (context != null && GestureActivation.isEnabled(context)) {
                            if (writeBooleanField(
                                    chain.getThisObject(),
                                    "mIsUseMiuiHomeAsDefaultHome",
                                    true)) {
                                recordInfo(
                                        "activation",
                                        "refresh-launcher-gesture-eligibility",
                                        "Set mIsUseMiuiHomeAsDefaultHome=true before window refresh");
                            } else {
                                recordFailure(
                                        "activation",
                                        "refresh-launcher-gesture-eligibility",
                                        "Unable to update launcher eligibility field",
                                        null);
                            }
                        }
                        Object result = chain.proceed();
                        if (context != null && GestureActivation.isEnabled(context)) {
                            recordSuccess(
                                    "activation",
                                    "refresh-xiaomi-gesture-windows",
                                    "Launcher gesture window state refreshed");
                        }
                        return result;
                    }));
            recordSuccess(
                    "hook-install",
                    "launcher-gesture-window-refresh",
                    updateFsgWindowState.toGenericString());
        } catch (Throwable throwable) {
            recordFailure(
                    "hook-install",
                    "launcher-gesture-engine-guard",
                    LAUNCHER_RECENTS + " method unavailable",
                    throwable);
        }
        installLauncherOverviewHook(classLoader);
    }

    private void installLauncherOverviewHook(ClassLoader classLoader) {
        try {
            Class<?> navStubClass = Class.forName(
                    LAUNCHER_NAV_STUB, false, classLoader);
            Class<?> observerClass = Class.forName(
                    OVERVIEW_COMPONENT_OBSERVER, false, classLoader);
            Class<?> commandClass = Class.forName(
                    OVERVIEW_COMMAND_HELPER, false, classLoader);

            Method performAppToRecents = navStubClass.getDeclaredMethod(
                    "performAppToRecents", boolean.class);
            Method performAppToHome = navStubClass.getDeclaredMethod(
                    "performAppToHome");
            Method finishDirectly = navStubClass.getDeclaredMethod(
                    "finishDirectly", boolean.class);
            Method finishRecentsActivityDirectly = navStubClass.getDeclaredMethod(
                    "finishRecentsActivityDirectly");
            Method getObserver = observerClass.getDeclaredMethod(
                    "getInstance", Context.class);
            Method updateTargets = observerClass.getDeclaredMethod(
                    "updateOverviewTargets");
            Method getHomeIntent = observerClass.getDeclaredMethod(
                    "getHomeIntent");
            Constructor<?> commandConstructor = commandClass.getDeclaredConstructor(
                    Context.class, observerClass);
            Method onOverviewToggle = commandClass.getDeclaredMethod(
                    "onOverviewToggle");
            performAppToRecents.setAccessible(true);
            performAppToHome.setAccessible(true);
            finishDirectly.setAccessible(true);
            finishRecentsActivityDirectly.setAccessible(true);
            getObserver.setAccessible(true);
            updateTargets.setAccessible(true);
            getHomeIntent.setAccessible(true);
            commandConstructor.setAccessible(true);
            onOverviewToggle.setAccessible(true);

            record(hook(performAppToRecents)
                    .setId("hga_route_third_party_overview")
                    .intercept(chain -> {
                        Object navStub = chain.getThisObject();
                        Context context = directContext(navStub, "mContext");
                        if (context == null
                                || !GestureActivation.isEnabled(context)
                                || readField(navStub, "mLauncher") != null) {
                            return chain.proceed();
                        }
                        attachEventContext(context);
                        try {
                            Object observer = getObserver.invoke(null, context);
                            updateTargets.invoke(observer);
                            Object command = commandConstructor.newInstance(
                                    context, observer);
                            finishDirectly.invoke(navStub, false);
                            finishRecentsActivityDirectly.invoke(navStub);
                            onOverviewToggle.invoke(command);
                            recordSuccess(
                                    "activation",
                                    "route-third-party-overview",
                                    "Gesture release dispatched to Xiaomi fallback RecentsActivity");
                            return null;
                        } catch (Throwable throwable) {
                            recordFailure(
                                    "activation",
                                    "route-third-party-overview",
                                    "Official Overview fallback dispatch failed",
                                    throwable);
                            return chain.proceed();
                        }
                    }));
            recordSuccess(
                    "hook-install",
                    "launcher-third-party-overview-route",
                    performAppToRecents.toGenericString());

            record(hook(performAppToHome)
                    .setId("hga_route_third_party_home")
                    .intercept(chain -> {
                        Object navStub = chain.getThisObject();
                        Context context = directContext(navStub, "mContext");
                        if (context == null
                                || !GestureActivation.isEnabled(context)
                                || readField(navStub, "mLauncher") != null) {
                            return chain.proceed();
                        }
                        attachEventContext(context);
                        try {
                            Object observer = getObserver.invoke(null, context);
                            updateTargets.invoke(observer);
                            Intent observedHomeIntent = (Intent) getHomeIntent.invoke(observer);
                            if (observedHomeIntent == null) {
                                recordFailure(
                                        "activation",
                                        "route-third-party-home",
                                        "Overview observer returned no default HOME intent",
                                        null);
                                return chain.proceed();
                            }
                            Intent homeIntent = new Intent(observedHomeIntent)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                            finishDirectly.invoke(navStub, false);
                            finishRecentsActivityDirectly.invoke(navStub);
                            context.startActivity(homeIntent);
                            recordSuccess(
                                    "activation",
                                    "route-third-party-home",
                                    "Gesture release dispatched to observed default HOME: "
                                            + homeIntent.getComponent());
                            return null;
                        } catch (Throwable throwable) {
                            recordFailure(
                                    "activation",
                                    "route-third-party-home",
                                    "Observed default HOME dispatch failed",
                                    throwable);
                            return chain.proceed();
                        }
                    }));
            recordSuccess(
                    "hook-install",
                    "launcher-third-party-home-route",
                    performAppToHome.toGenericString());
        } catch (Throwable throwable) {
            recordFailure(
                    "hook-install",
                    "launcher-third-party-overview-route",
                    LAUNCHER_NAV_STUB + " Overview route unavailable",
                    throwable);
        }
    }

    private void installNavigationModeControllerHooks(ClassLoader classLoader) {
        try {
            Class<?> controllerClass = Class.forName(
                    NAVIGATION_MODE_CONTROLLER, false, classLoader);
            int constructorIndex = 0;
            int installedConstructors = 0;
            for (Constructor<?> constructor : controllerClass.getDeclaredConstructors()) {
                final int index = constructorIndex++;
                try {
                    constructor.setAccessible(true);
                    record(hook(constructor)
                            .setId("hga_navigation_controller_ctor_" + index)
                            .intercept(chain -> {
                                try {
                                    Object result = chain.proceed();
                                    navigationModeController = chain.getThisObject();
                                    recordSuccess("hook-event",
                                            "navigation-controller-constructed",
                                            describeController(navigationModeController));
                                    scheduleSnapshot("controller-constructed");
                                    return result;
                                } catch (Throwable throwable) {
                                    recordFailure("hook-event",
                                            "navigation-controller-constructed",
                                            "Constructor failed", throwable);
                                    throw throwable;
                                }
                            }));
                    installedConstructors++;
                    recordSuccess("hook-install", "navigation-controller-constructor-" + index,
                            constructor.toGenericString());
                } catch (Throwable throwable) {
                    recordFailure("hook-install", "navigation-controller-constructor-" + index,
                            constructor.toGenericString(), throwable);
                }
            }

            int observedCount = 0;
            int installedMethods = 0;
            for (Method method : controllerClass.getDeclaredMethods()) {
                if (!isObservedMethod(method.getName())) {
                    continue;
                }
                String hookId = "hga_navigation_" + method.getName()
                        + "_" + observedCount++;
                try {
                    method.setAccessible(true);
                    record(hook(method)
                            .setId(hookId)
                            .intercept(chain -> traceNavigationDecision(chain, hookId)));
                    installedMethods++;
                    recordSuccess("hook-install", hookId, method.toGenericString());
                } catch (Throwable throwable) {
                    recordFailure("hook-install", hookId, method.toGenericString(), throwable);
                }
            }
            String summary = "NavigationModeController diagnostics"
                    + ", constructors=" + installedConstructors + '/' + constructorIndex
                    + ", methods=" + installedMethods + '/' + observedCount;
            if (constructorIndex > 0
                    && observedCount > 0
                    && installedConstructors == constructorIndex
                    && installedMethods == observedCount) {
                recordSuccess("hook-install", "navigation-mode-controller", summary);
            } else {
                recordFailure("hook-install", "navigation-mode-controller", summary, null);
            }
        } catch (Throwable throwable) {
            recordFailure("hook-install", "navigation-mode-controller",
                    "NavigationModeController unavailable", throwable);
        }
    }

    private Object traceNavigationDecision(XposedInterface.Chain chain, String hookId)
            throws Throwable {
        recordInfo("hook-event", hookId + ":before",
                "args=" + describeArgs(chain.getArgs())
                        + ", controller=" + describeController(chain.getThisObject()));
        try {
            Object result = chain.proceed();
            navigationModeController = chain.getThisObject();
            recordSuccess("hook-event", hookId + ":after",
                    "result=" + shortValue(result)
                            + ", controller=" + describeController(navigationModeController));
            scheduleSnapshot(hookId);
            return result;
        } catch (Throwable throwable) {
            recordFailure("hook-event", hookId + ":after",
                    "SystemUI method threw after args=" + describeArgs(chain.getArgs()),
                    throwable);
            throw throwable;
        }
    }

    private void attachDiagnostics(Context context) {
        if (diagnosticsAttached) {
            return;
        }
        diagnosticsAttached = true;
        systemUiContext = context.getApplicationContext() == null
                ? context : context.getApplicationContext();
        flushPendingEvents();
        if (GestureActivation.markSystemUiReady(systemUiContext)) {
            recordSuccess(
                    "activation",
                    "mark-systemui-hook-ready",
                    "SystemUI activation guard is ready");
        } else {
            recordFailure(
                    "activation",
                    "mark-systemui-hook-ready",
                    "Unable to write SystemUI readiness token",
                    null);
        }
        if (GestureActivation.isEnabled(systemUiContext)) {
            boolean restored = GestureActivation.writeGlobalInt(
                    systemUiContext,
                    GestureActivation.KEY_FORCE_FSG_NAV_BAR,
                    1);
            if (restored) {
                recordSuccess(
                        "activation",
                        "restore-gesture-mode-on-systemui-start",
                        "force_fsg_nav_bar=1");
            } else {
                recordFailure(
                        "activation",
                        "restore-gesture-mode-on-systemui-start",
                        "Unable to restore force_fsg_nav_bar",
                        null);
            }
        }
        registerSettingsObservers();
        registerLauncherReceiver();
        recordSuccess("diagnostics", "attach-systemui",
                "Read-only diagnostics attached to SystemUI");
        scheduleSnapshot("systemui-created");
    }

    private void attachEventContext(Context context) {
        if (context == null || systemUiContext != null) {
            return;
        }
        systemUiContext = context.getApplicationContext() == null
                ? context : context.getApplicationContext();
        flushPendingEvents();
    }

    private void registerSettingsObservers() {
        settingsObserver = new ContentObserver(mainHandler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                recordInfo("settings", "setting-changed", "uri=" + uri);
                scheduleSnapshot("setting-changed:" + uri);
            }
        };
        ContentResolver resolver = systemUiContext.getContentResolver();
        for (SettingSpec setting : OBSERVED_SETTINGS) {
            try {
                resolver.registerContentObserver(setting.uri(), false, settingsObserver);
                recordSuccess("settings", "observe-setting", setting.toString());
            } catch (Throwable throwable) {
                recordFailure("settings", "observe-setting", setting.toString(), throwable);
            }
        }
    }

    private void registerLauncherReceiver() {
        launcherReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent == null ? "null" : intent.getAction();
                recordInfo("launcher", "launcher-related-broadcast", action);
                scheduleSnapshot("broadcast:" + action);
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_PREFERRED_ACTIVITY_CHANGED);
        try {
            systemUiContext.registerReceiver(
                    launcherReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            recordSuccess("launcher", "register-launcher-receiver",
                    ACTION_PREFERRED_ACTIVITY_CHANGED);
        } catch (Throwable throwable) {
            recordFailure("launcher", "register-launcher-receiver",
                    ACTION_PREFERRED_ACTIVITY_CHANGED, throwable);
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
            String home = resolveDefaultHome(systemUiContext);
            String settings = readSettings(systemUiContext.getContentResolver());
            String overlays = readNavigationOverlays(systemUiContext);
            String detail = "reason=" + reason
                    + " | home=" + home
                    + " | settings=" + settings
                    + " | overlays=" + overlays
                    + " | controller=" + describeController(navigationModeController);
            if (home.startsWith("error:")
                    || "unresolved".equals(home)
                    || settings.contains("error:")
                    || overlays.startsWith("error:")
                    || overlays.endsWith("-null")) {
                recordFailure("snapshot", "capture-navigation-state", detail, null);
            } else {
                recordSuccess("snapshot", "capture-navigation-state", detail);
            }
        } catch (Throwable throwable) {
            recordFailure("snapshot", "capture-navigation-state",
                    "reason=" + reason, throwable);
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
                Object state = invokeOptionalNoArg(info, "getState", "unavailable");
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

    private static Object invokeOptionalNoArg(
            Object target,
            String methodName,
            Object fallback) {
        try {
            return invokeNoArg(target, methodName);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static Context nestedContext(Object target) {
        Object outer = readField(target, "this$0");
        return directContext(outer, "mContext");
    }

    private static Context directContext(Object target, String fieldName) {
        Object value = readField(target, fieldName);
        return value instanceof Context ? (Context) value : null;
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean writeBooleanField(
            Object target,
            String fieldName,
            boolean value) {
        if (target == null) {
            return false;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.setBoolean(target, value);
                return true;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
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

    private void recordInfo(String category, String operation, String detail) {
        recordEvent(DiagnosticEvent.STATUS_INFO, category, operation, detail, null);
    }

    private void recordSuccess(String category, String operation, String detail) {
        recordEvent(DiagnosticEvent.STATUS_SUCCESS, category, operation, detail, null);
    }

    private void recordFailure(
            String category,
            String operation,
            String detail,
            Throwable throwable) {
        recordEvent(DiagnosticEvent.STATUS_FAILURE, category, operation, detail, throwable);
    }

    private void recordEvent(
            String status,
            String category,
            String operation,
            String detail,
            Throwable throwable) {
        String completeDetail = detail == null ? "" : detail;
        if (throwable != null) {
            completeDetail += "\n" + Log.getStackTraceString(throwable);
        }
        String message = status + " " + category + '/' + operation + ": " + completeDetail;
        if (throwable != null) {
            log(Log.ERROR, TAG, message, throwable);
        } else if (DiagnosticEvent.STATUS_FAILURE.equals(status)) {
            log(Log.ERROR, TAG, message);
        } else {
            log(Log.INFO, TAG, message);
        }

        Context context = systemUiContext;
        if (context == null) {
            synchronized (pendingEvents) {
                pendingEvents.add(new PendingEvent(
                        status, category, operation, completeDetail, processName));
            }
            return;
        }
        sendEvent(context, new PendingEvent(
                status, category, operation, completeDetail, processName));
    }

    private void flushPendingEvents() {
        List<PendingEvent> events;
        synchronized (pendingEvents) {
            events = new ArrayList<>(pendingEvents);
            pendingEvents.clear();
        }
        for (PendingEvent event : events) {
            sendEvent(systemUiContext, event);
        }
    }

    private void sendEvent(Context context, PendingEvent event) {
        try {
            DiagnosticEventReporter.send(
                    context,
                    event.status,
                    event.category,
                    event.operation,
                    event.detail,
                    event.processName);
        } catch (Throwable throwable) {
            log(Log.ERROR, TAG, "Unable to deliver live diagnostic event "
                    + event.category + '/' + event.operation, throwable);
        }
    }

    private static final class PendingEvent {
        final String status;
        final String category;
        final String operation;
        final String detail;
        final String processName;

        PendingEvent(
                String status,
                String category,
                String operation,
                String detail,
                String processName) {
            this.status = status;
            this.category = category;
            this.operation = operation;
            this.detail = detail;
            this.processName = processName;
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
