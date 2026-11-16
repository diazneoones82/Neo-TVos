package com.tvos.neo;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Persistent multi-category memberships; explicit Home visibility is independent from membership. */
final class NeoCategories {
    static final List<String> IDS=Arrays.asList("favorites","video","audio","general","tv_apps");
    static final List<String> NAMES=Arrays.asList("Favorites","Video","Audio","General","TV Apps");
    private final SharedPreferences prefs;
    NeoCategories(SharedPreferences prefs){this.prefs=prefs;}
    List<String> ids(){List<String> out=new ArrayList<>(IDS);out.addAll(prefs.getStringSet("category_custom",Collections.emptySet()));return out;}
    String name(String id){int index=IDS.indexOf(id);return prefs.getString("category_name:"+id,index<0?id:NAMES.get(index));}
    boolean enabled(String id){return prefs.getBoolean("category_enabled:"+id,true);}
    void enabled(String id,boolean value){prefs.edit().putBoolean("category_enabled:"+id,value).apply();}
    Set<String> members(String id){return new HashSet<>(prefs.getStringSet("category_members:"+id,Collections.emptySet()));}
    void toggle(String id,String key){Set<String> members=members(id);if(!members.remove(key))members.add(key);prefs.edit().putStringSet("category_members:"+id,members).apply();}
    void seedNewApps(List<String> keys){
        Set<String> known=new HashSet<>(prefs.getStringSet("category_known",Collections.emptySet()));
        Set<String> general=members("general");boolean changed=false;
        for(String key:keys)if(known.add(key)){general.add(key);changed=true;}
        if(changed)prefs.edit().putStringSet("category_known",known).putStringSet("category_members:general",general).apply();
    }
    boolean visible(String active,String key){
        if(!"all".equals(active))return enabled(active)&&prefs.getStringSet("category_members:"+active,Collections.emptySet()).contains(key);
        for(String id:ids())if(enabled(id)&&prefs.getStringSet("category_members:"+id,Collections.emptySet()).contains(key))return true;
        return false;
    }
    void rename(String id,String name){if(!name.trim().isEmpty())prefs.edit().putString("category_name:"+id,name.trim()).apply();}
    void add(String name){String id="custom_"+System.currentTimeMillis();Set<String> ids=new HashSet<>(prefs.getStringSet("category_custom",Collections.emptySet()));ids.add(id);prefs.edit().putStringSet("category_custom",ids).putString("category_name:"+id,name.trim()).apply();}
    void remove(String id){if(IDS.contains(id))return;Set<String> ids=new HashSet<>(prefs.getStringSet("category_custom",Collections.emptySet()));ids.remove(id);prefs.edit().putStringSet("category_custom",ids).remove("category_members:"+id).remove("category_name:"+id).remove("category_enabled:"+id).apply();}
}
