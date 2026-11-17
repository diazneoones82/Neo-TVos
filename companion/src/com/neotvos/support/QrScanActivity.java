package com.neotvos.support;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.neotv.protocol.NeoQr;
import java.util.concurrent.*;

/** On-device camera scanning; no photos or pairing data leave the phone. */
public final class QrScanActivity extends Activity implements SurfaceHolder.Callback {
    private ActionProgress progress;private Camera camera;private SurfaceView preview;private TextView status;private volatile boolean decoding,finished;private long lastScan;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(20,30,20,20);root.setBackgroundColor(0xff071326);
        status=new TextView(this);status.setText("Scan the QR in Neo TVLauncher → Settings → Install apps remotely. Keep the whole white square in view.");status.setTextColor(0xfff4faff);status.setTextSize(18);root.addView(status);
        progress=new ActionProgress(this,0xe61a3049,0xfff4faff,0xff8feaff,0xffb6cee2);root.addView(progress);progress.begin("Starting QR camera");
        preview=new SurfaceView(this);root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));preview.getHolder().addCallback(this);
        Button cancel=new Button(this);cancel.setText("Cancel scan");cancel.setOnClickListener(v->cancelScan());root.addView(cancel);setContentView(root);
        if(checkSelfPermission(android.Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.CAMERA},41);
    }
    private void open(){
        if(camera!=null||finished||checkSelfPermission(android.Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED||!preview.getHolder().getSurface().isValid())return;
        try{
            int selected=-1;Camera.CameraInfo info=new Camera.CameraInfo();for(int i=0;i<Camera.getNumberOfCameras();i++){Camera.getCameraInfo(i,info);if(info.facing==Camera.CameraInfo.CAMERA_FACING_BACK){selected=i;break;}}
            if(selected<0)throw new IllegalStateException("No back camera available");camera=Camera.open(selected);Camera.Parameters parameters=camera.getParameters();
            Camera.Size size=null;for(Camera.Size candidate:parameters.getSupportedPreviewSizes())if(candidate.width<=1280&&candidate.height<=960&&(size==null||candidate.width*candidate.height>size.width*size.height))size=candidate;
            if(size==null)size=parameters.getPreviewSize();parameters.setPreviewSize(size.width,size.height);parameters.setPreviewFormat(android.graphics.ImageFormat.NV21);
            if(parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            camera.setParameters(parameters);Camera.getCameraInfo(selected,info);int rotation=getWindowManager().getDefaultDisplay().getRotation()*90;camera.setDisplayOrientation((info.orientation-rotation+360)%360);camera.setPreviewDisplay(preview.getHolder());
            final int width=size.width,height=size.height;
            camera.setPreviewCallback((bytes,source)->{
                long now=SystemClock.elapsedRealtime();if(decoding||finished||now-lastScan<400)return;lastScan=now;decoding=true;byte[] luminance=java.util.Arrays.copyOf(bytes,width*height);
                worker.execute(()->{String text=NeoQr.scan(luminance,width,height);decoding=false;if(text!=null&&text.startsWith("neotv://pair/"))runOnUiThread(()->{if(!finished){finished=true;progress.finish("TV QR read successfully");Toast.makeText(this,"Step completed: TV QR scan",Toast.LENGTH_LONG).show();setResult(RESULT_OK,new Intent().putExtra("pairing",text));finish();}});});
            });camera.startPreview();progress.step("Scanning TV QR");
        }catch(Exception error){close();progress.stop("Camera could not start",false);status.setText("Camera could not start. Grant Camera permission or use Paste pairing link. "+error.getClass().getSimpleName());}
    }
    private void close(){if(camera!=null){camera.setPreviewCallback(null);camera.stopPreview();camera.release();camera=null;}}
    public void surfaceCreated(SurfaceHolder holder){open();}public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){}public void surfaceDestroyed(SurfaceHolder holder){close();}
    @Override protected void onResume(){super.onResume();open();}
    @Override protected void onPause(){close();super.onPause();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==41){if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)open();else{progress.stop("Camera permission was not granted",true);status.setText("Camera permission is needed to scan. You can cancel and paste the TV pairing link instead.");}}}
    private void cancelScan(){progress.stop("QR scan cancelled",true);Toast.makeText(this,"QR scan cancelled",Toast.LENGTH_SHORT).show();finish();}
    @Override public void onBackPressed(){cancelScan();}
    @Override protected void onDestroy(){finished=true;close();worker.shutdownNow();super.onDestroy();}
}
