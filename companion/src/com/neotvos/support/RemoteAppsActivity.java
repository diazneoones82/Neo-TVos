package com.neotvos.support;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.neotv.protocol.NeoPairing;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Guided QR pairing, user-app base/split transfer and official TV Play Store handoff. */
public final class RemoteAppsActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private RemoteTvClient client;private JSONObject tv;
    private AlertDialog activeDialog;private ScrollView contentScroll;private LinearLayout body;private TextView status;private ActionProgress progress;private long pageId;private String lastState="";
    private boolean busy,closed;private String watching="";
    private int bg=0xff071326,card=0xe61a3049,text=0xfff4faff,accent=0xff8feaff,muted=0xffb6cee2;
    private final Runnable poll=new Runnable(){public void run(){if(closed||client==null||watching.isEmpty())return;if(busy){ui.postDelayed(this,800);return;}busy=true;RemoteTvClient selected=client;long screen=pageId;worker.execute(()->{
        try{JSONObject state=selected.get("/status");ui.post(()->{busy=false;if(closed||selected!=client||screen!=pageId)return;renderStatus(state);});}
        catch(Exception failure){ui.post(()->{busy=false;if(!closed&&selected==client&&screen==pageId){watching="";String message="Connection ended. Check the TV for the final installation result. "+failure.getMessage();status.setText(message);progress.stop(message,false);alert("Result not confirmed",message,null);}});}
    });}};
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);loadTheme();getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);home();if(getIntent().getData()!=null)connect(getIntent().getData().toString());}
    private void loadTheme(){try{String id=getSharedPreferences("MainActivity",0).getString("theme","aurora");try(InputStream input=getAssets().open("themes/"+id+".json")){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[2048];for(int n;(n=input.read(buffer))!=-1;)bytes.write(buffer,0,n);JSONObject tokens=new JSONObject(bytes.toString("UTF-8"));card=Color.parseColor(tokens.getString("glass"));text=Color.parseColor(tokens.getString("text"));accent=Color.parseColor(tokens.getString("accent"));muted=Color.parseColor(tokens.getString("muted"));}}catch(Exception ignored){}}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private GradientDrawable surface(){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{card,0xe6102035});d.setCornerRadius(dp(18));d.setStroke(dp(1),0x6095cfff);return d;}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(7),0,dp(7));return v;}
    private void page(String title){
        pageId++;ui.removeCallbacks(poll);
        android.widget.FrameLayout root=new FrameLayout(this);root.setBackgroundColor(bg);
        try(InputStream input=getAssets().open("companion-lake.jpg")){ImageView image=new ImageView(this);image.setImageBitmap(BitmapFactory.decodeStream(input));image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setAlpha(.32f);root.addView(image,new FrameLayout.LayoutParams(-1,-1));}catch(Exception ignored){}
        ScrollView scroll=new ScrollView(this);contentScroll=scroll;body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(22),dp(22),dp(22),dp(28));scroll.addView(body);root.addView(scroll);setContentView(root);
        body.addView(text("NEO TVLAUNCHER COMPANION",12,accent));body.addView(text(title,26,text));status=text("",15,muted);body.addView(status);progress=new ActionProgress(this,card,text,accent,muted);body.addView(progress,new LinearLayout.LayoutParams(-1,-2));
    }
    private void paragraph(String value){body.addView(text(value,15,muted));}
    private void button(String label,Runnable action){TextView item=text(label,17,text);item.setPadding(dp(17),dp(15),dp(17),dp(15));item.setBackground(surface());item.setClickable(true);item.setFocusable(true);item.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(7),0,dp(7));body.addView(item,lp);}
    private void beginProgress(String name){progress.begin(name);contentScroll.smoothScrollTo(0,0);}
    private void alert(String title,String message,Runnable yes){if(activeDialog!=null)activeDialog.dismiss();AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton(yes==null?"OK":"Continue",(d,w)->{if(yes!=null)yes.run();}).setNegativeButton(yes==null?null:"Cancel",null).create();dialog.show();activeDialog=dialog;if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(surface());TextView content=dialog.findViewById(android.R.id.message);if(content!=null)content.setTextColor(text);dialog.getButton(-1).setTextColor(accent);}
    private void home(){
        page("Install apps remotely");
        if(client==null){paragraph("On the TV: Neo Settings → Install apps remotely. Keep that TV screen open. Connect both devices to the same Wi-Fi/Ethernet network; USB debugging is not required for this feature.");button("Scan TV QR",()->startActivityForResult(new Intent(this,QrScanActivity.class),51));button("Paste pairing link",()->input("Pairing link","neotv://pair/",this::connect));}
        else{
            paragraph("Connected TV: "+tv.optString("model")+"\nAndroid "+tv.optString("android")+" · "+client.pairing.host);
            button("Choose an app installed on this phone",this::phoneApps);
            button("Install from Google Play on TV",this::playStore);
            button("Review current TV request",this::watch);
            button("Disconnect from TV",()->{client.disconnect();client=null;tv=null;watching="";ui.removeCallbacks(poll);home();});
        }
        button("Back to TV setup",this::finish);
    }
    private interface Value{void accept(String value);}
    private void input(String title,String initial,Value result){EditText field=new EditText(this);field.setText(initial);field.setTextColor(text);field.setSingleLine(true);field.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);field.setPadding(dp(16),dp(14),dp(16),dp(14));AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(field).setPositiveButton("Continue",(d,w)->result.accept(field.getText().toString().trim())).setNegativeButton("Cancel",null).create();dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(surface());}
    private void connect(String encoded){
        if(busy)return;busy=true;beginProgress("Checking the scanned TV");long screen=pageId;worker.execute(()->{
            try{
                RemoteTvClient candidate=new RemoteTvClient(NeoPairing.parse(encoded,System.currentTimeMillis()/1000));ui.post(()->{if(!closed&&screen==pageId)progress.step("Verifying encrypted TV connection");});JSONObject info=candidate.get("/info");
                ui.post(()->{busy=false;if(closed||screen!=pageId)return;progress.finish("TV identity verified. Confirm the model below.");alert("Connection check completed — pair with this TV?",info.optString("model")+"\nAndroid "+info.optString("android")+"\nAddress: "+candidate.pairing.host+"\nCheck this is your intended TV. APK installation still requires approval on its screen.",()->{if(client!=null)client.disconnect();client=candidate;tv=info;home();beginProgress("Pairing with TV");progress.finish("Paired with "+tv.optString("model"));Toast.makeText(this,"Step completed: TV pairing",Toast.LENGTH_LONG).show();});});
            }catch(Exception failure){ui.post(()->{busy=false;if(!closed&&screen==pageId){progress.stop(failure.getMessage(),false);alert("Could not pair",failure.getMessage(),null);}});}
        });
    }
    private void phoneApps(){
        if(busy)return;page("Choose a phone app");paragraph("Only user-installed launchable apps are listed. Base and split APKs are transferred together. Phone-only interfaces or native CPU libraries may be incompatible with the TV; Android decides whether it can install them.");EditText search=new EditText(this);search.setHint("Search app name or package");search.setTextColor(text);search.setHintTextColor(muted);search.setSingleLine(true);search.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_FILTER|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);body.addView(search);LinearLayout list=new LinearLayout(this);list.setOrientation(1);body.addView(list);status.setText("Reading phone apps…");beginProgress("Scanning installed phone apps");busy=true;long screen=pageId;
        worker.execute(()->{
            try{List<ApplicationInfo> apps=new ArrayList<>();for(ApplicationInfo app:getPackageManager().getInstalledApplications(0))if((app.flags&ApplicationInfo.FLAG_SYSTEM)==0&&!app.packageName.equals(getPackageName())&&getPackageManager().getLaunchIntentForPackage(app.packageName)!=null)apps.add(app);
            apps.sort(Comparator.comparing(app->app.loadLabel(getPackageManager()).toString(),String.CASE_INSENSITIVE_ORDER));
            ui.post(()->{busy=false;if(closed||screen!=pageId)return;progress.finish(apps.size()+" general phone apps available");Toast.makeText(this,"Step completed: phone app scan",Toast.LENGTH_LONG).show();status.setText(apps.size()+" apps available");renderPhoneApps(apps,"",list);search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence value,int start,int count,int after){}public void onTextChanged(CharSequence value,int start,int before,int count){renderPhoneApps(apps,value.toString(),list);}public void afterTextChanged(android.text.Editable value){}});button("Back",this::home);});}catch(Exception failure){ui.post(()->{busy=false;if(!closed&&screen==pageId){progress.stop(failure.getMessage(),false);alert("App scan failed",failure.getMessage(),null);button("Back",this::home);}});}
        });
    }
    private void renderPhoneApps(List<ApplicationInfo> apps,String query,LinearLayout list){
        list.removeAllViews();String needle=query.toLowerCase(Locale.ROOT);
        for(ApplicationInfo app:apps){String name=app.loadLabel(getPackageManager()).toString();if(!(name+" "+app.packageName).toLowerCase(Locale.ROOT).contains(needle))continue;
            TextView item=text(name+"\n"+app.packageName,17,text);item.setPadding(dp(17),dp(15),dp(17),dp(15));item.setBackground(surface());item.setOnClickListener(v->choose(app));item.setFocusable(true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(7),0,dp(7));list.addView(item,lp);
        }
    }
    private void choose(ApplicationInfo app){
        if(client==null||busy)return;busy=true;beginProgress("Checking app compatibility");long screen=pageId;JSONObject selectedTv=tv;
        worker.execute(()->{try{
            List<File> files=new ArrayList<>();files.add(new File(app.sourceDir));if(app.splitSourceDirs!=null)for(String split:app.splitSourceDirs)files.add(new File(split));long size=0;for(File file:files)size+=file.length();
            if(app.minSdkVersion>selectedTv.optInt("sdk",0))throw new IOException("This build needs newer Android. Use the TV Play Store for a compatible version.");
            Set<String> nativeAbis=new HashSet<>(),tvAbis=new HashSet<>();org.json.JSONArray supported=selectedTv.optJSONArray("abis");if(supported!=null)for(int i=0;i<supported.length();i++)tvAbis.add(supported.getString(i));
            int checked=0;for(File file:files){try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(file)){java.util.Enumeration<? extends java.util.zip.ZipEntry> entries=zip.entries();while(entries.hasMoreElements()){String[] path=entries.nextElement().getName().split("/");if(path.length>=3&&path[0].equals("lib")&&path[path.length-1].endsWith(".so"))nativeAbis.add(path[1]);}}int done=++checked;ui.post(()->{if(!closed&&screen==pageId)progress.amount(done,files.size(),"APK files checked");});}
            if(!nativeAbis.isEmpty()&&Collections.disjoint(nativeAbis,tvAbis))throw new IOException("This phone build has "+nativeAbis+" native code; the TV supports "+tvAbis+". Use the TV Play Store for a compatible build.");
            String name=app.loadLabel(getPackageManager()).toString();String label=name.length()>100?name.substring(0,100):name;long total=size;
            ui.post(()->{busy=false;if(closed||screen!=pageId)return;progress.finish("Compatibility check completed");alert("App check completed — send to TV?",label+"\n"+app.packageName+"\n"+files.size()+" APK file(s), "+total/1048576+" MB\nDestination: "+selectedTv.optString("model")+"\nApp data, sign-ins and Play licensing are not copied. Approve installation on the TV.",()->transfer(label,app.packageName,files));});
        }catch(Exception failure){ui.post(()->{busy=false;if(!closed&&screen==pageId){progress.stop(failure.getMessage(),false);alert("App cannot be transferred",failure.getMessage(),null);}});}});
    }
    private void transfer(String label,String pkg,List<File> files){
        if(busy||client==null)return;page("Sending app to TV");paragraph(label+"\n"+pkg);beginProgress("Preparing app transfer");long screen=pageId;RemoteTvClient selected=client;busy=true;java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean();
        button("Cancel transfer",()->{if(cancelled.getAndSet(true))return;selected.disconnect();progress.stop("Transfer cancelled. Checking the TV request…",true);worker.execute(()->{try{JSONObject result=selected.post("/cancel",new JSONObject());ui.post(()->{if(!closed&&screen==pageId){busy=false;progress.stop("TV request cancelled. Nothing further will be installed by this request.",true);alert("Transfer cancelled",result.optString("message","Cancelled on TV"),null);}});}catch(Exception failure){ui.post(()->{if(!closed&&screen==pageId){busy=false;progress.stop("Cancellation not confirmed. Check the TV.",true);}});}});});
        worker.execute(()->{try{
            JSONObject result=selected.upload(label,pkg,files,new RemoteTvClient.Progress(){
                int previous=-1;
                public void stage(String name){previous=-1;ui.post(()->{if(!closed&&screen==pageId&&!cancelled.get())progress.step(name);});}
                private void update(long done,long total){int percent=total<=0?0:(int)(100d*done/total);if(percent==previous)return;previous=percent;ui.post(()->{if(!closed&&screen==pageId&&!cancelled.get())progress.amount(done,total,"bytes");});}
                public void preparing(long done,long total){update(done,total);}
                public void sent(long done,long total){update(done,total);}
            });
            ui.post(()->{if(cancelled.get())return;busy=false;if(!closed&&screen==pageId){progress.finish("All APK files received by the TV. Installation is not complete yet.");watching=result.optString("ticket");watch();renderStatus(result);}});
        }catch(Exception failure){ui.post(()->{if(cancelled.get())return;busy=false;if(!closed&&screen==pageId){String message="Transfer stopped: "+failure.getMessage()+"\nCheck the TV before retrying.";status.setText(message);progress.stop(message,false);alert("Transfer not completed",message,null);button("Back",this::home);}});}});
    }
    private void playStore(){
        page("Install from Google Play");paragraph("Use the TV's official Play Store to get the compatible TV build. Sign in and finish the store's Install steps on the TV; this app does not extract Play Store download files.");
        button("Enter app package or Play Store link",()->input("Package or Play Store link","",value->{
            String pkg=value;try{if(value.startsWith("https://play.google.com/store/apps/details"))pkg=android.net.Uri.parse(value).getQueryParameter("id");}catch(Exception ignored){}
            if(pkg==null||!pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")){alert("App identifier needed","Enter the app's package name, or paste its Google Play details link.",null);return;}
            final String wanted=pkg;alert("Open this app on the TV?",wanted+"\nDestination: "+tv.optString("model")+"\nApprove the request on the TV, then use Google Play to install it.",()->storeRequest(wanted));
        }));
        button("Open Google Play on this phone",()->{try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("market://apps")));}catch(RuntimeException missing){alert("Google Play unavailable","Use the TV store or transfer a compatible app already installed on this phone.",null);}});
        paragraph("You can also use Google's Install on more devices option where offered for your account/TV. Store purchase, availability and compatibility rules still apply.");button("Back",this::home);
    }
    private void storeRequest(String pkg){if(busy||client==null)return;busy=true;beginProgress("Sending Play Store request");worker.execute(()->{try{JSONObject result=client.post("/store",new JSONObject().put("package",pkg));ui.post(()->{busy=false;if(!closed){progress.finish("TV received the Play Store request");watching=result.optString("ticket");watch();renderStatus(result);}});}catch(Exception error){ui.post(()->{busy=false;if(!closed){progress.stop(error.getMessage(),false);alert("Store request failed",error.getMessage(),null);}});}});}
    private void watch(){
        if(client==null)return;ActionProgress previous=progress;page("TV installation status");progress.carryFrom(previous);progress.step("Checking TV request status");lastState="";
        paragraph("Complete any approval shown on the TV. This phone shows the TV's reported result; opening Google Play is not an installation result.");
        button("Cancel request",()->{if(busy)return;busy=true;progress.step("Cancelling TV request");long screen=pageId;worker.execute(()->{try{JSONObject result=client.post("/cancel",new JSONObject());ui.post(()->{busy=false;if(!closed&&screen==pageId)renderStatus(result);});}catch(Exception failure){ui.post(()->{busy=false;if(!closed&&screen==pageId){progress.stop("Cancellation not confirmed. Check the TV.",false);alert("Cancellation not confirmed",failure.getMessage(),null);}});}});});
        button("Back",this::home);if(watching.isEmpty())watching="current";ui.removeCallbacks(poll);ui.post(poll);
    }
    private void renderStatus(JSONObject result){
        String ticket=result.optString("ticket");if(!watching.equals("current")&&!watching.equals(ticket)){watching="";progress.stop("The TV has moved to another request. Check its screen.",false);return;}
        String state=result.optString("state"),message=result.optString("message");boolean changed=!state.equals(lastState);lastState=state;
        status.setText(result.optString("label")+"\n"+result.optString("package")+"\n\n"+message);
        if(state.equals("receiving")){progress.step("TV receiving APK files");progress.amount(result.optLong("received"),result.optLong("total"),"bytes");}
        else if(state.equals("review")){progress.step("Waiting for your approval on the TV");if(changed)alert("Request delivery completed","The TV received this request. Review it and choose Approve request on the TV. Installation is not complete yet.",null);}
        else if(state.equals("installing"))progress.step("Android installing app on TV");
        else if(state.equals("confirm")){progress.step("Waiting for Android confirmation on TV");if(changed)alert("TV review completed","Confirm Android's installation prompt on the TV to continue.",null);}
        else if(state.equals("installed")){progress.finish(message);if(changed)alert("Installation completed",message,null);}
        else if(state.equals("store-open")){progress.finish("Official TV Play Store opened");if(changed)alert("Store request completed","Finish installation in Google Play on the TV. Neo cannot confirm the store's installation result.",null);}
        else if(state.equals("cancelled")){progress.stop(message,true);if(changed)alert("Request cancelled",message,null);}
        else if(Arrays.asList("error","closed","expired").contains(state)){progress.stop(message,false);if(changed)alert("Action not completed",message,null);}
        else progress.step(message.isEmpty()?"Waiting for TV status":message);
        if(Arrays.asList("installed","cancelled","error","closed","expired","store-open").contains(state)){watching="";ui.removeCallbacks(poll);return;}
        ui.removeCallbacks(poll);ui.postDelayed(poll,1000);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==51&&result==RESULT_OK&&data!=null)connect(data.getStringExtra("pairing"));}
    @Override public void onBackPressed(){if(busy){if(client!=null)client.disconnect();return;}home();}
    @Override protected void onDestroy(){closed=true;if(activeDialog!=null)activeDialog.dismiss();ui.removeCallbacksAndMessages(null);if(client!=null)client.disconnect();worker.shutdownNow();super.onDestroy();}
}
