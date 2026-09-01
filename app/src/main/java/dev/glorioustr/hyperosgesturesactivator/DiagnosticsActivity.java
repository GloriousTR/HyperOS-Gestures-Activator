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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class DiagnosticsActivity extends Activity {
    private static final long LIVE_REFRESH_INTERVAL_MS = 750L;

    private final Handler liveHandler = new Handler(Looper.getMainLooper());
    private final EventAdapter eventAdapter = new EventAdapter();
    private final Runnable liveRefresh = new Runnable() {
        @Override
        public void run() {
            updateActivationState();
            reloadEvents();
            liveHandler.postDelayed(this, LIVE_REFRESH_INTERVAL_MS);
        }
    };

    private DiagnosticDatabase database;
    private TextView liveStateView;
    private TextView activationStateView;
    private TextView summaryView;
    private TextView filterView;
    private ListView eventList;
    private String activeFilter;
    private long renderedTotal = -1L;
    private String renderedFilter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        database = DiagnosticDatabase.get(this);
        setTitle(R.string.screen_title);
        setContentView(buildScreen());
        recordAppEvent(
                DiagnosticEvent.STATUS_SUCCESS,
                "ui",
                "live-diagnostics-opened",
                buildDeviceSummary());
        captureAppSnapshot("screen-opened");
    }

    @Override
    protected void onStart() {
        super.onStart();
        liveStateView.setText("● CANLI — olaylar izleniyor");
        liveStateView.setTextColor(Color.rgb(20, 125, 70));
        liveHandler.removeCallbacks(liveRefresh);
        liveHandler.post(liveRefresh);
    }

    @Override
    protected void onStop() {
        liveHandler.removeCallbacks(liveRefresh);
        liveStateView.setText("● DURAKLATILDI");
        liveStateView.setTextColor(Color.rgb(121, 116, 126));
        super.onStop();
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 242, 250));
        root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsets.Type.systemBars());
            view.setPadding(
                    dp(16) + bars.left,
                    dp(20) + bars.top,
                    dp(16) + bars.right,
                    dp(12) + bars.bottom);
            return windowInsets;
        });

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(Color.rgb(29, 25, 43));
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, matchWrap());

        liveStateView = new TextView(this);
        liveStateView.setTextSize(14);
        liveStateView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        liveStateView.setPadding(0, dp(6), 0, dp(4));
        root.addView(liveStateView, matchWrap());

        activationStateView = new TextView(this);
        activationStateView.setTextSize(14);
        activationStateView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        activationStateView.setPadding(0, dp(4), 0, dp(4));
        root.addView(activationStateView, matchWrap());

        LinearLayout activationControls = new LinearLayout(this);
        activationControls.setOrientation(LinearLayout.HORIZONTAL);
        addWeightedButton(activationControls,
                actionButton("Hareketleri aç", view -> setGestureActivation(true)));
        addWeightedButton(activationControls,
                actionButton("Güvenli kapat", view -> setGestureActivation(false)));
        root.addView(activationControls, matchWrap());

        summaryView = new TextView(this);
        summaryView.setTextColor(Color.rgb(73, 69, 79));
        summaryView.setTextSize(14);
        summaryView.setPadding(0, 0, 0, dp(8));
        root.addView(summaryView, matchWrap());

        filterView = new TextView(this);
        filterView.setText("Gösterilen: Tümü (en yeni 1000 olay)");
        filterView.setTextColor(Color.rgb(73, 69, 79));
        filterView.setTextSize(12);
        root.addView(filterView, matchWrap());

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(0, dp(6), 0, dp(8));

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        addWeightedButton(filters, filterButton("Tümü", null));
        addWeightedButton(filters, filterButton("Başarılı", DiagnosticEvent.STATUS_SUCCESS));
        addWeightedButton(filters, filterButton("Başarısız", DiagnosticEvent.STATUS_FAILURE));
        addWeightedButton(filters, filterButton("Bilgi", DiagnosticEvent.STATUS_INFO));
        controls.addView(filters, matchWrap());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        addWeightedButton(actions,
                actionButton("Anlık kayıt", view -> captureAppSnapshot("manual-refresh")));
        addWeightedButton(actions, actionButton("Kayıtları temizle", view -> confirmClear()));
        controls.addView(actions, matchWrap());
        root.addView(controls, matchWrap());

        eventList = new ListView(this);
        eventList.setAdapter(eventAdapter);
        eventList.setDividerHeight(dp(8));
        eventList.setClipToPadding(false);
        eventList.setPadding(0, dp(4), 0, dp(8));
        eventList.setBackgroundColor(Color.TRANSPARENT);
        root.addView(eventList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));

        TextView footer = new TextView(this);
        footer.setText("Tüm olaylar kalıcı veritabanında tutulur. Ekran performans için son 1000 kaydı gösterir.");
        footer.setTextColor(Color.rgb(121, 116, 126));
        footer.setTextSize(11);
        root.addView(footer, matchWrap());
        return root;
    }

    private Button filterButton(String label, String filter) {
        return actionButton(label, view -> {
            activeFilter = filter;
            filterView.setText("Gösterilen: " + label + " (en yeni 1000 olay)");
            recordAppEvent(
                    DiagnosticEvent.STATUS_SUCCESS,
                    "ui",
                    "filter-changed",
                    filter == null ? "ALL" : filter);
            reloadEvents();
        });
    }

    private Button actionButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(dp(6));
        button.setLayoutParams(params);
        return button;
    }

    private void addWeightedButton(LinearLayout row, Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f);
        params.setMarginEnd(dp(6));
        button.setLayoutParams(params);
        row.addView(button);
    }

    private void reloadEvents() {
        try {
            DiagnosticDatabase.Counts counts = database.counts();
            boolean sameFilter = activeFilter == null
                    ? renderedFilter == null
                    : activeFilter.equals(renderedFilter);
            if (counts.total == renderedTotal && sameFilter) {
                return;
            }
            List<DiagnosticEvent> events = database.latest(activeFilter);
            summaryView.setText("Toplam " + counts.total
                    + "   ✓ " + counts.success
                    + "   ✕ " + counts.failure
                    + "   ℹ " + counts.info);
            eventAdapter.replace(events);
            eventList.setSelection(0);
            renderedTotal = counts.total;
            renderedFilter = activeFilter;
        } catch (Throwable throwable) {
            liveStateView.setText("● HATA — diagnostics veritabanı okunamadı");
            liveStateView.setTextColor(Color.rgb(186, 26, 26));
        }
    }

    private void updateActivationState() {
        if (activationStateView == null) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            activationStateView.setText("● YETKİ GEREKLİ — aktivasyon izni verilmemiş");
            activationStateView.setTextColor(Color.rgb(186, 26, 26));
            return;
        }
        boolean enabled = GestureActivation.isEnabled(this);
        boolean systemUiReady = GestureActivation.isSystemUiReady(this);
        boolean launcherReady = GestureActivation.isLauncherReady(this);
        int forceFsg = GestureActivation.readGlobalInt(
                this, GestureActivation.KEY_FORCE_FSG_NAV_BAR, 0);
        int navigationMode = readSecureInt(GestureActivation.KEY_NAVIGATION_MODE, -1);
        String readiness = "SystemUI " + (systemUiReady ? "✓" : "…")
                + " · Launcher " + (launcherReady ? "✓" : "…");
        if (enabled && forceFsg == 1 && navigationMode == 2) {
            activationStateView.setText("● AKTİF — hareketle gezinme açık · " + readiness);
            activationStateView.setTextColor(Color.rgb(20, 125, 70));
        } else if (enabled) {
            activationStateView.setText("● BAŞLATILIYOR — sistem geçişi bekleniyor · " + readiness);
            activationStateView.setTextColor(Color.rgb(151, 84, 0));
        } else {
            activationStateView.setText("○ KAPALI — " + readiness);
            activationStateView.setTextColor(Color.rgb(73, 69, 79));
        }
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
            reloadEvents();
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
                    .setMessage("SystemUI ve Xiaomi Launcher hook'ları hazır olmadan gezinme çubuğu gizlenmeyecek. SystemUI ile Launcher yeniden başlatılmalı.")
                    .setPositiveButton("Tamam", null)
                    .show();
            reloadEvents();
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
        renderedTotal = -1L;
        updateActivationState();
        reloadEvents();
        liveHandler.postDelayed(
                () -> captureAppSnapshot("activation-settled:" + enabled),
                1500L);
    }

    private void captureAppSnapshot(String reason) {
        try {
            String detail = "reason=" + reason
                    + " | " + buildDeviceSummary()
                    + " | defaultHome=" + resolveDefaultHome()
                    + " | activation=" + GestureActivation.isEnabled(this)
                    + " | hooks={systemUi=" + GestureActivation.isSystemUiReady(this)
                    + ",launcher=" + GestureActivation.isLauncherReady(this) + "}"
                    + " | global/force_fsg_nav_bar="
                    + GestureActivation.readGlobalInt(
                            this, GestureActivation.KEY_FORCE_FSG_NAV_BAR, -1)
                    + " | secure/force_fsg_nav_bar=" + readSecure("force_fsg_nav_bar")
                    + " | secure/navigation_mode=" + readSecure("navigation_mode")
                    + " | secure/navigation_bar_mode=" + readSecure("navigation_bar_mode")
                    + " | secure/miui_fullscreen_gesture="
                    + readSecure("miui_fullscreen_gesture");
            recordAppEvent(
                    DiagnosticEvent.STATUS_SUCCESS,
                    "snapshot",
                    "capture-app-state",
                    detail);
        } catch (Throwable throwable) {
            recordAppEvent(
                    DiagnosticEvent.STATUS_FAILURE,
                    "snapshot",
                    "capture-app-state",
                    throwable.getClass().getName() + ": " + throwable.getMessage());
        }
        reloadEvents();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Diagnostics kayıtları silinsin mi?")
                .setMessage("Bu işlem cihazdaki önceki başarı, hata ve bilgi olaylarını kalıcı olarak siler.")
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Sil", (dialog, which) -> {
                    database.clear();
                    renderedTotal = -1L;
                    recordAppEvent(
                            DiagnosticEvent.STATUS_SUCCESS,
                            "storage",
                            "diagnostic-events-cleared",
                            "User cleared the diagnostics database");
                    reloadEvents();
                })
                .show();
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
                return "unresolved";
            }
            return new ComponentName(
                    resolved.activityInfo.packageName,
                    resolved.activityInfo.name).flattenToShortString();
        } catch (Throwable throwable) {
            return "error:" + throwable.getClass().getSimpleName();
        }
    }

    private String readSecure(String key) {
        try {
            String value = Settings.Secure.getString(getContentResolver(), key);
            return value == null ? "<null>" : value;
        } catch (Throwable throwable) {
            return "error:" + throwable.getClass().getSimpleName();
        }
    }

    private int readSecureInt(String key, int fallback) {
        try {
            return Settings.Secure.getInt(getContentResolver(), key, fallback);
        } catch (Throwable throwable) {
            return fallback;
        }
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class EventAdapter extends BaseAdapter {
        private final List<DiagnosticEvent> events = new ArrayList<>();
        private final SimpleDateFormat timeFormat =
                new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

        void replace(List<DiagnosticEvent> replacement) {
            events.clear();
            events.addAll(replacement);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return events.size();
        }

        @Override
        public DiagnosticEvent getItem(int position) {
            return events.get(position);
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            EventRow row;
            if (convertView instanceof LinearLayout && convertView.getTag() instanceof EventRow) {
                row = (EventRow) convertView.getTag();
            } else {
                row = createEventRow();
                convertView = row.container;
                convertView.setTag(row);
            }
            bindEventRow(row, getItem(position));
            return convertView;
        }

        private EventRow createEventRow() {
            LinearLayout container = new LinearLayout(DiagnosticsActivity.this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(dp(12), dp(10), dp(12), dp(10));

            TextView header = new TextView(DiagnosticsActivity.this);
            header.setTextSize(13);
            header.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            container.addView(header, matchWrap());

            TextView detail = new TextView(DiagnosticsActivity.this);
            detail.setTextColor(Color.rgb(73, 69, 79));
            detail.setTextSize(12);
            detail.setTypeface(Typeface.MONOSPACE);
            detail.setTextIsSelectable(true);
            detail.setPadding(0, dp(5), 0, 0);
            container.addView(detail, matchWrap());

            TextView source = new TextView(DiagnosticsActivity.this);
            source.setTextColor(Color.rgb(121, 116, 126));
            source.setTextSize(10);
            source.setGravity(Gravity.END);
            source.setPadding(0, dp(5), 0, 0);
            container.addView(source, matchWrap());
            return new EventRow(container, header, detail, source);
        }

        private void bindEventRow(EventRow row, DiagnosticEvent event) {
            int background;
            int accent;
            if (DiagnosticEvent.STATUS_SUCCESS.equals(event.status)) {
                background = Color.rgb(229, 246, 235);
                accent = Color.rgb(20, 105, 60);
            } else if (DiagnosticEvent.STATUS_FAILURE.equals(event.status)) {
                background = Color.rgb(255, 232, 230);
                accent = Color.rgb(186, 26, 26);
            } else {
                background = Color.rgb(232, 238, 255);
                accent = Color.rgb(47, 67, 130);
            }
            row.container.setBackgroundColor(background);
            row.header.setTextColor(accent);
            row.header.setText(timeFormat.format(new Date(event.timestamp))
                    + "  " + event.status
                    + "  " + event.category + '/' + event.operation);
            row.detail.setText(event.detail.isEmpty() ? "—" : event.detail);
            row.source.setText(event.processName + " · " + event.threadName + " · #" + event.id);
        }
    }

    private static final class EventRow {
        final LinearLayout container;
        final TextView header;
        final TextView detail;
        final TextView source;

        EventRow(
                LinearLayout container,
                TextView header,
                TextView detail,
                TextView source) {
            this.container = container;
            this.header = header;
            this.detail = detail;
            this.source = source;
        }
    }
}
