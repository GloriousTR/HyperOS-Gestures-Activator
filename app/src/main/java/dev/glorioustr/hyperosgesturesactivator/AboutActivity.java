package dev.glorioustr.hyperosgesturesactivator;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class AboutActivity extends Activity {
    private static final String PROJECT_URL =
            "https://github.com/GloriousTR/HyperOS-Gestures-Activator";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.about_title);
        setContentView(buildScreen());
        DiagnosticDatabase.get(this).insert(new DiagnosticEvent(
                0L,
                System.currentTimeMillis(),
                DiagnosticEvent.STATUS_SUCCESS,
                "ui",
                "about-opened",
                "version=" + BuildConfig.VERSION_NAME,
                getPackageName(),
                Thread.currentThread().getName()));
    }

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(246, 248, 252));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(32));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            view.setPadding(dp(20) + bars.left, dp(16) + bars.top,
                    dp(20) + bars.right, dp(32) + bars.bottom);
            return insets;
        });
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(buildTopBar(), matchWrap());
        root.addView(space(18));
        root.addView(buildHero(), matchWrap());
        root.addView(space(16));
        root.addView(buildFeatureCard(), matchWrap());
        root.addView(space(16));
        root.addView(buildRepositoryCard(), matchWrap());
        return scroll;
    }

    private View buildTopBar() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 34, Color.rgb(42, 54, 74), Typeface.NORMAL);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription(getString(R.string.back_cd));
        back.setClickable(true);
        back.setFocusable(true);
        back.setOnClickListener(view -> finish());
        row.addView(back, new LinearLayout.LayoutParams(dp(44), dp(48)));
        TextView title = text(getString(R.string.about_title), 24,
                Color.rgb(25, 33, 48), Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMarginStart(dp(8));
        row.addView(title, params);
        return row;
    }

    private View buildHero() {
        LinearLayout card = card();
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(20), dp(22), dp(20), dp(24));
        card.setBackground(rounded(Color.rgb(9, 19, 48), 26,
                Color.rgb(30, 68, 150), 1));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher_art);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setAdjustViewBounds(true);
        logo.setContentDescription(getString(R.string.about_logo_cd));
        card.addView(logo, new LinearLayout.LayoutParams(dp(220), dp(220)));

        TextView eyebrow = text(getString(R.string.about_eyebrow), 11,
                Color.rgb(123, 196, 255), Typeface.BOLD);
        eyebrow.setLetterSpacing(0.12f);
        eyebrow.setGravity(Gravity.CENTER);
        eyebrow.setPadding(0, dp(14), 0, 0);
        card.addView(eyebrow, matchWrap());

        TextView appName = text(getString(R.string.app_name), 23,
                Color.WHITE, Typeface.BOLD);
        appName.setGravity(Gravity.CENTER);
        appName.setPadding(0, dp(7), 0, 0);
        card.addView(appName, matchWrap());

        TextView version = text(getString(
                R.string.about_version, BuildConfig.VERSION_NAME), 13,
                Color.rgb(190, 202, 230), Typeface.NORMAL);
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, dp(5), 0, 0);
        card.addView(version, matchWrap());

        TextView description = text(getString(R.string.about_description), 14,
                Color.rgb(221, 228, 245), Typeface.NORMAL);
        description.setGravity(Gravity.CENTER);
        description.setLineSpacing(0f, 1.15f);
        description.setPadding(0, dp(16), 0, 0);
        card.addView(description, matchWrap());
        return card;
    }

    private View buildFeatureCard() {
        LinearLayout card = card();
        addFeature(card, "⌁", R.string.about_capabilities_title,
                R.string.about_capabilities_desc, false);
        addFeature(card, "✓", R.string.about_safe_title,
                R.string.about_safe_desc, true);
        addFeature(card, "◉", R.string.about_diagnostics_title,
                R.string.about_diagnostics_desc, true);
        return card;
    }

    private void addFeature(
            LinearLayout parent,
            String symbol,
            int titleId,
            int descriptionId,
            boolean divider) {
        if (divider) {
            parent.addView(divider(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(14), 0, dp(14));
        TextView icon = text(symbol, 20, Color.rgb(67, 79, 146), Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(rounded(
                Color.rgb(239, 241, 255), 13, Color.TRANSPARENT, 0));
        row.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.setMarginStart(dp(14));
        row.addView(copy, copyParams);
        copy.addView(text(getString(titleId), 15,
                Color.rgb(32, 41, 58), Typeface.BOLD), matchWrap());
        TextView description = text(getString(descriptionId), 12,
                Color.rgb(96, 105, 121), Typeface.NORMAL);
        description.setPadding(0, dp(4), 0, 0);
        copy.addView(description, matchWrap());
        parent.addView(row, matchWrap());
    }

    private View buildRepositoryCard() {
        LinearLayout card = card();
        TextView title = text(getString(R.string.about_repository_title), 17,
                Color.rgb(26, 35, 52), Typeface.BOLD);
        card.addView(title, matchWrap());
        TextView description = text(getString(R.string.about_repository_desc), 13,
                Color.rgb(96, 105, 121), Typeface.NORMAL);
        description.setPadding(0, dp(6), 0, 0);
        card.addView(description, matchWrap());
        TextView url = text(getString(R.string.about_repository_url), 11,
                Color.rgb(71, 83, 160), Typeface.NORMAL);
        url.setPadding(0, dp(12), 0, dp(12));
        url.setTextIsSelectable(true);
        card.addView(url, matchWrap());

        Button open = new Button(this);
        open.setAllCaps(false);
        open.setText(R.string.about_open_github);
        open.setTextColor(Color.WHITE);
        open.setTextSize(14);
        open.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        open.setBackground(rounded(
                Color.rgb(68, 82, 160), 14, Color.TRANSPARENT, 0));
        open.setOnClickListener(view -> openProject());
        card.addView(open, matchWrap());
        return card;
    }

    private void openProject() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL)));
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.about_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(8), dp(18), dp(8));
        card.setBackground(rounded(
                Color.WHITE, 20, Color.rgb(225, 230, 238), 1));
        card.setElevation(dp(1));
        return card;
    }

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(235, 238, 244));
        return divider;
    }

    private View space(int height) {
        return new View(this) {{
            setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        }};
    }

    private TextView text(String value, int size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable rounded(
            int color,
            int radius,
            int strokeColor,
            int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) {
            drawable.setStroke(dp(strokeWidth), strokeColor);
        }
        return drawable;
    }
}
