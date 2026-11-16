package com.tvos.neo;

import android.content.SharedPreferences;
import java.util.*;

/** Membership, enabled-category union, persistence and new-app assignment contracts. */
public final class NeoCategoriesTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        Memory prefs=new Memory();NeoCategories categories=new NeoCategories(prefs);
        check(categories.ids().equals(Arrays.asList("favorites","video","audio","general","tv_apps")),"Default categories");
        categories.seedNewApps(Arrays.asList("a","b"));check(categories.visible("general","a"),"New app goes to General");
        categories.toggle("video","a");categories.toggle("general","a");check(categories.visible("video","a"),"Manual membership");
        categories.enabled("video",false);check(!categories.visible("all","a"),"Disabled only category excludes app from All");
        categories.toggle("favorites","a");check(categories.visible("all","a"),"Enabled secondary membership retains app");
        categories.toggle("favorites","a");check(!categories.visible("all","a"),"Removing secondary membership updates All");
        categories.seedNewApps(Arrays.asList("a","b","c"));check(!categories.members("general").contains("a"),"Known removed membership must not be re-seeded");
        check(categories.members("general").contains("c"),"Newly installed app seeded");
        categories.rename("video","Cinema");categories.enabled("video",true);
        NeoCategories reopened=new NeoCategories(prefs);check(reopened.name("video").equals("Cinema")&&reopened.visible("video","a"),"State survives store reconstruction");
        reopened.add("Local");check(reopened.ids().size()==6,"Custom category added");String custom=reopened.ids().get(5);reopened.remove(custom);check(reopened.ids().size()==5,"Custom category removed");
        reopened.remove("general");check(reopened.ids().contains("general"),"Default category retained");
        Set<String> copy=reopened.members("video");copy.clear();check(reopened.members("video").contains("a"),"Editor copies cannot mutate stored membership");
        System.out.println("Category behavior checks passed");
    }
    static final class Memory implements SharedPreferences {
        final Map<String,Object> data=new HashMap<>();
        public Map<String,?> getAll(){return new HashMap<>(data);}
        public String getString(String key,String fallback){return (String)data.getOrDefault(key,fallback);}
        @SuppressWarnings("unchecked") public Set<String> getStringSet(String key,Set<String> fallback){return (Set<String>)data.getOrDefault(key,fallback);}
        public int getInt(String key,int fallback){return (Integer)data.getOrDefault(key,fallback);}
        public long getLong(String key,long fallback){return (Long)data.getOrDefault(key,fallback);}
        public float getFloat(String key,float fallback){return (Float)data.getOrDefault(key,fallback);}
        public boolean getBoolean(String key,boolean fallback){return (Boolean)data.getOrDefault(key,fallback);}
        public boolean contains(String key){return data.containsKey(key);}
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener){}
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener){}
        public Editor edit(){return new Editor(){
            final Map<String,Object> writes=new HashMap<>();boolean clear;
            public Editor putString(String k,String v){writes.put(k,v);return this;}
            public Editor putStringSet(String k,Set<String> v){writes.put(k,new HashSet<>(v));return this;}
            public Editor putInt(String k,int v){writes.put(k,v);return this;}
            public Editor putLong(String k,long v){writes.put(k,v);return this;}
            public Editor putFloat(String k,float v){writes.put(k,v);return this;}
            public Editor putBoolean(String k,boolean v){writes.put(k,v);return this;}
            public Editor remove(String k){writes.put(k,null);return this;}
            public Editor clear(){clear=true;return this;}
            public boolean commit(){apply();return true;}
            public void apply(){if(clear)data.clear();for(Map.Entry<String,Object> e:writes.entrySet())if(e.getValue()==null)data.remove(e.getKey());else data.put(e.getKey(),e.getValue());}
        };}
    }
}
