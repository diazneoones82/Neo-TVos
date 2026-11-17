package com.neotv.protocol;

import javax.security.auth.x500.X500Principal;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.*;

/** App-private TLS identity using JCA primitives and a minimal self-signed X.509 envelope. */
public final class NeoTlsIdentity {
    public final PrivateKey key;public final X509Certificate certificate;
    private NeoTlsIdentity(PrivateKey key,X509Certificate certificate){this.key=key;this.certificate=certificate;}
    public static NeoTlsIdentity load(File directory)throws Exception{
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create TLS identity directory");
        File privateFile=new File(directory,"receiver.pk8"),certificateFile=new File(directory,"receiver.der");
        if(privateFile.isFile()&&certificateFile.isFile()){
            PrivateKey key=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(read(privateFile)));
            X509Certificate certificate=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(read(certificateFile)));
            byte[] challenge=new byte[32];new SecureRandom().nextBytes(challenge);Signature test=Signature.getInstance("SHA256withRSA");test.initSign(key);test.update(challenge);byte[] signed=test.sign();test.initVerify(certificate);test.update(challenge);if(!test.verify(signed))throw new GeneralSecurityException("TLS identity mismatch");
            return new NeoTlsIdentity(key,certificate);
        }
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);KeyPair pair=generator.generateKeyPair();
        byte[] algorithm=sequence(tag(6,new byte[]{0x2a,(byte)0x86,0x48,(byte)0x86,(byte)0xf7,0x0d,1,1,11}),tag(5,new byte[0]));
        byte[] name=new X500Principal("CN=Neo TVLauncher local receiver").getEncoded();byte[] serial=new byte[16];new SecureRandom().nextBytes(serial);
        SimpleDateFormat date=new SimpleDateFormat("yyyyMMddHHmmss'Z'",Locale.US);date.setTimeZone(TimeZone.getTimeZone("UTC"));long now=System.currentTimeMillis();
        byte[] validity=sequence(tag(24,date.format(new Date(now-86400000L)).getBytes(StandardCharsets.US_ASCII)),tag(24,date.format(new Date(now+5L*365*86400000L)).getBytes(StandardCharsets.US_ASCII)));
        byte[] tbs=sequence(tag(0xa0,tag(2,new byte[]{2})),tag(2,new BigInteger(1,serial).toByteArray()),algorithm,name,validity,name,pair.getPublic().getEncoded());
        Signature signature=Signature.getInstance("SHA256withRSA");signature.initSign(pair.getPrivate());signature.update(tbs);byte[] signed=signature.sign();byte[] bitString=new byte[signed.length+1];System.arraycopy(signed,0,bitString,1,signed.length);
        byte[] encoded=sequence(tbs,algorithm,tag(3,bitString));X509Certificate certificate=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(encoded));certificate.verify(pair.getPublic());
        write(privateFile,pair.getPrivate().getEncoded());privateFile.setReadable(false,false);privateFile.setWritable(false,false);privateFile.setReadable(true,true);privateFile.setWritable(true,true);write(certificateFile,encoded);
        return new NeoTlsIdentity(pair.getPrivate(),certificate);
    }
    private static byte[] read(File file)throws IOException{if(file.length()>16384)throw new IOException("Invalid identity size");try(InputStream input=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[2048];for(int n;(n=input.read(buffer))!=-1;)out.write(buffer,0,n);return out.toByteArray();}}
    private static void write(File file,byte[] bytes)throws IOException{try(FileOutputStream output=new FileOutputStream(file)){output.write(bytes);output.getFD().sync();}}
    private static byte[] sequence(byte[]... parts)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();for(byte[] part:parts)bytes.write(part);return tag(0x30,bytes.toByteArray());}
    private static byte[] tag(int type,byte[] bytes)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(type);if(bytes.length<128)out.write(bytes.length);else{int count=0;for(int n=bytes.length;n>0;n>>>=8)count++;out.write(0x80|count);for(int i=count-1;i>=0;i--)out.write((bytes.length>>>(i*8))&255);}out.write(bytes);return out.toByteArray();}
}
