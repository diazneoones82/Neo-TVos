package com.tvos.neo;
import android.content.Context;
import android.graphics.Typeface;
import java.io.*;
import java.util.*;
/** App-private, content-addressed fonts; Android/system fonts are never replaced. */
final class NeoFonts {
    static final class Entry{final String id,name;Entry(String id,String name){this.id=id;this.name=name;}}
    static File directory(Context c){return new File(c.getFilesDir(),"fonts");}
    static File file(Context c,String id){if(!id.matches("[0-9a-f]{64}\\.(ttf|otf)"))throw new IllegalArgumentException("Invalid font identity");return new File(directory(c),id);}
    static List<Entry> list(Context c){List<Entry> out=new ArrayList<>();File[] files=directory(c).listFiles();if(files!=null)for(File f:files)if(f.getName().matches("[0-9a-f]{64}\\.(ttf|otf)")){String name="Imported font "+f.getName().substring(0,8);File label=new File(directory(c),f.getName()+".name");try(DataInputStream in=new DataInputStream(new FileInputStream(label))){name=in.readUTF();if(name.length()>100)continue;}catch(Exception ignored){}out.add(new Entry(f.getName(),name));}out.sort(Comparator.comparing(e->e.name,String.CASE_INSENSITIVE_ORDER));return out;}
    static Typeface load(Context c,String id){try{return Typeface.createFromFile(file(c,id));}catch(Exception invalid){return null;}}
    static Typeface selected(Context c,boolean clock){String id=c.getSharedPreferences("neo",0).getString(clock?"custom_clock_font":"custom_text_font","");return id.isEmpty()?null:load(c,id);}
    static String key(Context c){android.content.SharedPreferences p=c.getSharedPreferences("neo",0);return p.getString("custom_text_font","")+":"+p.getString("custom_clock_font","");}
    static void select(Context c,String id,boolean text,boolean clock){if(!id.isEmpty()&&load(c,id)==null)throw new IllegalArgumentException("Font is unavailable");android.content.SharedPreferences.Editor edit=c.getSharedPreferences("neo",0).edit();if(text)edit.putString("custom_text_font",id);if(clock)edit.putString("custom_clock_font",id);edit.apply();}
    static void remove(Context c,String id){android.content.SharedPreferences p=c.getSharedPreferences("neo",0);select(c,"",id.equals(p.getString("custom_text_font","")),id.equals(p.getString("custom_clock_font","")));file(c,id).delete();new File(directory(c),id+".name").delete();}
}
