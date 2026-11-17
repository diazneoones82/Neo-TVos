package com.tvos.neo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Neo's independently authored rectangular-card home screen and theme-aware controls. */
public final class NeoHomeActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<App> apps = new ArrayList<>();
    private final List<View> cards = new ArrayList<>();
    private SharedPreferences prefs;
    private NeoTheme theme;
    private FrameLayout root;
    private TextView headerTime, headerDate, heroLabel, weatherTitle, weatherNow, weatherDetails, weatherForecast;
    private NeoFlipClock heroTime, secondTime;
    private TextView secondLabel, firstZoneLabel;
    private LinearLayout dualClockRow;
    private NeoAppLock appLock;
    private NeoIconPacks iconPacks;
    private NeoCategories categories;
    private LinearLayout categoryBar;
    private TextView controlToggle;
    private String activeCategory="all";
    private LinearLayout grid;
    private ScrollView appScroll;
    private Bitmap wallpaper;
    private String loadedWallpaper="", loadingWallpaper="";
    private float unit;
    private boolean resumed, closed;
    private long lastInteraction=android.os.SystemClock.elapsedRealtime();
    private String focusedKey = "@inputs";
    private Dialog activeDialog;
    private int weatherTicks;
    private String movingKey;
    private List<String> moveOrder;
    private TextView appHeading,appInstructions;
    private boolean swallowMoveUp;
    private int analogX,analogY;
    private final Runnable analogRepeat=new Runnable(){@Override public void run(){
        if(!resumed || (analogX==0 && analogY==0))return;
        navigateApps(analogX,analogY);main.postDelayed(this,170);
    }};

    private static final class App {
        String key, pkg, label; Drawable icon; Intent intent; boolean system;
    }

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!resumed || closed) return;
            updateClock();
            if(activeDialog!=null && activeDialog.isShowing())lastInteraction=android.os.SystemClock.elapsedRealtime();
            if(prefs.getBoolean("saver_enabled",false) && (activeDialog==null||!activeDialog.isShowing()) && android.os.SystemClock.elapsedRealtime()-lastInteraction>=prefs.getInt("saver_minutes",5)*60000L){lastInteraction=android.os.SystemClock.elapsedRealtime();startActivity(new Intent(NeoHomeActivity.this,NeoScreensaverActivity.class));return;}
            if (++weatherTicks >= 900) { weatherTicks = 0; refreshWeather(false); }
            main.postDelayed(this, 1000 - System.currentTimeMillis() % 1000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("neo", MODE_PRIVATE);
        iconPacks=new NeoIconPacks(this);appLock=new NeoAppLock(this);
        categories=new NeoCategories(prefs);activeCategory=prefs.getString("active_category","all");
        acceptIntentTheme(getIntent());
        if (!prefs.contains("initialized")) {
            NeoForecast.prefs(this).edit().putString(NeoForecast.K_UNITS, "c").apply();
            prefs.edit().putBoolean("initialized", true).apply();
        }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setBackgroundDrawableResource(android.R.color.black);
        unit = getResources().getDisplayMetrics().widthPixels / 960f;
        theme = NeoTheme.load(this);
        worker.execute(() -> {
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                try (java.io.InputStream stream = NeoTheme.openWallpaper(this,theme.wallpaper)) {
                    wallpaper = BitmapFactory.decodeStream(stream, null, options);
                    loadedWallpaper=theme.wallpaper;
                }
            } catch (Exception ignored) { }
            List<App> found = discoverApps();
            main.post(() -> { if (!closed) { apps.clear(); apps.addAll(found); build(); } });
        });
        build();
    }

    private int px(float value) { return Math.round(value * unit); }
    private TextView text(String value, float size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextColor(color);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(size * theme.textScale));
        t.setTypeface("inter".equals(theme.fontFamily) ? Typeface.create("sans-serif-medium",Typeface.NORMAL) : Typeface.create(theme.fontFamily,Typeface.NORMAL)); t.setIncludeFontPadding(false);
        return t;
    }
    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    private FrameLayout.LayoutParams position(int w, int h, int gravity, int x, int y) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w < 0 ? w : px(w), h < 0 ? h : px(h), gravity);
        if ((gravity & Gravity.RIGHT) == Gravity.RIGHT) p.rightMargin = px(x); else p.leftMargin = px(x);
        p.topMargin = px(y); return p;
    }

    private void build() {
        if(heroTime!=null)heroTime.stop();if(secondTime!=null)secondTime.stop();
        theme = NeoTheme.load(this);
        if (prefs.getBoolean("reduced_motion",false)) theme.motionMs = 0;
        if(!theme.wallpaper.equals(loadedWallpaper) && !theme.wallpaper.equals(loadingWallpaper)) {
            final String path=theme.wallpaper; loadingWallpaper=path;
            worker.execute(() -> {
                Bitmap image=null;
                try {
                    BitmapFactory.Options options=new BitmapFactory.Options(); options.inJustDecodeBounds=true;
                    try(java.io.InputStream in=NeoTheme.openWallpaper(this,path)){BitmapFactory.decodeStream(in,null,options);}
                    int sample=1; while(options.outWidth/sample>1920 || options.outHeight/sample>1080)sample*=2;
                    options.inJustDecodeBounds=false; options.inSampleSize=sample; options.inPreferredConfig=Bitmap.Config.RGB_565;
                    try(java.io.InputStream in=NeoTheme.openWallpaper(this,path)){image=BitmapFactory.decodeStream(in,null,options);}
                } catch(Exception ignored) { }
                final Bitmap ready=image;
                main.post(() -> { if(!closed && path.equals(NeoTheme.load(this).wallpaper)) { loadedWallpaper=path; loadingWallpaper=""; if(ready!=null)wallpaper=ready; build(); } });
            });
        }
        root = new FrameLayout(this); root.setBackgroundColor(0xFF071326);
        root.setClipChildren(false);
        if (wallpaper != null) {
            ImageView bg = new ImageView(this); bg.setImageBitmap(wallpaper); bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
            root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        }
        View scrim = new View(this); scrim.setBackgroundColor(theme.scrim);
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout branding = column();
        TextView brand = text("Neo TVLauncher", 23, theme.text); brand.setLetterSpacing(0.06f);
        branding.addView(brand);
        root.addView(branding, position(240, 60, Gravity.TOP | Gravity.LEFT, 36, 28));

        LinearLayout weather = column(); weather.setPadding(px(12), px(10), px(12), px(10));
        LinearLayout clockRow=new LinearLayout(this);clockRow.setGravity(Gravity.CENTER_VERTICAL);
        weatherTitle = text("WEATHER", 9, theme.accent);weatherTitle.setMaxLines(1);weatherTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        clockRow.addView(weatherTitle,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout timeColumn=column();timeColumn.setGravity(Gravity.RIGHT);
        headerTime=text("",15,theme.text);headerTime.setTypeface(Typeface.MONOSPACE);headerTime.setGravity(Gravity.RIGHT);
        headerDate=text("",8,theme.muted);headerDate.setGravity(Gravity.RIGHT);
        timeColumn.addView(headerTime);timeColumn.addView(headerDate);
        clockRow.addView(timeColumn,new LinearLayout.LayoutParams(px(104),-2));
        weather.addView(clockRow);
        weatherNow = text("Loading forecast", 16, theme.text);
        weatherNow.setMaxLines(1); weatherNow.setEllipsize(android.text.TextUtils.TruncateAt.END);
        weatherDetails = text("Current conditions will appear here", 8, theme.muted);
        weatherForecast = text("", 8, theme.muted);
        LinearLayout.LayoutParams currentLp=new LinearLayout.LayoutParams(-1,-2);currentLp.topMargin=px(3);
        weather.addView(weatherNow,currentLp);
        weather.addView(weatherDetails); LinearLayout.LayoutParams forecastLp = new LinearLayout.LayoutParams(-1, -2);
        forecastLp.topMargin = px(5); weather.addView(weatherForecast, forecastLp);
        interactive(weather, () -> showWeather(), () -> showWeather());
        root.addView(weather, position(260, 104, Gravity.TOP | Gravity.RIGHT, 36, 22));

        LinearLayout hero = column(); hero.setGravity(Gravity.CENTER);
        heroLabel = text("LOCAL TIME", 9, theme.accent); heroLabel.setLetterSpacing(0.23f); heroLabel.setGravity(Gravity.CENTER);
        dualClockRow=new LinearLayout(this);dualClockRow.setGravity(Gravity.CENTER);
        LinearLayout first=column();first.setGravity(Gravity.CENTER);
        firstZoneLabel=text("",9,theme.accent);firstZoneLabel.setGravity(Gravity.CENTER);first.addView(firstZoneLabel);
        heroTime = new NeoFlipClock(this,theme,unit*0.72f,prefs.getBoolean("reduced_motion",false));
        first.addView(heroTime,new LinearLayout.LayoutParams(-1,px(58)));
        LinearLayout second=column();second.setGravity(Gravity.CENTER);
        secondLabel=text("",9,theme.accent);secondLabel.setGravity(Gravity.CENTER);second.addView(secondLabel);
        secondTime=new NeoFlipClock(this,theme,unit*0.72f,prefs.getBoolean("reduced_motion",false));
        second.addView(secondTime,new LinearLayout.LayoutParams(-1,px(58)));
        dualClockRow.addView(first,new LinearLayout.LayoutParams(0,-2,1));
        dualClockRow.addView(second,new LinearLayout.LayoutParams(0,-2,1));
        hero.addView(dualClockRow,new LinearLayout.LayoutParams(-1,px(82)));
        TextView heroHint = text("Select clock for timer controls", 9, theme.muted); heroHint.setGravity(Gravity.CENTER); heroHint.setAlpha(0f); hero.addView(heroHint);
        hero.setFocusable(true);hero.setFocusableInTouchMode(true); hero.setClickable(true); hero.setOnClickListener(v -> {v.requestFocus();fadeClockHint(heroHint,true);clockMenu();});
        hero.setOnLongClickListener(v -> {v.requestFocus();fadeClockHint(heroHint,true);clockMenu();return true;});
        hero.setOnFocusChangeListener((v, has) -> {heroLabel.setTextColor(has ? theme.text : theme.accent);fadeClockHint(heroHint,has);});
        root.addView(hero, position(640, 116, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 168));

        LinearLayout appArea = column();appArea.setClipChildren(false);appArea.setClipToPadding(false);
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text("YOUR APPS", 11, theme.text); heading.setLetterSpacing(0.15f); heading.setGravity(Gravity.CENTER);
        appHeading=heading;
        bar.addView(heading, new LinearLayout.LayoutParams(0, px(34), 1));
        bar.setClipChildren(false);controlToggle=text("",9,theme.text);controlToggle.setGravity(Gravity.CENTER);controlToggle.setPadding(px(10),px(4),px(10),px(4));
        interactive(controlToggle,()->{prefs.edit().putBoolean("controls_collapsed",!prefs.getBoolean("controls_collapsed",false)).apply();renderApps();},this::controlsMenu);
        LinearLayout.LayoutParams toggleLp=new LinearLayout.LayoutParams(-2,px(25));toggleLp.rightMargin=px(12);bar.addView(controlToggle,toggleLp);
        TextView controls = text("OK  Open     Hold OK  Options     Menu  Settings", 9, theme.muted);
        appInstructions=controls;
        bar.addView(controls);
        appArea.addView(bar);
        android.widget.HorizontalScrollView categoryScroll=new android.widget.HorizontalScrollView(this);
        categoryScroll.setHorizontalScrollBarEnabled(false);categoryScroll.setClipToPadding(false);categoryScroll.setClipChildren(false);categoryScroll.setPadding(px(8),px(8),px(8),px(8));
        categoryBar=new LinearLayout(this);categoryBar.setClipChildren(false);categoryBar.setClipToPadding(false);categoryBar.setGravity(Gravity.CENTER_VERTICAL);categoryBar.setPadding(px(6),0,px(6),0);
        categoryScroll.addView(categoryBar,new FrameLayout.LayoutParams(-2,px(32)));appArea.addView(categoryScroll,new LinearLayout.LayoutParams(-1,px(48)));
        ScrollView scroll = new NeoAppScroll(this,unit);appScroll=scroll;
        scroll.setPadding(px(8), px(12), px(8), px(12)); scroll.setVerticalScrollBarEnabled(false);
        grid = column(); grid.setPadding(px(8),px(8),px(8),px(8));grid.setClipToPadding(false); grid.setClipChildren(false); scroll.addView(grid);
        appArea.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        FrameLayout.LayoutParams areaLp = position(898, -1, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 292);
        areaLp.bottomMargin = px(15); root.addView(appArea, areaLp);
        setContentView(root); renderApps(); updateClock(); renderWeather();
    }

    private void fadeClockHint(TextView hint,boolean highlighted) {
        Object old=hint.getTag();if(old instanceof Runnable)hint.removeCallbacks((Runnable)old);
        hint.setTextColor(highlighted?theme.text:theme.muted);
        float from=hint.getAlpha(),to=highlighted?1f:0f;
        if(prefs.getBoolean("reduced_motion",false)){hint.setAlpha(to);hint.setTag(null);return;}
        long began=android.os.SystemClock.uptimeMillis();
        Runnable fade=new Runnable(){public void run(){
            if(!hint.isAttachedToWindow()){hint.setTag(null);return;}
            float t=Math.min(1f,(android.os.SystemClock.uptimeMillis()-began)/220f);
            hint.setAlpha(from+(to-from)*(1-(1-t)*(1-t)*(1-t)));
            if(t<1f)hint.postOnAnimation(this);else hint.setTag(null);
        }};
        hint.setTag(fade);hint.postOnAnimation(fade);
    }
    private void interactive(View view, Runnable click, Runnable hold) {
        view.setId(View.generateViewId()); view.setFocusable(true);view.setFocusableInTouchMode(true); view.setClickable(true);
        view.setBackground(theme.surface(unit, false));
        view.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(theme.surface(unit, focused));
            NeoFocusMotion.apply(v,focused,theme.motionMs,unit);
            if (focused && v.getTag() instanceof String) focusedKey = (String) v.getTag();
            if(focused && v.getTag(R.id.neo_category_id)!=null && appScroll!=null)appScroll.smoothScrollTo(0,0);
        });
        view.setOnClickListener(v -> {lastInteraction=android.os.SystemClock.elapsedRealtime();click.run();});
        if (hold != null) view.setOnLongClickListener(v -> { hold.run(); return true; });
    }

    private List<App> discoverApps() {
        Bundle response=NeoWorkerClient.blocking(this,NeoCatalogService.class,new Bundle(),6000);
        ArrayList<Bundle> rows=response.getParcelableArrayList("apps");
        if(rows==null)return discoverAppsLocally();
        List<App> found=new ArrayList<>();iconPacks.load(prefs.getString("icon_pack",""));
        for(Bundle row:rows)try{
            ComponentName cn=ComponentName.unflattenFromString(row.getString("component",""));if(cn==null)continue;
            String pkg=cn.getPackageName();if(pkg.equals(getPackageName())||pkg.equals("com.google.android.tvlauncher"))continue;
            android.content.pm.ActivityInfo info=getPackageManager().getActivityInfo(cn,0);
            App app=new App();app.pkg=pkg;app.key=cn.flattenToShortString();app.label=row.getString("label",pkg);
            app.icon=iconPacks.icon(cn,info.loadIcon(getPackageManager()));app.system=(info.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0;
            app.intent=new Intent(Intent.ACTION_MAIN).setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);found.add(app);
        }catch(Exception ignored){}
        Collections.sort(found,(a,b)->a.label.compareToIgnoreCase(b.label));return found;
    }
    private List<App> discoverAppsLocally() {
        List<App> found = new ArrayList<>(); Set<String> packages = new HashSet<>();
        iconPacks.load(prefs.getString("icon_pack",""));
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo ri : getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName())
                        || pkg.equals("com.google.android.tvlauncher") || !packages.add(pkg)) continue;
                App a = new App(); a.pkg = pkg;
                ComponentName cn = new ComponentName(pkg, ri.activityInfo.name);
                a.key = cn.flattenToShortString(); a.label = ri.loadLabel(getPackageManager()).toString();
                a.icon = iconPacks.icon(cn,ri.loadIcon(getPackageManager()));
                a.system = (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                a.intent = new Intent(Intent.ACTION_MAIN).setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                found.add(a);
            }
        }
        Collections.sort(found, (a,b) -> a.label.compareToIgnoreCase(b.label)); return found;
    }

    private App special(String key, String name) { App a = new App(); a.key = key; a.label = name; a.system = true; return a; }
    private List<App> orderedApps() {
        List<App> out = new ArrayList<>();
        if(!prefs.getBoolean("controls_collapsed",false)){
            if(prefs.getBoolean("control_visible:@inputs",true))out.add(special("@inputs", "TV Inputs"));
            if(prefs.getBoolean("control_visible:@settings",true))out.add(special("@settings", "Neo TVLauncher Settings"));
            if(prefs.getBoolean("control_visible:@themes",true))out.add(special("@themes", "Theme Studio"));
            if(prefs.getBoolean("control_visible:@timer",true))out.add(special("@timer", "Clock & Timer"));
        }
        Set<String> hidden = prefs.getStringSet("hidden", Collections.emptySet());
        List<String> order = new ArrayList<>();
        try { JSONArray a = new JSONArray(prefs.getString(orderPreference(), prefs.getString("order", "[]"))); for (int i=0;i<a.length();i++) order.add(a.getString(i)); }
        catch (Exception ignored) { }
        if(moveOrder!=null)order=new ArrayList<>(moveOrder);
        final List<String> sortingOrder=order;
        List<App> actual = new ArrayList<>(apps);
        actual.sort((a,b) -> {
            int x = sortingOrder.indexOf(a.key), y = sortingOrder.indexOf(b.key);
            return Integer.compare(x < 0 ? Integer.MAX_VALUE : x, y < 0 ? Integer.MAX_VALUE : y);
        });
        int visibleApps=0;for (App a : actual) if (!hidden.contains(a.key) && categories.visible(activeCategory,a.key)){out.add(a);visibleApps++;}
        if(visibleApps==0)out.add(special("@add_apps","all".equals(activeCategory)?"Choose visible categories":"Add apps to "+categories.name(activeCategory)));
        return out;
    }

    private void renderApps() {
        if (grid == null) return; grid.removeAllViews(); cards.clear();
        List<String> keys=new ArrayList<>();for(App app:apps)keys.add(app.key);categories.seedNewApps(keys);
        if(!"all".equals(activeCategory)&&!categories.enabled(activeCategory)){activeCategory="all";prefs.edit().putString("active_category",activeCategory).apply();}
        renderCategoryBar();
        controlToggle.setText(prefs.getBoolean("controls_collapsed",false)?"Show controls":"Hide controls");
        appHeading.setText(movingKey==null?("all".equals(activeCategory)?"YOUR APPS":categories.name(activeCategory).toUpperCase(Locale.getDefault())):"REORDER APP");
        appInstructions.setText(movingKey==null?"OK  Open     Hold OK  Options     Menu  Settings":"Left / Right  Move     OK  Save     Back  Cancel");
        LinearLayout row = null; List<App> visible = orderedApps();
        for (int i=0;i<visible.size();i++) {
            App a = visible.get(i);
            if (i % theme.columns == 0) {
                row = new LinearLayout(this); row.setClipChildren(false);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, px(91)); rp.bottomMargin = px(12);
                grid.addView(row, rp);
            }
            LinearLayout card = column(); card.setTag(a.key); card.setPadding(px(10), px(7), px(10), px(7));
            card.setGravity(Gravity.CENTER);
            ImageView icon = new ImageView(this); icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (a.icon != null) icon.setImageDrawable(a.icon);
            else icon.setImageBitmap(NeoSymbols.render(a.key.equals("@inputs") ? "input" : a.key.equals("@timer") ? "timer" : a.key.equals("@themes") ? "shelf" : "gear", px(30), theme.accent));
            card.addView(icon, new LinearLayout.LayoutParams(px(26), px(26)));
            TextView title = text(prefs.getString("name:" + a.key, a.label), 12, theme.text);
            title.setMaxLines(2); title.setGravity(Gravity.CENTER); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setIncludeFontPadding(false);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2); tp.topMargin = px(4);
            card.addView(title, tp);
            TextView detail = text(a.key.startsWith("@") ? "NEO CONTROL" : "OPEN APPLICATION", 8, theme.muted);
            detail.setGravity(Gravity.CENTER); detail.setIncludeFontPadding(false);detail.setMaxLines(1);
            detail.setLetterSpacing(0.10f); LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, -2);
            dp.topMargin = px(4); card.addView(detail, dp);
            interactive(card, () -> launch(a), () -> appMenu(a));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -1, 1);
            if (i % theme.columns > 0) cp.leftMargin = px(12); row.addView(card, cp); cards.add(card);
        }
        int empty = (theme.columns - visible.size() % theme.columns) % theme.columns;
        for (int i=0;i<empty;i++) { View spacer = new View(this); LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0,-1,1); sp.leftMargin=px(12); row.addView(spacer,sp); }
        View target = cards.isEmpty() ? null : cards.get(0);
        for (View card : cards) if (focusedKey.equals(card.getTag())) target = card;
        if (target != null) target.requestFocus();else if(controlToggle!=null)controlToggle.requestFocus();
    }

    private void safeStart(Intent intent) {
        try { startActivity(intent); } catch (Exception e) { toast("This action is unavailable on this TV"); }
    }
    private void launch(App a) {
        switch (a.key) {
            case "@inputs": inputMenu(); return;
            case "@settings": settingsMenu(); return;
            case "@themes": themeMenu(); return;
            case "@timer": clockMenu(); return;
            case "@controls": prefs.edit().putBoolean("controls_collapsed",!prefs.getBoolean("controls_collapsed",false)).apply();focusedKey="@controls";renderApps();return;
            case "@add_apps":if("all".equals(activeCategory))categoriesMenu();else categoryAppsMenu(activeCategory,0);return;
            default: if(appLock.blocked(a.pkg)){askPin("Unlock "+a.label+" / "+appLock.minutes(a.pkg)+" minutes",()->{appLock.grant(a.pkg);safeStart(a.intent);});}else safeStart(a.intent);
        }
    }
    private void appMenu(App a) {
        List<String> labels = new ArrayList<>(Arrays.asList("Open"));
        List<Runnable> actions = new ArrayList<>(); actions.add(() -> launch(a));
        if (!a.key.startsWith("@")) {
            labels.add("Categories");actions.add(()->appCategoriesMenu(a));
            labels.add("Move left"); actions.add(() -> startMove(a, -1));
            labels.add("Move right"); actions.add(() -> startMove(a, 1));
            labels.add("Rename"); actions.add(() -> edit("App name", prefs.getString("name:"+a.key,a.label), false,
                    value -> { if (!value.trim().isEmpty()) prefs.edit().putString("name:"+a.key,value.trim()).apply(); renderApps(); }));
            if (!"com.android.tv.settings".equals(a.pkg) && !"com.realtek.tv.settings".equals(a.pkg)) {
                labels.add("Hide from Home"); actions.add(() -> { Set<String> hidden = new HashSet<>(prefs.getStringSet("hidden", Collections.emptySet())); hidden.add(a.key); prefs.edit().putStringSet("hidden",hidden).apply(); renderApps(); });
            }
            labels.add("App info"); actions.add(() -> safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+a.pkg))));
            if (!a.system) { labels.add("Uninstall"); actions.add(() -> safeStart(new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+a.pkg)))); }
        }
        else if(Arrays.asList("@inputs","@settings","@themes","@timer").contains(a.key)){
            labels.add("Hide this control");actions.add(()->{prefs.edit().putBoolean("control_visible:"+a.key,false).apply();focusedKey="@controls";renderApps();});
            labels.add("Minimize controls");actions.add(()->{prefs.edit().putBoolean("controls_collapsed",true).apply();focusedKey="@controls";renderApps();});
        }
        menu(a.label, labels, actions);
    }
    private void startMove(App app,int delta) {
        List<String> keys=new ArrayList<>();for(App item:orderedApps())if(!item.key.startsWith("@"))keys.add(item.key);
        moveOrder=keys;movingKey=app.key;focusedKey=app.key;shiftMove(delta);
        toast("Use Left / Right to move. OK saves; Back cancels.");
    }
    private void shiftMove(int delta) {
        if(moveOrder==null)return;
        int index=moveOrder.indexOf(movingKey),target=index+delta;
        if(index>=0 && target>=0 && target<moveOrder.size())Collections.swap(moveOrder,index,target);
        focusedKey=movingKey;renderApps();
    }
    private void finishMove(boolean save) {
        if(save && moveOrder!=null){JSONArray order=new JSONArray();for(String key:moveOrder)order.put(key);prefs.edit().putString(orderPreference(),order.toString()).apply();}
        movingKey=null;moveOrder=null;renderApps();toast(save?"App order saved":"Move cancelled");
    }
    private String orderPreference(){return "all".equals(activeCategory)?"order":"category_order:"+activeCategory;}

    private void settingsMenu() {
        menu("Neo TVLauncher Settings", Arrays.asList("Install apps remotely", "Screensaver", "App lock & parental controls", "App sections & categories", "Home controls", "Theme Studio", "Icon packs", "Clock & countdown", "Weather", "System updates", "Restore hidden apps", "Android TV settings", "Make Neo the Home launcher"),
                Arrays.asList(()->startActivity(new Intent(this,NeoRemoteInstallActivity.class)),this::screensaverMenu,this::parentalMenu,this::categoriesMenu,this::controlsMenu,this::themeMenu, this::iconPackMenu, this::clockMenu, this::showWeather,this::updatesMenu,
                () -> { prefs.edit().remove("hidden").apply(); renderApps(); toast("Hidden apps restored"); },
                () -> safeStart(new Intent(Settings.ACTION_SETTINGS)),this::homeSetup));
    }
    private void renderCategoryBar(){
        if(categoryBar==null)return;categoryBar.removeAllViews();
        List<String> ids=new ArrayList<>();ids.add("all");for(String id:categories.ids())if(categories.enabled(id))ids.add(id);
        for(String id:ids){
            TextView tab=text("all".equals(id)?"All":categories.name(id),10,id.equals(activeCategory)?theme.accent:theme.text);
            tab.setTag(R.id.neo_category_id,id);
            tab.setIncludeFontPadding(false);tab.setGravity(Gravity.CENTER);tab.setPadding(px(12),0,px(12),0);
            interactive(tab,()->{if(movingKey!=null)finishMove(false);activeCategory=id;prefs.edit().putString("active_category",id).apply();focusedKey="@controls";renderApps();},null);
            if(id.equals(activeCategory))tab.setBackground(theme.surface(unit,true));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-1);p.rightMargin=px(7);categoryBar.addView(tab,p);
        }
    }
    private void controlsMenu(){
        List<String> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>();
        labels.add(prefs.getBoolean("controls_collapsed",false)?"Expand controls":"Minimize controls");actions.add(()->{prefs.edit().putBoolean("controls_collapsed",!prefs.getBoolean("controls_collapsed",false)).apply();renderApps();});
        String[] keys={"@inputs","@settings","@themes","@timer"},names={"TV Inputs","Neo TVLauncher Settings","Theme Studio","Clock & Timer"};
        for(int i=0;i<keys.length;i++){final String key=keys[i];labels.add((prefs.getBoolean("control_visible:"+key,true)?"[Shown] ":"[Hidden] ")+names[i]);actions.add(()->{prefs.edit().putBoolean("control_visible:"+key,!prefs.getBoolean("control_visible:"+key,true)).apply();renderApps();controlsMenu();});}
        labels.add("Show all controls");actions.add(()->{android.content.SharedPreferences.Editor e=prefs.edit().putBoolean("controls_collapsed",false);for(String key:keys)e.putBoolean("control_visible:"+key,true);e.apply();renderApps();});
        menu("Home controls",labels,actions);
    }
    private void categoriesMenu(){
        List<String> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>();
        for(String id:categories.ids()){labels.add(categories.name(id)+(categories.enabled(id)?"  /  Shown":"  /  Hidden"));actions.add(()->categoryMenu(id));}
        labels.add("Add category");actions.add(()->edit("Category name","",false,name->{if(!name.trim().isEmpty()){categories.add(name);renderApps();categoriesMenu();}}));
        menu("Edit app categories",labels,actions);
    }
    private void categoryMenu(String id){
        List<String> labels=new ArrayList<>(Arrays.asList("Add or remove apps",categories.enabled(id)?"Hide category from Home":"Show category on Home","Show this category now","Rename category"));
        List<Runnable> actions=new ArrayList<>(Arrays.asList(()->categoryAppsMenu(id,0),()->{categories.enabled(id,!categories.enabled(id));renderApps();categoryMenu(id);},()->{categories.enabled(id,true);activeCategory=id;prefs.edit().putString("active_category",id).apply();renderApps();},()->edit("Category name",categories.name(id),false,name->{categories.rename(id,name);renderApps();categoryMenu(id);})));
        if(!NeoCategories.IDS.contains(id)){labels.add("Delete category");actions.add(()->{categories.remove(id);if(activeCategory.equals(id))activeCategory="all";renderApps();categoriesMenu();});}
        menu(categories.name(id),labels,actions);
    }
    private void categoryAppsMenu(String id,int focus){
        List<String> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>();Set<String> members=categories.members(id);
        for(int i=0;i<apps.size();i++){final App app=apps.get(i);final int at=i;labels.add((members.contains(app.key)?"[x] ":"[ ] ")+app.label);actions.add(()->{categories.toggle(id,app.key);renderApps();categoryAppsMenu(id,at);});}
        if(labels.isEmpty()){toast("No installed apps found");return;}menu("Apps in "+categories.name(id),labels,actions,focus);
    }
    private void appCategoriesMenu(App app){
        List<String> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>();
        for(String id:categories.ids()){labels.add((categories.members(id).contains(app.key)?"[x] ":"[ ] ")+categories.name(id));actions.add(()->{categories.toggle(id,app.key);renderApps();appCategoriesMenu(app);});}
        menu(app.label+" / Categories",labels,actions);
    }
    private void updatesMenu(){
        boolean disabled=Settings.Global.getInt(getContentResolver(),"ota_disable_automatic_update",0)==1;
        menu("System updates",Arrays.asList("Automatic restart updates: "+(disabled?"Disabled":"Managed by TV"),"Manufacturer update settings","Developer update settings","Background platform support"),
                Arrays.asList(()->toast("Use the companion installer to change automatic restart updates; manufacturer policies may differ"),()->safeStart(new Intent("android.settings.SYSTEM_UPDATE_SETTINGS")),()->safeStart(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)),()->toast("Android TV and Google TV are supported. webOS needs a separate LG app; firmware policies remain with each TV.")));
    }
    private void homeSetup() {
        menu("Set up Home button",Arrays.asList("Choose default Home app", "Enable Neo Home-button service", "Use Neo as default (Android 10+)"),
                Arrays.asList(() -> safeStart(new Intent(Settings.ACTION_HOME_SETTINGS)),
                () -> safeStart(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
                () -> { if(android.os.Build.VERSION.SDK_INT>=29) {
                    android.app.role.RoleManager roles=getSystemService(android.app.role.RoleManager.class);
                    if(roles!=null && roles.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {startActivityForResult(roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME),55);return;}
                } toast("Use the default Home app or accessibility setting on this TV"); }));
    }
    private void iconPackMenu() {
        toast("Finding installed icon packs");worker.execute(() -> {
            Bundle request=new Bundle();request.putString("operation","packs");Bundle result=NeoWorkerClient.blocking(this,NeoCatalogService.class,request,8000);
            ArrayList<Bundle> rows=result.getParcelableArrayList("packs");List<NeoIconPacks.Pack> packs=new ArrayList<>();
            if(rows!=null)for(Bundle row:rows)packs.add(new NeoIconPacks.Pack(row.getString("pkg"),row.getString("label")));else packs=iconPacks.installed();
            final List<NeoIconPacks.Pack> foundPacks=packs;
            main.post(() -> {
                if(closed)return;
                List<String> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>();
                labels.add("Default app icons");actions.add(() -> selectIconPack(""));
                for(NeoIconPacks.Pack pack:foundPacks){labels.add(pack.label);actions.add(() -> selectIconPack(pack.pkg));}
                menu(foundPacks.isEmpty()?"Icon packs / install an appfilter.xml pack first":"Installed icon packs",labels,actions);
            });
        });
    }
    private void selectIconPack(String pkg) {
        prefs.edit().putString("icon_pack",pkg).apply();
        worker.execute(() -> { List<App> found=discoverApps();main.post(() -> {if(!closed){apps.clear();apps.addAll(found);renderApps();toast(pkg.isEmpty()?"Default icons restored":"Icon pack applied; unmatched apps keep their icons");}}); });
    }
    private void inputMenu() {
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        for (NeoInputs.Source input : NeoInputs.sources(this)) {
            labels.add(input.label + (input.disconnected ? " (disconnected)" : ""));
            actions.add(() -> { if (input.passthrough) safeStart(new Intent(Intent.ACTION_VIEW, android.media.tv.TvContract.buildChannelUriForPassthroughInput(input.id))); else safeStart(new Intent(Intent.ACTION_VIEW, android.media.tv.TvContract.Channels.CONTENT_URI)); });
        }
        if (labels.isEmpty()) { toast("No TV inputs reported"); return; }
        menu("TV Inputs", labels, actions);
    }
    private void themeMenu() {
        menu("Theme Studio  /  " + theme.name,
                Arrays.asList("Aurora Glass", "Violet Dusk", "Midnight Contrast", "Import theme JSON", "Reduce motion: " + (prefs.getBoolean("reduced_motion",false) ? "On" : "Off")),
                Arrays.asList(() -> chooseTheme("aurora"), () -> chooseTheme("violet"), () -> chooseTheme("midnight"),
                () -> { Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE); try { startActivityForResult(pick, 42); } catch (Exception e) { toast("No file picker installed. Bundled themes are available."); } },
                () -> { prefs.edit().putBoolean("reduced_motion",!prefs.getBoolean("reduced_motion",false)).apply(); build(); }));
    }
    private void chooseTheme(String id) {
        worker.execute(()->{try { String raw = NeoTheme.read(getAssets().open("neo/themes/"+id+".json"));validateThemeRemotely(raw);main.post(()->{if(!closed){prefs.edit().putString("theme_json",raw).apply();build();}}); }
        catch (Exception e) { main.post(()->toast("Theme could not be loaded")); }});
    }
    private void validateThemeRemotely(String raw)throws Exception{Bundle input=new Bundle();input.putString("raw",raw);Bundle result=NeoWorkerClient.blocking(this,NeoThemeService.class,input,5000);if(!result.getBoolean("valid",false))throw new IllegalArgumentException("Invalid theme");NeoTheme.validateWallpaper(this,NeoTheme.parse(raw));}
    /** PC-assisted theme import for Android TVs without a document picker. */
    private boolean acceptIntentTheme(Intent intent) {
        String encoded=intent==null ? null : intent.getStringExtra("neo_theme_base64");
        if(encoded==null)return false;
        intent.removeExtra("neo_theme_base64");
        worker.execute(()->{try {
            if(encoded.length()>45000)throw new IllegalArgumentException();
            String raw=new String(android.util.Base64.decode(encoded,android.util.Base64.DEFAULT),java.nio.charset.StandardCharsets.UTF_8);
            validateThemeRemotely(raw);
            main.post(()->{if(!closed){prefs.edit().putString("theme_json",raw).apply();build();}});
        } catch(Exception e) { main.post(()->toast("Invalid theme. Previous theme preserved.")); }});return false;
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if(request==46 && result==RESULT_OK && data!=null && data.getData()!=null){importSaver(data.getData());return;}
        if (request == 42 && result == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData(); worker.execute(() -> {
                try { String raw = NeoTheme.read(getContentResolver().openInputStream(uri)); validateThemeRemotely(raw);
                    main.post(() -> { if (!closed) { prefs.edit().putString("theme_json",raw).apply(); build(); toast("Theme applied"); } });
                } catch (Exception e) { main.post(() -> toast("Invalid theme. Previous theme preserved.")); }
            });
        }
    }

    private void updateClock() {
        if (headerTime == null) return;
        Date now = new Date(); headerTime.setText(new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(now));
        headerDate.setText(new SimpleDateFormat("EEE, d MMM",Locale.getDefault()).format(now));
        long end = prefs.getLong("timer_end",0);
        boolean dual=end==0 && prefs.getBoolean("dual_clock",false);
        dualClockRow.getChildAt(1).setVisibility(dual?View.VISIBLE:View.GONE);firstZoneLabel.setVisibility(dual?View.VISIBLE:View.GONE);
        if(dual){
            String zone=prefs.getString("clock_zone_1",java.util.TimeZone.getDefault().getID());
            String zone2=prefs.getString("clock_zone_2","America/New_York");
            SimpleDateFormat f=new SimpleDateFormat("HH:mm:ss",Locale.US);f.setTimeZone(java.util.TimeZone.getTimeZone(zone));heroTime.setTime(f.format(now));
            f.setTimeZone(java.util.TimeZone.getTimeZone(zone2));secondTime.setTime(f.format(now));
            heroLabel.setText("DUAL LIVE CLOCK");firstZoneLabel.setText(zone.replace('_',' '));secondLabel.setText(zone2.replace('_',' '));return;
        }
        if (end > 0) {
            long seconds = Math.max(0,(end-System.currentTimeMillis()+999)/1000);
            heroTime.setTime(String.format(Locale.US,"%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60));
            heroLabel.setText(seconds > 0 ? "COUNTDOWN" : "TIMER COMPLETE");
        } else {
            heroTime.setTime(new SimpleDateFormat("HH:mm:ss",Locale.US).format(now));
            heroLabel.setText("LOCAL TIME  /  " + new SimpleDateFormat("EEEE",Locale.getDefault()).format(now).toUpperCase(Locale.getDefault()));
        }
    }
    private void clockMenu() {
        menu("Immersive clock & countdown", Arrays.asList("Show live clock", "Show Dual Live clock", "5 minute countdown", "15 minute countdown", "30 minute countdown", "Custom minutes", "Cancel countdown"),
                Arrays.asList(() -> {prefs.edit().putBoolean("dual_clock",false).apply();setTimer(0);}, this::dualClockMenu, () -> setTimer(5), () -> setTimer(15), () -> setTimer(30),
                () -> edit("Countdown minutes (1-1440)","10",true,value -> { try { int minutes=Integer.parseInt(value); if(minutes<1||minutes>1440) throw new IllegalArgumentException(); setTimer(minutes); } catch(Exception e) { toast("Enter 1 to 1440 minutes"); } }), () -> setTimer(0)));
    }
    private void setTimer(int minutes) { prefs.edit().putLong("timer_end",minutes == 0 ? 0 : System.currentTimeMillis()+minutes*60000L).apply(); updateClock(); }

    @Override public void onUserInteraction(){super.onUserInteraction();lastInteraction=android.os.SystemClock.elapsedRealtime();}
    private void applySaverWakePolicy(){if(prefs.getBoolean("saver_enabled",false))getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    private void screensaverMenu(){
        menu("Neo screensaver",Arrays.asList("Screensaver: "+(prefs.getBoolean("saver_enabled",false)?"Enabled":"Disabled"),"Start after: "+prefs.getInt("saver_minutes",5)+" minutes","Wallpaper: "+prefs.getString("saver_wallpaper","black"),"Spaceship engine exhaust: "+(prefs.getBoolean("saver_ship_engines",false)?"Blue breathing":"Off"),"Preview screensaver","Use Neo instead of Android / Google TV screensaver"),Arrays.asList(()->{prefs.edit().putBoolean("saver_enabled",!prefs.getBoolean("saver_enabled",false)).apply();lastInteraction=android.os.SystemClock.elapsedRealtime();applySaverWakePolicy();screensaverMenu();},this::screensaverTimerMenu,this::screensaverWallpaperMenu,this::toggleShipEngines,()->startActivity(new Intent(this,NeoScreensaverActivity.class)),()->menu("Replace TV screensaver",Arrays.asList("Choose Neo TVLauncher Screensaver in Android settings","How override works"),Arrays.asList(()->safeStart(new Intent("android.settings.DREAM_SETTINGS")),()->toast("Select Neo TVLauncher Screensaver in the TV settings. Neo's idle timer applies on Home; the TV controls the system idle delay. Some TVs require the companion CLI override.")))));
    }
    private void toggleShipEngines(){prefs.edit().putBoolean("saver_ship_engines",!prefs.getBoolean("saver_ship_engines",false)).apply();screensaverMenu();}
    private void screensaverTimerMenu(){List<String> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>();for(int m:new int[]{2,5,10,15,20,25,30}){final int minutes=m;labels.add(m+" minutes");actions.add(()->{prefs.edit().putInt("saver_minutes",minutes).apply();lastInteraction=android.os.SystemClock.elapsedRealtime();screensaverMenu();});}menu("Screensaver idle timer",labels,actions);}
    private void screensaverWallpaperMenu(){menu("Screensaver wallpaper",Arrays.asList("Black screen / glowing custom text","Live Home landscape / water ripples & distant lights","Deep space / original black hole","Custom wallpaper"),Arrays.asList(()->edit("Text for black screensaver (max 60 characters)",prefs.getString("saver_text","Neo TVLauncher"),false,value->{if(value.trim().isEmpty()||value.length()>60){toast("Enter 1 to 60 characters");return;}prefs.edit().putString("saver_text",value.trim()).apply();selectSaver("black");}),()->selectSaver("lake"),()->selectSaver("space"),this::customSaverMenu));}
    private void customSaverMenu(){menu("Custom screensaver image",Arrays.asList("Choose image from TV storage","Use image uploaded by companion CLI"),Arrays.asList(()->{Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);try{startActivityForResult(pick,46);}catch(Exception e){toast("No image picker installed. Use companion CLI option 20.");}},()->importSaver(Uri.fromFile(new java.io.File(getExternalFilesDir("screensaver"),"custom-image")))));}
    private void selectSaver(String mode){prefs.edit().putString("saver_wallpaper",mode).apply();screensaverMenu();}
    private void importSaver(Uri uri){toast("Importing screensaver image");worker.execute(()->{try{
        java.io.File temp=new java.io.File(getFilesDir(),"neo-screensaver-pending");
        try(java.io.InputStream in=getContentResolver().openInputStream(uri);java.io.OutputStream out=new java.io.FileOutputStream(temp)){byte[] buffer=new byte[8192];int n,total=0;while((n=in.read(buffer))!=-1){total+=n;if(total>20000000)throw new IllegalArgumentException("Image too large");out.write(buffer,0,n);}}
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(temp.getPath(),bounds);if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>20000||bounds.outHeight>20000)throw new IllegalArgumentException("Invalid image");
        java.io.File dest=new java.io.File(getFilesDir(),"neo-screensaver-image");if(!temp.renameTo(dest))throw new IllegalStateException("Import failed");main.post(()->{if(!closed)selectSaver("custom");});
    }catch(Exception e){main.post(()->toast("Image import failed. Choose an image under 20 MB."));}});}

    private void dualClockMenu(){
        menu("Dual live clocks",Arrays.asList("Show selected clocks","First clock: "+prefs.getString("clock_zone_1",java.util.TimeZone.getDefault().getID()),"Second clock: "+prefs.getString("clock_zone_2","America/New_York")),Arrays.asList(()->{prefs.edit().putBoolean("dual_clock",true).putLong("timer_end",0).apply();updateClock();},()->zoneMenu(1),()->zoneMenu(2)));
    }
    private void zoneMenu(int which){
        List<String> regions=new ArrayList<>(Arrays.asList("Africa","America","Asia","Atlantic","Australia","Europe","Indian","Pacific","Etc"));List<Runnable> actions=new ArrayList<>();
        for(String region:regions)actions.add(()->{List<String> zones=new ArrayList<>();List<Runnable> picks=new ArrayList<>();for(String zone:java.util.TimeZone.getAvailableIDs())if(zone.startsWith(region+"/")){zones.add(zone.replace('_',' '));picks.add(()->{prefs.edit().putString("clock_zone_"+which,zone).apply();dualClockMenu();});}menu("Clock "+which+" / "+region,zones,picks);});menu("Clock "+which+" / region",regions,actions);
    }
    private void pinInput(String title,TextResult done){
        Dialog dialog=new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=column();panel.setPadding(px(22),px(20),px(22),px(18));panel.setBackground(theme.surface(unit,false));
        TextView heading=text(title,15,theme.text);heading.setGravity(Gravity.CENTER);panel.addView(heading,new LinearLayout.LayoutParams(-1,-2));
        TextView mask=text("○  ○  ○  ○",24,theme.accent);mask.setGravity(Gravity.CENTER);panel.addView(mask,new LinearLayout.LayoutParams(-1,px(54)));
        StringBuilder pin=new StringBuilder();final View[] first=new View[1];
        String[] keys={"1","2","3","4","5","6","7","8","9","Delete","0","OK"};
        for(int row=0;row<4;row++){LinearLayout line=new LinearLayout(this);for(int col=0;col<3;col++){
            String key=keys[row*3+col];TextView button=text(key,16,theme.text);button.setGravity(Gravity.CENTER);
            interactive(button,()->{if(key.equals("OK")){if(pin.length()!=4){toast("Enter all four digits");return;}String value=pin.toString();pin.setLength(0);dialog.dismiss();done.accept(value);return;}if(key.equals("Delete")){if(pin.length()>0)pin.deleteCharAt(pin.length()-1);}else if(pin.length()<4)pin.append(key);
                StringBuilder dots=new StringBuilder();for(int i=0;i<4;i++)dots.append(i<pin.length()?"●  ":"○  ");mask.setText(dots.toString().trim());},null);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,px(43),1);lp.setMargins(px(3),px(3),px(3),px(3));line.addView(button,lp);if(first[0]==null)first[0]=button;
        }panel.addView(line,new LinearLayout.LayoutParams(-1,-2));}
        TextView cancel=text("Cancel",13,theme.muted);cancel.setGravity(Gravity.CENTER);interactive(cancel,dialog::dismiss,null);panel.addView(cancel,new LinearLayout.LayoutParams(-1,px(40)));
        dialog.setContentView(panel);dialog.setOnDismissListener(d->pin.setLength(0));dialog.show();if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);dialog.getWindow().setLayout(Math.min(px(360),getResources().getDisplayMetrics().widthPixels-px(36)),-2);dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);}first[0].requestFocus();activeDialog=dialog;
    }
    private void askPin(String title,Runnable done){pinInput(title,pin->{if(appLock.verify(pin))done.run();else toast("Incorrect PIN or temporary lockout. After 5 attempts wait 30 seconds.");});}
    private void createPin(Runnable done){pinInput("Set 4 digit parental PIN",pin->pinInput("Confirm parental PIN",confirm->{if(!pin.equals(confirm)){toast("PINs did not match");return;}appLock.setPin(pin);done.run();}));}
    private void parentalMenu(){if(!appLock.configured()){createPin(this::parentalOptions);return;}askPin("Parental controls PIN",this::parentalOptions);}
    private void parentalOptions(){
        menu("App lock / timed access",Arrays.asList("Choose apps and access durations","Change parental PIN","Lock all apps now","Enable background enforcement","How timed access works"),Arrays.asList(this::lockedAppsMenu,()->createPin(this::parentalOptions),()->{appLock.revoke();toast("All protected app sessions ended");},()->safeStart(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),()->toast("PIN opens a 15-240 minute access window. Time continues outside the app. Enable Neo accessibility to return Home at expiry. Android Settings stays available; this is not a device-owner lock.")));
    }
    private void lockedAppsMenu(){List<String> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>();Set<String> seen=new HashSet<>();for(App a:apps)if(appLock.eligible(a.pkg)&&!a.key.startsWith("@")&&seen.add(a.pkg)){labels.add(a.label+ (appLock.enabled(a.pkg)?" / Locked / "+appLock.minutes(a.pkg)+" min":" / Unlocked"));actions.add(()->lockAppMenu(a));}menu("App locks",labels,actions);}
    private void lockAppMenu(App a){List<String> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>();labels.add("Disable lock");actions.add(()->{appLock.configure(a.pkg,false,15);lockedAppsMenu();});for(int m=15;m<=240;m+=15){final int minutes=m;labels.add("Require PIN / allow "+m+" minutes");actions.add(()->{appLock.configure(a.pkg,true,minutes);toast("App lock enabled. Enable Neo accessibility for expiry enforcement.");lockedAppsMenu();});}menu(a.label+" / access window",labels,actions);}
    private void handleLockedIntent(){String pkg=getIntent().getStringExtra("neo_locked_package");if(pkg==null)return;getIntent().removeExtra("neo_locked_package");if(!appLock.blocked(pkg))return;main.post(()->{if(closed)return;if(activeDialog!=null)activeDialog.dismiss();askPin("App locked / PIN for "+appLock.minutes(pkg)+" minutes",()->{appLock.grant(pkg);Intent launch=getPackageManager().getLeanbackLaunchIntentForPackage(pkg);if(launch==null)launch=getPackageManager().getLaunchIntentForPackage(pkg);if(launch!=null)safeStart(launch);});});}

    private void refreshWeather(boolean force) {
        if(!NeoForecast.enabled(this))return;
        Bundle request=new Bundle();android.content.SharedPreferences weather=NeoForecast.prefs(this);
        request.putBoolean("force",force);request.putString("units",NeoForecast.units(this));request.putBoolean("manual",NeoForecast.manual(this));request.putString("place",NeoForecast.place(this));
        request.putString("source",NeoForecast.source(this));request.putFloat("lat",weather.getFloat(NeoForecast.K_LAT,0));request.putFloat("lon",weather.getFloat(NeoForecast.K_LON,0));
        NeoWorkerClient.request(this,NeoWeatherService.class,request,55000,result->{if(closed)return;String raw=result.getString("weather");if(raw!=null){NeoForecast.prefs(this).edit().putString(NeoForecast.K_CACHE,raw).putLong(NeoForecast.K_CACHE_AT,System.currentTimeMillis()).apply();}renderWeather();});
    }
    private void renderWeather() {
        if (weatherTitle == null) return;
        NeoForecast.Data data = NeoForecast.cached(this);
        if (data == null) { weatherNow.setText("Forecast unavailable"); weatherDetails.setText("Select to refresh or choose a city"); return; }
        weatherTitle.setText(data.place.toUpperCase(Locale.getDefault()));
        weatherNow.setText(NeoForecast.formatTemp(this,data.temp) + "  " + NeoForecast.describe(this,data.code,data.day));
        weatherNow.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(16));
        weatherDetails.setText("Feels " + NeoForecast.formatTemp(this,data.feels) + "   H " + NeoForecast.formatTemp(this,data.max) + "  L " + NeoForecast.formatTemp(this,data.min));
        StringBuilder forecast = new StringBuilder();
        for (int i=0;i<Math.min(3,data.days.size());i++) { NeoForecast.Day d = data.days.get(i); if(i>0) forecast.append("    "); forecast.append(d.label).append(' ').append(NeoForecast.formatTemp(this,d.max)); }
        weatherForecast.setText(forecast);
    }
    private void showWeather() {
        NeoForecast.Data d = NeoForecast.cached(this);
        String title = d == null ? "Weather" : d.place + " / " + NeoForecast.formatTemp(this,d.temp) + " / " + NeoForecast.describe(this,d.code,d.day);
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        if (d != null) {
            labels.add("Humidity " + d.humidity + "%   Wind " + d.wind + " " + d.windUnit); actions.add(() -> showWeather());
            for (NeoForecast.Day day : d.days) { labels.add(day.label + "   " + NeoForecast.formatTemp(this,day.min) + " / " + NeoForecast.formatTemp(this,day.max) + "   " + NeoForecast.describe(this,day.code,true)); actions.add(() -> showWeather()); }
        }
        labels.add("Refresh forecast"); actions.add(() -> { refreshWeather(true); toast("Refreshing forecast"); });
        labels.add("Temperature unit: " + NeoForecast.units(this).toUpperCase(Locale.US)); actions.add(() -> { NeoForecast.prefs(this).edit().putString(NeoForecast.K_UNITS,NeoForecast.units(this).equals("c")?"f":"c").apply(); NeoForecast.changed(this); refreshWeather(true); });
        labels.add("Choose city"); actions.add(() -> edit("Weather city","",false, this::findCity));
        menu(title,labels,actions);
    }
    private void findCity(String name) {
        if (name.trim().isEmpty()) return;
        toast("Searching cities");Bundle request=new Bundle();request.putString("operation","search");request.putString("query",name.trim());
        NeoWorkerClient.request(this,NeoWeatherService.class,request,25000,result->{
            if(closed)return;ArrayList<Bundle> rows=result.getParcelableArrayList("places");if(rows==null){toast("City lookup unavailable");return;}
            List<String> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>();
            for(Bundle row:rows){NeoForecast.Place place=new NeoForecast.Place();place.name=row.getString("name");place.detail=row.getString("detail");place.lat=row.getDouble("lat");place.lon=row.getDouble("lon");labels.add(place.name+", "+place.detail);actions.add(()->{NeoForecast.setPlace(this,place);refreshWeather(true);});}
            if(labels.isEmpty())toast("No matching city");else menu("Choose city",labels,actions);
        });
    }

    private void menu(String title, List<String> labels, List<Runnable> actions) {
        menu(title,labels,actions,0);
    }
    private void menu(String title, List<String> labels, List<Runnable> actions,int focusIndex) {
        stopAnalog();
        if (closed) return; if (activeDialog != null) activeDialog.dismiss();
        Dialog dialog = new Dialog(this); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = column(); panel.setPadding(px(20),px(18),px(20),px(16));
        panel.setClipChildren(true);panel.setClipToPadding(true);
        panel.setBackground(theme.surface(unit,false)); TextView heading=text(title,17,theme.text);
        heading.setPadding(0,0,0,px(15)); panel.addView(heading);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false);scroll.setClipToPadding(true);scroll.setClipChildren(true);
        scroll.setSmoothScrollingEnabled(false);scroll.setVerticalFadingEdgeEnabled(false);
        scroll.setPadding(px(6),px(4),px(6),px(4));scroll.setVerticalScrollBarEnabled(true);
        LinearLayout list=column();list.setClipChildren(true);list.setPadding(px(2),px(8),px(2),px(16)); scroll.addView(list);
        for(int i=0;i<labels.size();i++) {
            final Runnable action=actions.get(i); TextView item=text(labels.get(i),13,theme.text);
            item.setPadding(px(15),px(12),px(15),px(12));
            interactive(item,() -> { dialog.dismiss(); action.run(); },null);
            item.setMaxLines(3);item.setMinHeight(px(43));
            item.setOnFocusChangeListener((v,focused)->{
                v.setBackground(theme.surface(unit,focused));
                if(focused)scroll.postDelayed(()->{
                    android.graphics.Rect visible=new android.graphics.Rect();v.getDrawingRect(visible);
                    visible.inset(0,-px(12));v.requestRectangleOnScreen(visible,true);
                    int bottom=v.getBottom()+px(10),top=v.getTop()-px(6);
                    int visibleBottom=scroll.getScrollY()+scroll.getHeight()-scroll.getPaddingBottom();
                    if(bottom>visibleBottom)scroll.scrollTo(0,bottom-scroll.getHeight()+scroll.getPaddingBottom());
                    else if(top<scroll.getScrollY()+scroll.getPaddingTop())scroll.scrollTo(0,Math.max(0,top-scroll.getPaddingTop()));
                },80);
            });
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=px(7); list.addView(item,lp);
        }
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); dialog.setContentView(panel);
        Window window=dialog.getWindow(); if(window != null) { window.setBackgroundDrawableResource(android.R.color.transparent); window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); WindowManager.LayoutParams p=window.getAttributes(); p.dimAmount=0.45f; window.setAttributes(p); }
        dialog.show(); if(window != null) {
            int width=Math.min(px(500),getResources().getDisplayMetrics().widthPixels-px(48));
            int contentHeight=px(90+labels.size()*53);
            window.setLayout(width,Math.min(contentHeight,getResources().getDisplayMetrics().heightPixels-px(70)));
        }
        if(list.getChildCount()>0)list.getChildAt(Math.max(0,Math.min(focusIndex,list.getChildCount()-1))).requestFocus(); activeDialog=dialog;
    }
    interface TextResult { void accept(String value); }
    private void edit(String title,String initial,boolean numeric,TextResult result) {
        EditText input = new EditText(this); input.setSingleLine(true); input.setText(initial); input.setTextColor(theme.text);
        input.setBackground(theme.surface(unit,false)); input.setPadding(px(14),px(12),px(14),px(12));
        if(numeric)input.setInputType(InputType.TYPE_CLASS_NUMBER);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(input)
                .setPositiveButton("Save",(d,w)->result.accept(input.getText().toString())).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(d -> { if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(theme.surface(unit,false)); dialog.getButton(-1).setTextColor(theme.accent); dialog.getButton(-2).setTextColor(theme.muted); });
        dialog.show(); activeDialog=dialog;input.requestFocus();
    }
    private void toast(String message) { if(!closed)Toast.makeText(this,message,Toast.LENGTH_SHORT).show(); }
    @Override protected void onResume() {
        super.onResume(); lastInteraction=android.os.SystemClock.elapsedRealtime();applySaverWakePolicy();handleLockedIntent(); resumed=true; main.removeCallbacks(ticker); main.post(ticker); refreshWeather(false);
        worker.execute(() -> { List<App> found=discoverApps(); main.post(() -> { if(!closed) { apps.clear(); apps.addAll(found); renderApps(); } }); });
    }
    @Override protected void onPause() { resumed=false;stopAnalog(); main.removeCallbacks(ticker);if(heroTime!=null)heroTime.stop();if(secondTime!=null)secondTime.stop(); super.onPause(); }
    @Override protected void onDestroy() { closed=true; main.removeCallbacksAndMessages(null); worker.shutdownNow(); if(activeDialog!=null)activeDialog.dismiss(); super.onDestroy(); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent);lastInteraction=android.os.SystemClock.elapsedRealtime(); if(movingKey!=null)finishMove(false); if(activeDialog!=null)activeDialog.dismiss(); if(acceptIntentTheme(intent))build(); if(!cards.isEmpty())cards.get(0).requestFocus(); handleLockedIntent(); }
    @Override public boolean onKeyUp(int key,KeyEvent event) { if(key==KeyEvent.KEYCODE_MENU) { settingsMenu(); return true; } return super.onKeyUp(key,event); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key=event.getKeyCode();
        if(event.getAction()==KeyEvent.ACTION_DOWN)lastInteraction=android.os.SystemClock.elapsedRealtime();
        if(swallowMoveUp && event.getAction()==KeyEvent.ACTION_UP){swallowMoveUp=false;return true;}
        if(movingKey!=null){
            if(event.getAction()==KeyEvent.ACTION_DOWN){
                if(key==KeyEvent.KEYCODE_DPAD_LEFT)shiftMove(-1);
                else if(key==KeyEvent.KEYCODE_DPAD_RIGHT)shiftMove(1);
                else if((key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER) && event.getRepeatCount()==0){finishMove(true);swallowMoveUp=true;}
                else if(key==KeyEvent.KEYCODE_BACK && event.getRepeatCount()==0){finishMove(false);swallowMoveUp=true;}
            }
            return true;
        }
        int dx=0,dy=0;
        switch(key){
            case KeyEvent.KEYCODE_DPAD_LEFT:dx=-1;break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:dx=1;break;
            case KeyEvent.KEYCODE_DPAD_UP:dy=-1;break;
            case KeyEvent.KEYCODE_DPAD_DOWN:dy=1;break;
            case KeyEvent.KEYCODE_DPAD_UP_LEFT:dx=-1;dy=-1;break;
            case KeyEvent.KEYCODE_DPAD_UP_RIGHT:dx=1;dy=-1;break;
            case KeyEvent.KEYCODE_DPAD_DOWN_LEFT:dx=-1;dy=1;break;
            case KeyEvent.KEYCODE_DPAD_DOWN_RIGHT:dx=1;dy=1;break;
        }
        if(dy!=0 && categoryBar!=null && getCurrentFocus()!=null && getCurrentFocus().getParent()==categoryBar){
            if(event.getAction()==KeyEvent.ACTION_DOWN){
                if(dy<0 && controlToggle!=null)controlToggle.requestFocus();
                else if(dy>0 && !cards.isEmpty())cards.get(0).requestFocus();
            }
            return true;
        }
        if(dy>0 && getCurrentFocus()==controlToggle && categoryBar!=null && categoryBar.getChildCount()>0){
            if(event.getAction()==KeyEvent.ACTION_DOWN)categoryBar.getChildAt(0).requestFocus();
            return true;
        }
        if((dx!=0 || dy!=0) && cards.contains(getCurrentFocus())){
            if(event.getAction()==KeyEvent.ACTION_DOWN)navigateApps(dx,dy);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    /** Row-major horizontal flow with bounded edges and an upward exit to categories. */
    private boolean navigateApps(int dx,int dy) {
        int index=cards.indexOf(getCurrentFocus()),count=cards.size();
        if(index<0 || count==0)return false;
        if(movingKey!=null){if(dx!=0)shiftMove(dx);return true;}
        int target=NeoGridNavigation.target(index,count,theme.columns,dx,dy);
        if(target<0 && dy<0 && categoryBar!=null && categoryBar.getChildCount()>0){
            View tab=categoryBar.getChildAt(0);
            for(int i=0;i<categoryBar.getChildCount();i++)if(activeCategory.equals(categoryBar.getChildAt(i).getTag(R.id.neo_category_id)))tab=categoryBar.getChildAt(i);
            tab.requestFocus();return true;
        }
        if(android.util.Log.isLoggable("NeoNav",android.util.Log.DEBUG))android.util.Log.d("NeoNav","direction="+dx+","+dy+" index="+index+" target="+target+" count="+count);
        if(target>=0 && target<count)cards.get(target).requestFocus();
        return true;
    }
    private void stopAnalog(){analogX=0;analogY=0;main.removeCallbacks(analogRepeat);}
    @Override public boolean onGenericMotionEvent(android.view.MotionEvent event){
        if((event.getSource()&android.view.InputDevice.SOURCE_JOYSTICK)==android.view.InputDevice.SOURCE_JOYSTICK){
            float x=event.getAxisValue(android.view.MotionEvent.AXIS_HAT_X),y=event.getAxisValue(android.view.MotionEvent.AXIS_HAT_Y);
            if(Math.abs(x)<0.3f)x=event.getAxisValue(android.view.MotionEvent.AXIS_X);
            if(Math.abs(y)<0.3f)y=event.getAxisValue(android.view.MotionEvent.AXIS_Y);
            int dx=x>0.45f?1:x< -0.45f?-1:0,dy=y>0.45f?1:y< -0.45f?-1:0;
            if(dx==0 && dy==0){stopAnalog();return true;}
            lastInteraction=android.os.SystemClock.elapsedRealtime();
            if(cards.contains(getCurrentFocus())){
                if(dx!=analogX || dy!=analogY){stopAnalog();analogX=dx;analogY=dy;navigateApps(dx,dy);main.postDelayed(analogRepeat,300);}
                return true;
            }
        }
        return super.onGenericMotionEvent(event);
    }
    @Override public void onBackPressed() {
        int index=cards.indexOf(getCurrentFocus());
        if(index>=0 && index<theme.columns && categoryBar!=null && categoryBar.getChildCount()>0){for(int i=0;i<categoryBar.getChildCount();i++)if(activeCategory.equals(categoryBar.getChildAt(i).getTag(R.id.neo_category_id))){categoryBar.getChildAt(i).requestFocus();return;}categoryBar.getChildAt(0).requestFocus();return;}
        if(!cards.isEmpty())cards.get(0).requestFocus();else if(controlToggle!=null)controlToggle.requestFocus();
    }
}
