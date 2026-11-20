package com.tvos.neo;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

/** Shared theme tokens for the entire Neo shell, cards, menus, clocks and widgets. */
final class NeoTheme {
    String id, name, wallpaper, fontFamily, clockFont;android.graphics.Typeface customText,customClock;
    android.graphics.Typeface uiTypeface(){return customText!=null?customText:android.graphics.Typeface.create("inter".equals(fontFamily)?"sans-serif-medium":fontFamily,0);}
    android.graphics.Typeface clockTypeface(){return customClock!=null?customClock:android.graphics.Typeface.create(clockFont,0);}
    private static NeoTheme fonts(Context c,NeoTheme theme){theme.customText=NeoFonts.selected(c,false);theme.customClock=NeoFonts.selected(c,true);return theme;}
    float textScale;
    int accent, text, muted, glass, rim, focus, scrim, radius, motionMs, columns;

    static String read(InputStream in) throws Exception {
        try (InputStream source = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int n;
            while ((n = source.read(buffer)) != -1) {
                if (out.size() + n > 32768) throw new IllegalArgumentException("Theme exceeds 32 KB");
                out.write(buffer, 0, n);
            }
            return out.toString("UTF-8");
        }
    }

    static NeoTheme parse(String raw) throws Exception {
        JSONObject j = new JSONObject(raw);
        NeoTheme t = new NeoTheme();
        t.id = j.getString("id"); t.name = j.getString("name");
        if (t.id.length() > 60 || t.name.length() > 60) throw new IllegalArgumentException("Theme name too long");
        t.accent = Color.parseColor(j.getString("accent"));
        t.text = Color.parseColor(j.getString("text"));
        t.muted = Color.parseColor(j.getString("muted"));
        t.glass = Color.parseColor(j.getString("glass"));
        t.rim = Color.parseColor(j.getString("rim"));
        t.focus = Color.parseColor(j.getString("focus"));
        t.scrim = Color.parseColor(j.getString("scrim"));
        t.radius = Math.max(8, Math.min(36, j.optInt("radius", 22)));
        t.motionMs = Math.max(0, Math.min(300, j.optInt("motionMs", 160)));
        t.columns = Math.max(3, Math.min(5, j.optInt("columns", 4)));
        t.wallpaper = j.optString("wallpaper", "neo/lake-boy.png");
        if (!(t.wallpaper.startsWith("neo/") || t.wallpaper.matches("file:[A-Za-z0-9_.-]+")) || t.wallpaper.contains("..") || t.wallpaper.length()>128)
            throw new IllegalArgumentException("Wallpaper must be a Neo asset");
        t.fontFamily = j.optString("fontFamily", "inter");
        t.clockFont = j.optString("clockFont", "sans-serif-thin");
        if (!java.util.Arrays.asList("inter","sans-serif","sans-serif-light","serif","monospace").contains(t.fontFamily)
                || !java.util.Arrays.asList("sans-serif-thin","sans-serif-light","monospace").contains(t.clockFont))
            throw new IllegalArgumentException("Unsupported font family");
        t.textScale = Math.max(0.9f, Math.min(1.1f, (float)j.optDouble("textScale",1)));
        return t;
    }

    static InputStream openWallpaper(Context c, String path) throws Exception {
        if(path.startsWith("file:")) return new java.io.FileInputStream(new java.io.File(c.getExternalFilesDir("wallpapers"),path.substring(5)));
        return c.getAssets().open(path);
    }

    static void validateWallpaper(Context c, NeoTheme t) throws Exception {
        android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;
        try(InputStream in=openWallpaper(c,t.wallpaper)) { android.graphics.BitmapFactory.decodeStream(in,null,bounds); }
        if(bounds.outWidth<1 || bounds.outHeight<1)throw new IllegalArgumentException("Invalid wallpaper image");
    }

    static NeoTheme load(Context c) {
        try {
            String raw = c.getSharedPreferences("neo", 0).getString("theme_json", null);
            if (raw != null) return fonts(c,parse(raw));
        } catch (Exception ignored) { }
        try { return fonts(c,parse(read(c.getAssets().open("neo/themes/aurora.json")))); }
        catch (Exception e) { throw new IllegalStateException("Bundled theme missing", e); }
    }

    GradientDrawable surface(float unit, boolean focused) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{focused ? focus : glass, glass});
        d.setCornerRadius(radius * unit);
        d.setStroke(Math.max(1, Math.round(unit * (focused ? 1.7f : 0.8f))), focused ? accent : rim);
        return d;
    }
}
