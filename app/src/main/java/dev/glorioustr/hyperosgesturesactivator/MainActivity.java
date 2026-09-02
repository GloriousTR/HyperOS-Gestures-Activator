package dev.glorioustr.hyperosgesturesactivator;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final long STATUS_REFRESH_INTERVAL_MS = 1000L;

    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusRefresh = new Runnable() {
        @Override
        public void run() {
            updateDashboard();
            statusHandler.postDelayed(this, STATUS_REFRESH_INTERVAL_MS);
        }
    };

    private DiagnosticDatabase database;
    private LinearLayout statusCard;
    private TextView statusBadge;
    private TextView statusTitle;
    private TextView statusDetail;
    private Button primaryButton;
    private TextView systemUiHealth;
    private TextView launcherHealth;
    private TextView navigationHealth;
    private TextView diagnosticsSummary;
    private TextView defaultHomeView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        database = DiagnosticDatabase.get(this);
        setTitle(R.string.app_name);
        setContentView(buildScreen());
        recordAppEvent(
                DiagnosticEvent.STATUS_SUCCESS,
                "ui",
                "dashboard-opened",
                buildDeviceSummary());
    }

    @Override
    protected void onStart() {
        super.onStart();
        statusHandler.removeCallbacks(statusRefresh);
        statusHandler.post(statusRefresh);
    }

    @Override
    protected void onStop() {
        statusHandler.removeCallbacks(statusRefresh);
        super.onStop();
    }

    private View buildScreen() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(246, 248, 252));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(28));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            view.setPadding(
                    dp(20) + bars.left,
                    dp(18) + bars.top,
                    dp(20) + bars.right,
                    dp(28) + bars.bottom);
            return insets;
        });
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(buildTopBar(), matchWrap());
        root.addView(space(24));
        root.addView(buildStatusCard(), matchWrap());
        root.addView(space(24));
        root.addView(sectionTitle("Sistem sağlığı"), matchWrap());
        root.addView(space(10));
        root.addView(buildHealthCard(), matchWrap());
        root.addView(space(24));
        root.addView(sectionTitle("Hareketler"), matchWrap());
        root.addView(space(10));
        root.addView(buildGestureCard(), matchWrap());
        root.addView(space(24));
        root.addView(sectionTitle("Sistem araçları"), matchWrap());
        root.addView(space(10));
        root.addView(buildDiagnosticsMenu(), matchWrap());
        root.addView(space(22));

        TextView footer = text(
                "v" + BuildConfig.VERSION_NAME + "  •  "
                        + Build.MANUFACTURER + " " + Build.MODEL,
                12,
                Color.rgb(105, 113, 128),
                Typeface.NORMAL);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, matchWrap());
        return scrollView;
    }

    private View buildTopBar() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("HyperOS Gestures", 25,
                Color.rgb(22, 30, 45), Typeface.BOLD);
        TextView subtitle = text("Navigation Control Center", 13,
                Color.rgb(105, 113, 128), Typeface.NORMAL);
        subtitle.setPadding(0, dp(2), 0, 0);
        titles.addView(title, matchWrap());
        titles.addView(subtitle, matchWrap());
        row.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView menu = text("⋮", 28, Color.rgb(42, 54, 74), Typeface.BOLD);
        menu.setGravity(Gravity.CENTER);
        menu.setContentDescription("Menü");
        menu.setClickable(true);
        menu.setFocusable(true);
        menu.setBackground(rounded(Color.WHITE, 16, Color.rgb(224, 229, 238), 1));
        menu.setOnClickListener(this::showMainMenu);
        row.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return row;
    }

    private View buildStatusCard() {
        statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(20), dp(20), dp(20), dp(20));
        statusCard.setElevation(dp(2));

        statusBadge = text("DURUM", 11, Color.rgb(35, 85, 62), Typeface.BOLD);
        statusBadge.setLetterSpacing(0.12f);
        statusCard.addView(statusBadge, matchWrap());

        statusTitle = text("Kontrol ediliyor…", 26,
                Color.rgb(18, 46, 32), Typeface.BOLD);
        statusTitle.setPadding(0, dp(8), 0, 0);
        statusCard.addView(statusTitle, matchWrap());

        statusDetail = text("Sistem durumu okunuyor.", 14,
                Color.rgb(62, 82, 70), Typeface.NORMAL);
        statusDetail.setPadding(0, dp(6), 0, dp(18));
        statusCard.addView(statusDetail, matchWrap());

        primaryButton = new Button(this);
        primaryButton.setAllCaps(false);
        primaryButton.setTextSize(15);
        primaryButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        primaryButton.setMinHeight(dp(52));
        primaryButton.setOnClickListener(view ->
                setGestureActivation(!GestureActivation.isEnabled(this)));
        statusCard.addView(primaryButton, matchWrap());

        TextView safety = text(
                "Güvenli kapatma, önceki gezinme ayarını otomatik geri yükler.",
                11,
                Color.rgb(84, 101, 91),
                Typeface.NORMAL);
        safety.setGravity(Gravity.CENTER);
        safety.setPadding(0, dp(12), 0, 0);
        statusCard.addView(safety, matchWrap());
        return statusCard;
    }

    private View buildHealthCard() {
        LinearLayout card = card();
        systemUiHealth = addHealthRow(card, "SystemUI", "Kontrol ediliyor…", false);
        launcherHealth = addHealthRow(card, "Xiaomi Launcher motoru", "Kontrol ediliyor…", true);
        navigationHealth = addHealthRow(card, "Gezinme modu", "Kontrol ediliyor…", true);
        defaultHomeView = addHealthRow(card, "Varsayılan ana ekran", "Kontrol ediliyor…", true);
        return card;
    }

    private View buildGestureCard() {
        LinearLayout card = card();
        addGestureRow(card, "‹", "Geri", "Sol veya sağ kenardan içeri kaydır", false);
        addGestureRow(card, "⌃", "Ana ekran", "Alt kenardan hızlıca yukarı kaydır", true);
        addGestureRow(card, "▤", "Son uygulamalar", "Yukarı kaydır ve kısa süre tut", true);
        return card;
    }

    private View buildDiagnosticsMenu() {
        LinearLayout card = card();
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription("Live Diagnostics ekranını aç");
        card.setOnClickListener(view -> openDiagnostics());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        TextView icon = text("◉", 22, Color.rgb(71, 83, 160), Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(rounded(Color.rgb(235, 238, 255), 14, Color.TRANSPARENT, 0));
        row.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.setMarginStart(dp(14));
        row.addView(copy, copyParams);

        copy.addView(text("Live Diagnostics", 16,
                Color.rgb(26, 35, 52), Typeface.BOLD), matchWrap());
        diagnosticsSummary = text("Olay özeti yükleniyor…", 12,
                Color.rgb(105, 113, 128), Typeface.NORMAL);
        diagnosticsSummary.setPadding(0, dp(3), 0, 0);
        copy.addView(diagnosticsSummary, matchWrap());

        TextView arrow = text("›", 28, Color.rgb(105, 113, 128), Typeface.NORMAL);
        row.addView(arrow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(row, matchWrap());
        return card;
    }

    private TextView addHealthRow(
            LinearLayout parent,
            String label,
            String value,
            boolean divider) {
        if (divider) {
            parent.addView(divider(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(13), 0, dp(13));
        TextView labelView = text(label, 14,
                Color.rgb(54, 64, 82), Typeface.NORMAL);
        row.addView(labelView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView valueView = text(value, 13,
                Color.rgb(105, 113, 128), Typeface.BOLD);
        valueView.setGravity(Gravity.END);
        row.addView(valueView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        parent.addView(row, matchWrap());
        return valueView;
    }

    private void addGestureRow(
            LinearLayout parent,
            String symbol,
            String title,
            String detail,
            boolean divider) {
        if (divider) {
            parent.addView(divider(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));

        TextView icon = text(symbol, 22, Color.rgb(67, 79, 146), Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(rounded(Color.rgb(239, 241, 255), 13, Color.TRANSPARENT, 0));
        row.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMarginStart(dp(14));
        row.addView(copy, params);
        copy.addView(text(title, 14, Color.rgb(34, 43, 60), Typeface.BOLD), matchWrap());
        TextView detailView = text(detail, 12,
                Color.rgb(105, 113, 128), Typeface.NORMAL);
        detailView.setPadding(0, dp(2), 0, 0);
        copy.addView(detailView, matchWrap());
        parent.addView(row, matchWrap());
    }

    private void showMainMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Live Diagnostics");
        menu.getMenu().add(0, 2, 1, "Anlık sistem kaydı");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                openDiagnostics();
                return true;
            }
            if (item.getItemId() == 2) {
                captureSnapshot("dashboard-menu");
                Toast.makeText(this, "Sistem durumu kaydedildi", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void openDiagnostics() {
        startActivity(new Intent(this, DiagnosticsActivity.class));
    }

    private void updateDashboard() {
        boolean permissionGranted = checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
        boolean enabled = GestureActivation.isEnabled(this);
        boolean systemUiReady = GestureActivation.isSystemUiReady(this);
        boolean launcherReady = GestureActivation.isLauncherReady(this);
        int forceFsg = GestureActivation.readGlobalInt(
                this, GestureActivation.KEY_FORCE_FSG_NAV_BAR, 0);
        int navigationMode = readSecureInt(GestureActivation.KEY_NAVIGATION_MODE, -1);
        boolean fullyActive = enabled && forceFsg == 1 && navigationMode == 2;

        if (!permissionGranted) {
            applyStatus(
                    "YETKİ GEREKLİ",
                    "Aktivasyon izni verilmemiş",
                    "Sistem gezinme modunu değiştirmek için gerekli izin eksik.",
                    Color.rgb(255, 239, 237),
                    Color.rgb(145, 28, 28));
            primaryButton.setText("İzin durumunu kontrol et");
            primaryButton.setTextColor(Color.WHITE);
            primaryButton.setBackground(rounded(
                    Color.rgb(174, 39, 39), 14, Color.TRANSPARENT, 0));
        } else if (fullyActive) {
            applyStatus(
                    "AKTİF",
                    "Hareketle gezinme açık",
                    "HyperOS hareket motoru Smart Launcher ile kullanıma hazır.",
                    Color.rgb(232, 247, 239),
                    Color.rgb(20, 105, 60));
            primaryButton.setText("Güvenli şekilde kapat");
            primaryButton.setTextColor(Color.rgb(136, 31, 31));
            primaryButton.setBackground(rounded(
                    Color.rgb(255, 246, 245), 14, Color.rgb(231, 188, 184), 1));
        } else if (enabled) {
            applyStatus(
                    "BAŞLATILIYOR",
                    "Sistem geçişi bekleniyor",
                    "SystemUI ve Launcher gezinme modunu hazırlıyor.",
                    Color.rgb(255, 246, 226),
                    Color.rgb(137, 81, 0));
            primaryButton.setText("Güvenli şekilde kapat");
            primaryButton.setTextColor(Color.rgb(120, 67, 0));
            primaryButton.setBackground(rounded(
                    Color.rgb(255, 250, 240), 14, Color.rgb(231, 205, 149), 1));
        } else {
            applyStatus(
                    "KAPALI",
                    "Hareketle gezinme kapalı",
                    "Hazır olduğunda tek dokunuşla güvenle etkinleştirebilirsin.",
                    Color.WHITE,
                    Color.rgb(50, 61, 80));
            primaryButton.setText("Hareketleri etkinleştir");
            primaryButton.setTextColor(Color.WHITE);
            primaryButton.setBackground(rounded(
                    Color.rgb(68, 82, 160), 14, Color.TRANSPARENT, 0));
        }

        bindHealth(systemUiHealth, systemUiReady, "Hazır", "Bekleniyor");
        bindHealth(launcherHealth, launcherReady, "Hazır", "Bekleniyor");
        boolean navigationReady = forceFsg == 1 && navigationMode == 2;
        bindHealth(navigationHealth, navigationReady,
                "Gesture (2)", enabled ? "Geçişte" : "Kapalı");
        defaultHomeView.setText(shortHome(resolveDefaultHome()));
        defaultHomeView.setTextColor(Color.rgb(62, 72, 90));

        try {
            DiagnosticDatabase.Counts counts = database.counts();
            diagnosticsSummary.setText(counts.total + " olay  •  "
                    + counts.success + " başarılı  •  " + counts.failure + " hata");
        } catch (Throwable throwable) {
            diagnosticsSummary.setText("Olay özeti okunamadı");
        }
    }

    private void applyStatus(
            String badge,
            String title,
            String detail,
            int background,
            int accent) {
        statusCard.setBackground(rounded(background, 22,
                Color.argb(35, Color.red(accent), Color.green(accent), Color.blue(accent)), 1));
        statusBadge.setText(badge);
        statusBadge.setTextColor(accent);
        statusTitle.setText(title);
        statusTitle.setTextColor(accent);
        statusDetail.setText(detail);
        statusDetail.setTextColor(Color.rgb(71, 82, 75));
    }

    private void bindHealth(
            TextView view,
            boolean healthy,
            String healthyText,
            String waitingText) {
        view.setText((healthy ? "● " : "○ ") + (healthy ? healthyText : waitingText));
        view.setTextColor(healthy
                ? Color.rgb(20, 112, 65)
                : Color.rgb(145, 94, 15));
    }

    private void setGestureActivation(boolean enabled) {
        if (checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            recordAppEvent(
                    DiagnosticEvent.STATUS_FAILURE,
                    "activation",
                    "change-gesture-activation",
                    "WRITE_SECURE_SETTINGS permission missing");
            new AlertDialog.Builder(this)
                    .setTitle("Aktivasyon izni gerekli")
                    .setMessage("Uygulamanın sistem gezinme modunu güvenli biçimde değiştirme izni yok.")
                    .setPositiveButton("Tamam", null)
                    .show();
            return;
        }
        if (enabled
                && (!GestureActivation.isSystemUiReady(this)
                || !GestureActivation.isLauncherReady(this))) {
            recordAppEvent(
                    DiagnosticEvent.STATUS_FAILURE,
                    "activation",
                    "enable-gesture-navigation",
                    "Hooks not ready: systemUi=" + GestureActivation.isSystemUiReady(this)
                            + ", launcher=" + GestureActivation.isLauncherReady(this));
            new AlertDialog.Builder(this)
                    .setTitle("Modül henüz hazır değil")
                    .setMessage("SystemUI ve Xiaomi Launcher hazır olmadan gezinme çubuğu gizlenmeyecek.")
                    .setPositiveButton("Tamam", null)
                    .show();
            return;
        }

        SharedPreferences preferences = createDeviceProtectedStorageContext()
                .getSharedPreferences("gesture_activation", MODE_PRIVATE);
        boolean wasEnabled = GestureActivation.isEnabled(this);
        if (enabled && !wasEnabled) {
            preferences.edit().putInt(
                    "previous_force_fsg_nav_bar",
                    GestureActivation.readGlobalInt(
                            this, GestureActivation.KEY_FORCE_FSG_NAV_BAR, 0)).apply();
        }
        int forceValue = enabled
                ? 1 : preferences.getInt("previous_force_fsg_nav_bar", 0);
        boolean activationWritten = GestureActivation.writeGlobalInt(
                this, GestureActivation.KEY_ENABLED, enabled ? 1 : 0);
        boolean forceWritten = GestureActivation.writeGlobalInt(
                this, GestureActivation.KEY_FORCE_FSG_NAV_BAR, forceValue);
        String operation = enabled
                ? "enable-gesture-navigation" : "disable-gesture-navigation";
        if (activationWritten && forceWritten) {
            recordAppEvent(
                    DiagnosticEvent.STATUS_SUCCESS,
                    "activation",
                    operation,
                    "enabled=" + enabled + " | force_fsg_nav_bar=" + forceValue);
        } else {
            recordAppEvent(
                    DiagnosticEvent.STATUS_FAILURE,
                    "activation",
                    operation,
                    "activationWritten=" + activationWritten
                            + " | forceWritten=" + forceWritten);
        }
        updateDashboard();
        statusHandler.postDelayed(() -> captureSnapshot(
                "activation-settled:" + enabled), 1500L);
    }

    private void captureSnapshot(String reason) {
        String detail = "reason=" + reason
                + " | " + buildDeviceSummary()
                + " | defaultHome=" + resolveDefaultHome()
                + " | activation=" + GestureActivation.isEnabled(this)
                + " | hooks={systemUi=" + GestureActivation.isSystemUiReady(this)
                + ",launcher=" + GestureActivation.isLauncherReady(this) + "}"
                + " | global/force_fsg_nav_bar="
                + GestureActivation.readGlobalInt(
                        this, GestureActivation.KEY_FORCE_FSG_NAV_BAR, -1)
                + " | secure/navigation_mode=" + readSecureInt("navigation_mode", -1);
        recordAppEvent(
                DiagnosticEvent.STATUS_SUCCESS,
                "snapshot",
                "capture-dashboard-state",
                detail);
        updateDashboard();
    }

    private void recordAppEvent(
            String status,
            String category,
            String operation,
            String detail) {
        database.insert(new DiagnosticEvent(
                0L,
                System.currentTimeMillis(),
                status,
                category,
                operation,
                detail,
                getPackageName(),
                Thread.currentThread().getName()));
    }

    private String buildDeviceSummary() {
        return "HGA=" + BuildConfig.VERSION_NAME
                + " | device=" + Build.MANUFACTURER + ' ' + Build.MODEL
                + " | Android=" + Build.VERSION.RELEASE
                + " | API=" + Build.VERSION.SDK_INT
                + " | build=" + Build.DISPLAY;
    }

    private String resolveDefaultHome() {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo resolved = getPackageManager().resolveActivity(
                    intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolved == null || resolved.activityInfo == null) {
                return "Çözümlenemedi";
            }
            return new ComponentName(
                    resolved.activityInfo.packageName,
                    resolved.activityInfo.name).flattenToShortString();
        } catch (Throwable throwable) {
            return "Okunamadı";
        }
    }

    private String shortHome(String home) {
        if (home.startsWith("ginlemon.flowerfree")) {
            return "Smart Launcher";
        }
        if (home.startsWith("com.mi.android.globallauncher")) {
            return "Xiaomi Launcher";
        }
        int slash = home.indexOf('/');
        return slash > 0 ? home.substring(0, slash) : home;
    }

    private int readSecureInt(String key, int fallback) {
        try {
            return Settings.Secure.getInt(getContentResolver(), key, fallback);
        } catch (Throwable throwable) {
            return fallback;
        }
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(4), dp(16), dp(4));
        card.setBackground(rounded(Color.WHITE, 18,
                Color.rgb(228, 232, 240), 1));
        card.setElevation(dp(1));
        return card;
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 14, Color.rgb(67, 77, 95), Typeface.BOLD);
        view.setLetterSpacing(0.04f);
        return view;
    }

    private TextView text(String value, int size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(235, 238, 244));
        return divider;
    }

    private View space(int height) {
        View space = new View(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(height)));
        return space;
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) {
            drawable.setStroke(dp(strokeWidth), strokeColor);
        }
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
