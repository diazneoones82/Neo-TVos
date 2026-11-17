package com.neotvos.support;

import com.neotv.protocol.*;
import org.json.JSONObject;
import javax.net.ssl.*;
import java.io.*;
import java.net.URL;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;

/** Only the scanned TV certificate is trusted; session credentials remain in memory. */
final class RemoteTvClient {
    final NeoPairing pairing;
    private final SSLSocketFactory tls;
    private volatile HttpsURLConnection active;
    interface Progress{void sent(long current,long total);}
    RemoteTvClient(NeoPairing pairing)throws Exception{
        this.pairing=pairing;SSLContext context=SSLContext.getInstance("TLS");
        context.init(null,new TrustManager[]{new X509TrustManager(){
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
            public void checkClientTrusted(X509Certificate[] chain,String auth)throws java.security.cert.CertificateException{throw new java.security.cert.CertificateException("No client certificate accepted");}
            public void checkServerTrusted(X509Certificate[] chain,String auth)throws java.security.cert.CertificateException{
                try{if(chain.length<1||!MessageDigest.isEqual(pairing.certificate,MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded())))throw new java.security.cert.CertificateException("TV certificate does not match scanned QR");}
                catch(GeneralSecurityException invalid){throw new java.security.cert.CertificateException(invalid);}
            }
        }},new SecureRandom());tls=context.getSocketFactory();
    }
    private HttpsURLConnection open(String method,String path,long length)throws Exception{
        if(System.currentTimeMillis()/1000>=pairing.expires)throw new IOException("Pairing expired. Scan a fresh TV QR.");
        HttpsURLConnection connection=(HttpsURLConnection)new URL("https://"+pairing.host+":"+pairing.port+path).openConnection();
        connection.setSSLSocketFactory(tls);connection.setHostnameVerifier((host,session)->host.equals(pairing.host));connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(7000);connection.setReadTimeout(length<0?7000:120000);connection.setRequestMethod(method);connection.setRequestProperty("Authorization",pairing.authorization());connection.setRequestProperty("Connection","close");
        if(length>=0){connection.setDoOutput(true);connection.setFixedLengthStreamingMode(length);connection.setRequestProperty("Content-Type","application/octet-stream");}active=connection;return connection;
    }
    private JSONObject response(HttpsURLConnection connection)throws Exception{
        int code=connection.getResponseCode();InputStream input=code>=400?connection.getErrorStream():connection.getInputStream();if(input==null)throw new IOException("TV rejected the request ("+code+")");
        try(InputStream source=input;ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];for(int n;(n=source.read(buffer))!=-1;){if(bytes.size()+n>65536)throw new IOException("TV response too large");bytes.write(buffer,0,n);}
            JSONObject result=new JSONObject(bytes.toString("UTF-8"));if(code>=400)throw new IOException(result.optString("error","TV rejected request "+code));return result;
        }
    }
    JSONObject get(String path)throws Exception{HttpsURLConnection connection=open("GET",path,-1);try{return response(connection);}finally{connection.disconnect();active=null;}}
    JSONObject post(String path,JSONObject json)throws Exception{byte[] bytes=json.toString().getBytes("UTF-8");HttpsURLConnection connection=open("POST",path,bytes.length);try{try(OutputStream out=connection.getOutputStream()){out.write(bytes);}return response(connection);}finally{connection.disconnect();active=null;}}
    JSONObject upload(String label,String pkg,List<File> files,Progress progress)throws Exception{
        List<NeoInstallWire.Part> parts=new ArrayList<>();byte[] buffer=new byte[65536];
        for(int i=0;i<files.size();i++){
            File file=files.get(i);MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream input=new FileInputStream(file)){for(int n;(n=input.read(buffer))!=-1;)digest.update(buffer,0,n);}
            parts.add(new NeoInstallWire.Part(i==0?"base.apk":"split-"+i+".apk",file.length(),digest.digest()));
        }
        NeoInstallWire manifest=new NeoInstallWire(label,pkg,parts);byte[] header=manifest.header();long total=manifest.length();HttpsURLConnection connection=open("POST","/install",total);
        try{
            try(OutputStream output=new BufferedOutputStream(connection.getOutputStream())){
                output.write(header);long sent=header.length;
                for(File file:files)try(InputStream input=new FileInputStream(file)){for(int n;(n=input.read(buffer))!=-1;){output.write(buffer,0,n);sent+=n;if(progress!=null)progress.sent(sent,total);}}
            }
            return response(connection);
        }finally{connection.disconnect();active=null;}
    }
    void disconnect(){HttpsURLConnection connection=active;if(connection!=null)connection.disconnect();active=null;}
}
