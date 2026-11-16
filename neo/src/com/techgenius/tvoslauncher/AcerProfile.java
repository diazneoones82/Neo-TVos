package com.tvos.neo;

import android.content.Context;
import android.content.SharedPreferences;

/** First-run defaults for the Acer ATV R1; later user choices are preserved. */
final class AcerProfile {
    private AcerProfile() { }

    static void apply(Context c) {
        SharedPreferences profile = c.getSharedPreferences("acer_profile", Context.MODE_PRIVATE);
        if (!profile.getBoolean("home_fix_initialized", false)) {
            ButtonMapper.setHomeFixEnabled(c, true);
            profile.edit().putBoolean("home_fix_initialized", true).apply();
        }
        if (profile.getBoolean("initialized", false)) return;
        I18n.setLang(c, "en");
        c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putString("look_mode", Scenes.LOOK_SCENES)
                .putString("scene_enabled", "night")
                .putString("prefer_scene_id", "night")
                .putInt("dock_scale_pct", 100)
                .putBoolean("dock_names", true)
                .putBoolean("clock_24h", false).apply();
        HomeDashboard.setEnabled(c, true);
        HomeDashboard.setShowWhenNoCw(c, true);
        HomeDashboard.setWidgetPrayerEnabled(c, false);
        HomeDashboard.setWidgetFootballEnabled(c, false);
        HomeDashboard.setWidgetReleasesEnabled(c, false);
        HomeDashboard.setWidgetWeatherEnabled(c, true);
        ControlPanelOverlay.setBgMode(c, ControlPanelOverlay.BG_MODE_TRANSPARENT);
        // A locally signed build cannot be replaced by the upstream release signature.
        AppUpdater.setAutoUpdateEnabled(c, false);
        c.getSharedPreferences("setup", Context.MODE_PRIVATE).edit()
                .putBoolean("setup_done", true).apply();
        profile.edit().putBoolean("initialized", true).apply();
    }
}
