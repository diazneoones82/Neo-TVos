package com.neotv.protocol;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/** Bounded base/split APK manifest, followed by each APK's exact byte stream. */
public final class NeoInstallWire {
    public static final long MAX_TOTAL=1024L*1024*1024;
    public static final class Part {
        public final String name;public final long size;public final byte[] digest;
        public Part(String name,long size,byte[] digest){if(!name.matches("[A-Za-z0-9_.-]{1,80}\\.apk")||name.contains("..")||size<=0||size>MAX_TOTAL||digest.length!=32)throw new IllegalArgumentException("Invalid APK manifest");this.name=name;this.size=size;this.digest=digest.clone();}
    }
    public final String label,pkg;public final List<Part> parts;
    public NeoInstallWire(String label,String pkg,List<Part> parts){
        if(label.isEmpty()||label.length()>100||!pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")||pkg.length()>180||parts.isEmpty()||parts.size()>32)throw new IllegalArgumentException("Invalid app identity");
        long total=0;java.util.Set<String> names=new java.util.HashSet<>();for(Part part:parts){total+=part.size;if(!names.add(part.name))throw new IllegalArgumentException("Duplicate APK name");}if(total>MAX_TOTAL)throw new IllegalArgumentException("App exceeds 1 GB limit");
        this.label=label;this.pkg=pkg;this.parts=new ArrayList<>(parts);
    }
    public byte[] header()throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(0x4e454f31);out.writeUTF(label);out.writeUTF(pkg);out.writeShort(parts.size());for(Part part:parts){out.writeUTF(part.name);out.writeLong(part.size);out.write(part.digest);}out.flush();return bytes.toByteArray();}
    public long length()throws IOException{long size=header().length;for(Part part:parts)size+=part.size;return size;}
    public static NeoInstallWire read(DataInputStream in)throws IOException{
        if(in.readInt()!=0x4e454f31)throw new IOException("Unrecognized upload format");String label=in.readUTF(),pkg=in.readUTF();int count=in.readUnsignedShort();if(count<1||count>32)throw new IOException("APK count exceeds limit");
        List<Part> parts=new ArrayList<>();try{for(int i=0;i<count;i++){String name=in.readUTF();long size=in.readLong();byte[] digest=new byte[32];in.readFully(digest);parts.add(new Part(name,size,digest));}return new NeoInstallWire(label,pkg,parts);}catch(IllegalArgumentException invalid){throw new IOException(invalid.getMessage());}
    }
}
