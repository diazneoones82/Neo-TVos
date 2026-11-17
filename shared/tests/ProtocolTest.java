package com.neotv.protocol;

import java.util.*;import java.io.*;
public final class ProtocolTest {
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);}
    public static void main(String[] args)throws Exception{
        byte[] token=new byte[16],pin=new byte[32];new Random(3).nextBytes(token);new Random(7).nextBytes(pin);
        long now=1794873900;NeoPairing pair=new NeoPairing("192.168.1.12",45678,token,pin,now+600);
        String text=pair.text();check(text.length()<=106,"QR byte capacity");NeoPairing restored=NeoPairing.parse(text,now);
        check(restored.host.equals(pair.host)&&restored.port==pair.port&&Arrays.equals(restored.certificate,pin),"Pairing round trip");
        check(restored.authorizes(pair.authorization())&&!restored.authorizes("Bearer bad"),"Bearer verification");
        try{NeoPairing.parse(text,now+601);throw new AssertionError("Expired pair accepted");}catch(IllegalArgumentException expected){}
        check(!NeoPairing.localAddress("8.8.8.8")&&!NeoPairing.localAddress("127.0.0.1")&&!NeoPairing.localAddress("192.168.1.1.evil"),"QR is local address only");
        for(int trial=0;trial<12;trial++){
            String payload=trial==0?text:"neotv://pair/"+"a".repeat(10+trial*7);
            boolean[][] matrix=NeoQr.encode(payload);check(payload.equals(NeoQr.decode(matrix)),"QR encode/decode");
            matrix[35][35]^=true;matrix[34][35]^=true;matrix[33][35]^=true;check(payload.equals(NeoQr.decode(matrix)),"QR RS repairs data damage");
        }
        boolean[][] qr=NeoQr.encode(text);int size=(NeoQr.SIZE+8)*8;byte[] image=new byte[size*size];Arrays.fill(image,(byte)255);
        for(int y=0;y<NeoQr.SIZE;y++)for(int x=0;x<NeoQr.SIZE;x++)if(qr[y][x])for(int j=0;j<8;j++)for(int i=0;i<8;i++)image[((y+4)*8+j)*size+(x+4)*8+i]=0;
        check(text.equals(NeoQr.scan(image,size,size)),"Camera raster QR decode");
        byte[] rotated=new byte[image.length];for(int y=0;y<size;y++)for(int x=0;x<size;x++)rotated[x*size+size-1-y]=image[y*size+x];
        check(text.equals(NeoQr.scan(rotated,size,size)),"Rotated camera QR decode");
        if(args.length>0){try(FileOutputStream file=new FileOutputStream(args[0])){file.write(("P5\n"+size+" "+size+"\n255\n").getBytes());file.write(image);}System.out.println(text);}
        List<NeoInstallWire.Part> parts=Arrays.asList(new NeoInstallWire.Part("base.apk",20,pin),new NeoInstallWire.Part("split-1.apk",12,pin));
        NeoInstallWire manifest=new NeoInstallWire("Example app","org.example.app",parts);
        NeoInstallWire copy=NeoInstallWire.read(new DataInputStream(new ByteArrayInputStream(manifest.header())));
        check(copy.parts.size()==2&&copy.length()==manifest.header().length+32,"Split manifest and exact length");
        try{new NeoInstallWire.Part("../bad.apk",1,pin);throw new AssertionError("Traversal accepted");}catch(IllegalArgumentException expected){}
        try{new NeoInstallWire("Bad","org.example.app",Arrays.asList(parts.get(0),parts.get(0)));throw new AssertionError("Duplicate APK accepted");}catch(IllegalArgumentException expected){}
        File tlsDirectory=new File(System.getProperty("java.io.tmpdir"),"neo-test-tls-"+UUID.randomUUID());
        try{
            NeoTlsIdentity identity=NeoTlsIdentity.load(tlsDirectory);identity.certificate.verify(identity.certificate.getPublicKey());identity.certificate.checkValidity();
            NeoTlsIdentity again=NeoTlsIdentity.load(tlsDirectory);check(Arrays.equals(identity.certificate.getEncoded(),again.certificate.getEncoded()),"TLS identity persists and private key matches");
        }finally{File[] files=tlsDirectory.listFiles();if(files!=null)for(File f:files)f.delete();tlsDirectory.delete();}
        System.out.println("PASS: independent QR encoding/camera scan/RS correction, pairing expiry/local pin identity, bounded base/split manifest");
    }
}
