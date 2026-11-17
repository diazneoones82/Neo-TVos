package com.neotv.protocol;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;

/** Short-lived local-address endpoint plus TLS certificate pin and bearer secret. */
public final class NeoPairing {
    private static final String PREFIX="neotv://pair/", ALPHABET="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    public final String host;public final int port;public final byte[] token,certificate;public final long expires;
    public NeoPairing(String host,int port,byte[] token,byte[] certificate,long expires){
        if(!localAddress(host)||port<1024||port>65535||token.length!=16||certificate.length!=32)throw new IllegalArgumentException("Invalid local pairing");
        this.host=host;this.port=port;this.token=token.clone();this.certificate=certificate.clone();this.expires=expires;
    }
    public static boolean localAddress(String host){
        String[] parts=host.split("\\.",-1);if(parts.length!=4)return false;int[] bytes=new int[4];
        try{for(int i=0;i<4;i++){if(!parts[i].matches("0|[1-9][0-9]{0,2}"))return false;bytes[i]=Integer.parseInt(parts[i]);if(bytes[i]>255)return false;}}catch(NumberFormatException invalid){return false;}
        return bytes[0]==10||bytes[0]==192&&bytes[1]==168||bytes[0]==172&&bytes[1]>=16&&bytes[1]<=31||bytes[0]==169&&bytes[1]==254;
    }
    public String text(){
        ByteBuffer packet=ByteBuffer.allocate(59);packet.put((byte)1);for(String part:host.split("\\."))packet.put((byte)Integer.parseInt(part));
        packet.putShort((short)port).put(token).put(certificate).putInt((int)expires);return PREFIX+base64(packet.array());
    }
    public static NeoPairing parse(String text,long nowSeconds){
        if(!text.startsWith(PREFIX)||text.length()>110)throw new IllegalArgumentException("Scan the QR shown by Neo TVLauncher");
        byte[] bytes=unbase64(text.substring(PREFIX.length()));if(bytes.length!=59)throw new IllegalArgumentException("Invalid pairing size");
        ByteBuffer packet=ByteBuffer.wrap(bytes);if(packet.get()!=1)throw new IllegalArgumentException("Unsupported pairing version");
        String host=(packet.get()&255)+"."+(packet.get()&255)+"."+(packet.get()&255)+"."+(packet.get()&255);int port=packet.getShort()&65535;
        byte[] secret=new byte[16],pin=new byte[32];packet.get(secret).get(pin);long expires=packet.getInt()&0xffffffffL;
        if(expires<=nowSeconds||expires>nowSeconds+900)throw new IllegalArgumentException("QR expired; open a new TV pairing screen");
        return new NeoPairing(host,port,secret,pin,expires);
    }
    public String authorization(){return "Bearer "+base64(token);}
    public boolean authorizes(String value){return MessageDigest.isEqual(authorization().getBytes(java.nio.charset.StandardCharsets.US_ASCII),value.getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
    private static String base64(byte[] bytes){StringBuilder out=new StringBuilder();int buffer=0,bits=0;for(byte b:bytes){buffer=(buffer<<8)|(b&255);bits+=8;while(bits>=6){bits-=6;out.append(ALPHABET.charAt((buffer>>>bits)&63));}}if(bits>0)out.append(ALPHABET.charAt((buffer<<(6-bits))&63));return out.toString();}
    private static byte[] unbase64(String text){byte[] result=new byte[text.length()*6/8];int buffer=0,bits=0,index=0;for(char ch:text.toCharArray()){int value=ALPHABET.indexOf(ch);if(value<0)throw new IllegalArgumentException("Invalid pairing characters");buffer=(buffer<<6)|value;bits+=6;if(bits>=8){bits-=8;if(index>=result.length)throw new IllegalArgumentException("Pairing overflow");result[index++]=(byte)(buffer>>>bits);}}if(!base64(result).equals(text))throw new IllegalArgumentException("Noncanonical pairing");return result;}
}
