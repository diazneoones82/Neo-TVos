package com.neotv.protocol;
import java.io.*;
/** One bounded raw TrueType/OpenType font. Never a ZIP or executable package. */
public final class NeoFontWire {
    public static final long MAX_SIZE=10L*1024*1024;
    public final String name;public final long size;public final byte[] digest;
    public NeoFontWire(String name,long size,byte[] digest){
        if(name==null||name.length()>100||name.contains("..")||name.contains("/")||name.contains("\\")||name.matches(".*[\\p{Cntrl}].*")||!name.toLowerCase(java.util.Locale.ROOT).matches(".+\\.(ttf|otf)")||size<12||size>MAX_SIZE||digest.length!=32)throw new IllegalArgumentException("Choose a .ttf or .otf font, up to 10 MB");
        this.name=name;this.size=size;this.digest=digest.clone();
    }
    public byte[] header()throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);d.writeInt(0x4e454f46);d.writeUTF(name);d.writeLong(size);d.write(digest);d.flush();return b.toByteArray();}
    public long length()throws IOException{return header().length+size;}
    public static NeoFontWire read(DataInputStream in)throws IOException{if(in.readInt()!=0x4e454f46)throw new IOException("Invalid font transfer");String name=in.readUTF();long size=in.readLong();byte[] digest=new byte[32];in.readFully(digest);try{return new NeoFontWire(name,size,digest);}catch(IllegalArgumentException e){throw new IOException(e.getMessage());}}
    public static String validate(File file)throws IOException{
        if(file.length()<12||file.length()>MAX_SIZE)throw new IOException("Invalid font size");
        try(RandomAccessFile f=new RandomAccessFile(file,"r")){
            int signature=f.readInt();if(signature!=0x00010000&&signature!=0x4f54544f)throw new IOException("Only raw TrueType (.ttf) and OpenType (.otf) fonts are supported");
            int count=f.readUnsignedShort();if(count<1||count>256||12L+16L*count>f.length())throw new IOException("Invalid font table directory");f.seek(12);boolean cmap=false,outline=false;java.util.Set<Integer> tags=new java.util.HashSet<>();
            for(int i=0;i<count;i++){int tag=f.readInt();f.readInt();long offset=(f.readInt()&0xffffffffL),length=(f.readInt()&0xffffffffL);if(!tags.add(tag)||offset<12L+16L*count||offset>f.length()||length>f.length()-offset)throw new IOException("Invalid font table bounds");if(tag==0x636d6170)cmap=true;if(tag==0x676c7966||tag==0x43464620||tag==0x43464632)outline=true;}
            if(!tags.contains(0x68656164)||!tags.contains(0x68686561)||!tags.contains(0x686d7478)||!tags.contains(0x6d617870)||!tags.contains(0x6e616d65))throw new IOException("Font is missing required metadata or metrics");
            if(!cmap||!outline)throw new IOException("Font is missing character or outline tables");return signature==0x4f54544f?"otf":"ttf";
        }
    }
}
