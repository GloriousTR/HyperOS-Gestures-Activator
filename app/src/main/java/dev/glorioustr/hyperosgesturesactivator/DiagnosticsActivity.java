package dev.glorioustr.hyperosgesturesactivator;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

public final class DiagnosticsActivity extends Activity {
    private TextView snapshotView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.screen_title);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(28), dp(20), dp(32));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(Color.rgb(29, 25, 43));
        title.setTextSize(26);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        content.addView(title, matchWrap());

        TextView intro = new TextView(this);
        intro.setText(R.string.screen_intro);
        intro.setTextColor(Color.rgb(73, 69, 79));
        intro.setTextSize(16);
        intro.setPadding(0, dp(12), 0, dp(20));
        content.addView(intro, matchWrap());

        Button refresh = new Button(this);
        refresh.setText(R.string.refresh);
        refresh.setOnClickListener(view -> refreshSnapshot());
        content.addView(refresh, matchWrap());

        snapshotView = new TextView(this);
        snapshotView.setTextColor(Color.rgb(29, 25, 43));
        snapshotView.setTextSize(14);
        snapshotView.setTextIsSelectable(true);
        snapshotView.setTypeface(android.graphics.Typeface.MONOSPACE);
        snapshotView.setPadding(0, dp(20), 0, 0);
        content.addView(snapshotView, matchWrap());

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(247, 242, 250));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scrollView);
        refreshSnapshot();
    }

    private void refreshSnapshot() {
        StringBuilder report = new StringBuilder();
        report.append("BUILD\n")
                .append("HGA 0.1.0 (Navigation Diagnostics)\n")
                .append("Device: ").append(Build.MANUFACTURER).append(' ')
                .append(Build.MODEL).append('\n')
                .append("Android: ").append(Build.VERSION.RELEASE)
                .append(" / API ").append(Build.VERSION.SDK_INT).append('\n')
                .append("Build: ").append(Build.DISPLAY).append("\n\n")
                .append("APP-SIDE SNAPSHOT\n")
                .append("Default HOME: ").append(resolveDefaultHome()).append('\n')
                .append("secure/force_fsg_nav_bar: ")
                .append(readSecure("force_fsg_nav_bar")).append('\n')
                .append("secure/navigation_mode: ")
                .append(readSecure("navigation_mode")).append('\n')
                .append("secure/navigation_bar_mode: ")
                .append(readSecure("navigation_bar_mode")).append('\n')
                .append("secure/miui_fullscreen_gesture: ")
                .append(readSecure("miui_fullscreen_gesture")).append("\n\n")
                .append("TEST PROTOCOL\n")
                .append("1. Enable this module for System UI in LSPosed/Vector.\n")
                .append("2. Restart System UI or reboot the device.\n")
                .append("3. Select Xiaomi Launcher and then the third-party launcher.\n")
                .append("4. Export the LSPosed module log and filter for HGA/Diagnostics.\n\n")
                .append("This build does not modify navigation settings or overlays.\n")
                .append("Keep MiuiBackGestureHook 0.4.0 enabled for the existing Back gesture.");
        snapshotView.setText(report.toString());
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
            return TextUtils.isEmpty(value) ? "<null>" : value;
        } catch (Throwable throwable) {
            return String.format(Locale.ROOT, "error:%s", throwable.getClass().getSimpleName());
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
}
