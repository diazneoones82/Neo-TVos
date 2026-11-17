package com.neotvos.support;

import android.app.Instrumentation;
import android.os.Bundle;
import com.neotv.protocol.*;
import java.util.Arrays;

public final class PhoneRuntimeInstrumentation extends Instrumentation {
    private String encoded;
    @Override public void onCreate(Bundle options){super.onCreate(options);encoded=options.getString("pairing","");start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        NeoPairing pairing=NeoPairing.parse(encoded,System.currentTimeMillis()/1000);
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
        result.putString("stream","PASS: phone runtime TLS pairing, wrong-certificate rejection, QR and camera luminance decoding\n");finish(-1,result);
    }catch(Throwable error){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(error));finish(0,result);}}
}
