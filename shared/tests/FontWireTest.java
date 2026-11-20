package com.neotv.protocol;
import java.io.*;
import java.util.*;
public final class FontWireTest {
    private static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        byte[] hash=new byte[32];NeoFontWire original=new NeoFontWire("My font.ttf",256,hash);NeoFontWire copy=NeoFontWire.read(new DataInputStream(new ByteArrayInputStream(original.header())));check(copy.name.equals(original.name)&&copy.size==256&&copy.length()==original.length(),"Font manifest round trip");
        for(String name:new String[]{"../font.ttf","a/b.ttf","a\\b.ttf","app.apk","font.zip","font\n.ttf"})try{new NeoFontWire(name,256,hash);throw new AssertionError("Unsafe font name accepted");}catch(IllegalArgumentException expected){}
        try{new NeoFontWire("large.otf",NeoFontWire.MAX_SIZE+1,hash);throw new AssertionError("Oversize accepted");}catch(IllegalArgumentException expected){}
        File fixture=File.createTempFile("neo-font-test-",".ttf");try{
            try(DataOutputStream out=new DataOutputStream(new FileOutputStream(fixture))){out.writeInt(0x00010000);out.writeShort(7);out.write(new byte[6]);int offset=12+16*7;for(int tag:new int[]{0x636d6170,0x676c7966,0x68656164,0x68686561,0x686d7478,0x6d617870,0x6e616d65}){out.writeInt(tag);out.writeInt(0);out.writeInt(offset);out.writeInt(4);offset+=4;}out.write(new byte[28]);}
            check(NeoFontWire.validate(fixture).equals("ttf"),"Bounded SFNT directory accepted");
            try(RandomAccessFile file=new RandomAccessFile(fixture,"rw")){file.seek(20);file.writeInt(Integer.MAX_VALUE);}
            try{NeoFontWire.validate(fixture);throw new AssertionError("Out of bounds font accepted");}catch(IOException expected){}
            try(RandomAccessFile file=new RandomAccessFile(fixture,"rw")){file.seek(0);file.writeInt(0x504b0304);}
            try{NeoFontWire.validate(fixture);throw new AssertionError("ZIP accepted as font");}catch(IOException expected){}
        }finally{fixture.delete();}
        for(String path:args)NeoFontWire.validate(new File(path));
        System.out.println("PASS: font wire round trip, names/type/size bounds and SFNT table bounds");
    }
}
