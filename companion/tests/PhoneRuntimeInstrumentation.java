package com.neotvos.support;

import android.app.Instrumentation;
import android.os.Bundle;
import com.neotv.protocol.*;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import android.content.Intent;
import android.widget.ProgressBar;

public final class PhoneRuntimeInstrumentation extends Instrumentation {
    private String encoded;
    @Override public void onCreate(Bundle options){super.onCreate(options);encoded=options.getString("pairing","");start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        checkActionFeedback();
        if(!encoded.isEmpty()){NeoPairing pairing=NeoPairing.parse(encoded,System.currentTimeMillis()/1000);
        RemoteTvClient correct=new RemoteTvClient(pairing);
        if(correct.get("/info").optString("model").isEmpty())throw new AssertionError("TV identity missing");
        byte[] wrongPin=pairing.certificate.clone();wrongPin[0]^=1;
        RemoteTvClient wrong=new RemoteTvClient(new NeoPairing(pairing.host,pairing.port,pairing.token,wrongPin,pairing.expires));
        boolean rejected=false;try{wrong.get("/info");}catch(javax.net.ssl.SSLException expected){rejected=true;}
        if(!rejected)throw new AssertionError("Incorrect TV certificate pin accepted");
        boolean[][] modules=NeoQr.encode(encoded);if(!NeoQr.decode(modules).equals(encoded))throw new AssertionError("QR round trip");
        int size=(NeoQr.SIZE+8)*6;byte[] frame=new byte[size*size];Arrays.fill(frame,(byte)230);
        for(int y=0;y<NeoQr.SIZE;y++)for(int x=0;x<NeoQr.SIZE;x++)if(modules[y][x])for(int j=0;j<6;j++)for(int i=0;i<6;i++)frame[((y+4)*6+j)*size+(x+4)*6+i]=12;
        if(!encoded.equals(NeoQr.scan(frame,size,size)))throw new AssertionError("Camera luminance decoder");
        }
        result.putString("stream","PASS: phone action progress, completion, failure, cancellation/stale callbacks"+(encoded.isEmpty()?"":"; TLS pinning and QR decoding")+"\n");finish(-1,result);
    }catch(Throwable error){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(error));finish(0,result);}}
    private MainActivity activity;
    private void checkActionFeedback()throws Exception{
        activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger after=new AtomicInteger();
        runOnMainSync(()->activity.job("Feedback probe",()->{activity.step("Reading test data");activity.amount(25,100,"items");started.countDown();if(!release.await(8,TimeUnit.SECONDS))throw new AssertionError("Test release timed out");activity.step("Verifying result");return "Probe completed";},after::incrementAndGet));
        if(!started.await(5,TimeUnit.SECONDS))throw new AssertionError("Worker did not start");waitForIdleSync();
        runOnMainSync(()->{ProgressBar bar=(ProgressBar)activity.progress.getChildAt(2);if(bar.isIndeterminate()||bar.getProgress()!=25)throw new AssertionError("Real partial progress missing");});
        Thread.sleep(1100);runOnMainSync(()->{String label=((android.widget.TextView)activity.progress.getChildAt(1)).getText().toString();if(!label.contains("25%")||!label.contains("elapsed"))throw new AssertionError("Elapsed clock lost measured progress");});release.countDown();waitUntilFinished();
        runOnMainSync(()->{ProgressBar bar=(ProgressBar)activity.progress.getChildAt(2);if(bar.isIndeterminate()||bar.getProgress()!=100||after.get()!=1)throw new AssertionError("Success did not complete progress");if(!activity.progress.summary().contains("\u2713 Reading test data")||!activity.activeDialog.isShowing())throw new AssertionError("Step history/completion prompt missing");activity.activeDialog.dismiss();});
        runOnMainSync(()->activity.job("Failure probe",()->{throw new java.io.IOException("Expected test failure");},after::incrementAndGet));waitUntilFinished();
        runOnMainSync(()->{ProgressBar bar=(ProgressBar)activity.progress.getChildAt(2);if(bar.isIndeterminate()||bar.getProgress()==100||after.get()!=1)throw new AssertionError("Failure presented as completed");activity.activeDialog.dismiss();});
        CountDownLatch cancelling=new CountDownLatch(1),cancelRelease=new CountDownLatch(1),oldFinished=new CountDownLatch(1);
        runOnMainSync(()->activity.job("Cancellation probe",()->{cancelling.countDown();cancelRelease.await(8,TimeUnit.SECONDS);activity.step("Late stale update");oldFinished.countDown();return "Must not complete";},after::incrementAndGet));
        if(!cancelling.await(5,TimeUnit.SECONDS))throw new AssertionError("Cancellation worker did not start");
        runOnMainSync(()->activity.disconnect());cancelRelease.countDown();if(!oldFinished.await(5,TimeUnit.SECONDS))throw new AssertionError("Cancelled worker stuck");waitForIdleSync();
        runOnMainSync(()->{if(after.get()!=1||((ProgressBar)activity.progress.getChildAt(2)).getProgress()!=0||!activity.activeDialog.isShowing())throw new AssertionError("Cancelled worker updated replacement page or cancellation prompt missing");activity.activeDialog.dismiss();ActionProgress p=activity.progress;p.begin("Waiting for Android approval");ProgressBar bar=(ProgressBar)p.getChildAt(2);if(!bar.isIndeterminate())throw new AssertionError("Unknown-duration wait must animate");p.stop("Cancelled",true);if(bar.isIndeterminate()||bar.getProgress()==100)throw new AssertionError("Cancelled wait reported success");activity.finish();});
    }
    private void waitUntilFinished()throws Exception{
        long end=System.currentTimeMillis()+6000;boolean[] busy={true};do{waitForIdleSync();runOnMainSync(()->busy[0]=activity.busy);if(!busy[0])return;Thread.sleep(20);}while(System.currentTimeMillis()<end);throw new AssertionError("Action did not finish");
    }

}
