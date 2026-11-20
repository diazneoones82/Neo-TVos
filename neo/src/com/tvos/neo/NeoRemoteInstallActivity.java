package com.tvos.neo;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import com.neotv.protocol.NeoQr;
import org.json.JSONObject;

/** TV-local pairing/review screen; leaving it closes the receiver. */
public final class NeoRemoteInstallActivity extends Activity {
    private Messenger service;
    private boolean bound,fontsOnly;private TextView preview,apply;private String selectedFont="";
    private TextView detail,identity;
    private ImageView qr;
    private TextView approve,cancel;
    private String priorQr="";
    private NeoTheme theme;
    private float scale;
    private final Messenger events=new Messenger(new Handler(Looper.getMainLooper(),message->{render(message.getData().getString("status","{}"));return true;}));
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){service=new Messenger(binder);send("start");}
        @Override public void onServiceDisconnected(ComponentName name){service=null;detail.setText("Connection closed. Reopen this screen to pair again.");}
    };
    @Override public void onCreate(Bundle state){
        super.onCreate(state);fontsOnly=getIntent().getBooleanExtra("fonts_only",false);theme=NeoTheme.load(this);scale=getResources().getDisplayMetrics().widthPixels/960f;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(px(34),px(22),px(34),px(18));root.setBackgroundColor(0xff071326);
        root.addView(text(fontsOnly?"Install fonts from phone":"Install apps and fonts remotely",25));root.addView(text("Open Neo TVLauncher Companion → Install apps or fonts remotely → Scan TV QR",13));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);root.addView(row,new LinearLayout.LayoutParams(-1,0,1));
        qr=new ImageView(this);qr.setBackgroundColor(Color.WHITE);qr.setPadding(px(9),px(9),px(9),px(9));row.addView(qr,new LinearLayout.LayoutParams(px(270),px(270)));
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(px(24),0,0,0);row.addView(panel,new LinearLayout.LayoutParams(0,-2,1));
        identity=text(android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL,20);panel.addView(identity);
        detail=text("Opening local pairing…",15);detail.setPadding(0,px(12),0,px(10));panel.addView(detail);
        preview=text("",19);preview.setMaxLines(3);preview.setVisibility(View.GONE);panel.addView(preview);
        panel.addView(text(fontsOnly?"Send a TTF / OTF font (up to 10 MB) from your phone. Preview and approve here, then choose where to apply it. Android system fonts stay unchanged.":"Same local network required. Transfers are encrypted and pinned to this TV. Apps need Android confirmation; fonts need approval here.",12));
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER);root.addView(actions);
        approve=button("Approve request",()->send("approve"));actions.addView(approve);
        cancel=button("Cancel request",()->send("cancel"));actions.addView(cancel);
        if(!fontsOnly)actions.addView(button("Allow APK installs",()->{if(Build.VERSION.SDK_INT>=26)startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,android.net.Uri.parse("package:"+getPackageName())));else startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));}));
        apply=button("Apply font",this::applyFont);apply.setVisibility(View.GONE);actions.addView(apply);
        actions.addView(button("Close pairing",this::finish));setContentView(root);approve.setEnabled(false);approve.requestFocus();
        startReceiver();
    }
    private int px(float value){return Math.round(value*scale);}
    private TextView text(String value,int size){TextView view=new TextView(this);view.setText(value);view.setTypeface(theme.uiTypeface());view.setTextColor(theme.text);view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(size));return view;}
    private TextView button(String label,Runnable action){TextView view=text(label,13);view.setGravity(Gravity.CENTER);view.setPadding(px(12),px(11),px(12),px(11));view.setBackground(theme.surface(scale,false));view.setFocusable(true);view.setOnFocusChangeListener((v,focused)->view.setBackground(theme.surface(scale,focused)));view.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.setMargins(px(5),0,px(5),0);view.setLayoutParams(lp);return view;}
    private void send(String operation){if(service==null)return;try{Message request=Message.obtain(null,1);Bundle data=new Bundle();data.putString("op",operation);data.putBoolean("fonts_only",fontsOnly);request.setData(data);request.replyTo=events;service.send(request);}catch(RemoteException disconnected){detail.setText("Receiver connection was lost. Close and reopen pairing.");}}
    private void render(String raw){
        try{JSONObject status=new JSONObject(raw);String phase=status.optString("state"),encoded=status.optString("qr");
            String message=status.optString("message");String pkg=status.optString("package"),label=status.optString("label");
            if(!label.isEmpty())message=label+"\n"+pkg+"\n\n"+message;
            if(phase.equals("receiving"))message+="\n"+status.optLong("received")/1048576+" / "+status.optLong("total")/1048576+" MB";
            String kind=status.optString("kind"),id=status.optString("font_id");
            if(kind.equals("font")&&(phase.equals("review")||phase.equals("font-installed"))){try{Typeface face=phase.equals("font-installed")?NeoFonts.load(this,id):Typeface.createFromFile(new java.io.File(getCacheDir(),"remote-"+status.getString("ticket")+"/font.bin"));if(face!=null){preview.setTypeface(face);preview.setText("Neo TVLauncher · Aa Bb 0123456789");preview.setVisibility(View.VISIBLE);}}catch(Exception ignored){preview.setVisibility(View.GONE);}}else preview.setVisibility(View.GONE);
            selectedFont=phase.equals("font-installed")?id:"";apply.setVisibility(selectedFont.isEmpty()?View.GONE:View.VISIBLE);
            detail.setText(message);approve.setEnabled(phase.equals("review"));cancel.setEnabled(phase.equals("receiving")||phase.equals("review")||phase.equals("installing")||phase.equals("confirm"));
            if(!encoded.isEmpty()&&!priorQr.equals(encoded)){
                priorQr=encoded;boolean[][] modules=NeoQr.encode(encoded);int unit=8,size=(NeoQr.SIZE+8)*unit;Bitmap image=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);canvas.drawColor(Color.WHITE);Paint ink=new Paint();ink.setColor(Color.BLACK);
                for(int y=0;y<NeoQr.SIZE;y++)for(int x=0;x<NeoQr.SIZE;x++)if(modules[y][x])canvas.drawRect((x+4)*unit,(y+4)*unit,(x+5)*unit,(y+5)*unit,ink);qr.setImageBitmap(image);
            }
            if(phase.equals("expired")||phase.equals("closed")){qr.setImageDrawable(null);approve.setEnabled(false);}
            if(phase.equals("review"))approve.requestFocus();else if(phase.equals("font-installed"))apply.requestFocus();
        }catch(Exception invalid){detail.setText("Receiver returned an invalid status");}
    }
    private void applyFont(){
        if(selectedFont.isEmpty())return;final String id=selectedFont;LinearLayout content=new LinearLayout(this);content.setOrientation(1);content.setPadding(px(18),px(15),px(18),px(15));android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setView(content).create();
        content.addView(text("Apply imported font to",20));String[] labels={"Launcher text","Clocks","Launcher and clocks","Cancel"};for(int i=0;i<labels.length;i++){final int index=i;content.addView(button(labels[i],()->{if(index<3){try{NeoFonts.select(this,id,index!=1,index!=0);Toast.makeText(this,"Font applied. Return to Home to see it.",Toast.LENGTH_LONG).show();}catch(Exception failed){Toast.makeText(this,"Font could not be applied",Toast.LENGTH_LONG).show();}}dialog.dismiss();}));}dialog.setOnShowListener(d->{if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(theme.surface(scale,false));});dialog.show();content.getChildAt(1).requestFocus();
    }
    private void startReceiver(){
        if(bound)return;
        if(!fontsOnly&&Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){
            detail.setText("First choose Allow APK installs below and enable Neo TVLauncher. Reopen this screen afterwards if the TV settings return you to Home. The QR appears once this one-time setup is complete.");return;
        }
        bound=bindService(new Intent(this,NeoInstallService.class),connection,Context.BIND_AUTO_CREATE);
        if(!bound)detail.setText("Receiver could not start. Check the TV network and reopen this screen.");
    }
    @Override protected void onResume(){super.onResume();if(detail!=null)startReceiver();send("status");}
    @Override protected void onDestroy(){send("close");if(bound)unbindService(connection);bound=false;super.onDestroy();}
}
