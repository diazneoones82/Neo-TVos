package com.tvos.neo;

import android.content.Context;
import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import org.xmlpull.v1.XmlPullParser;
import android.util.Xml;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Standard appfilter.xml icon packs, read as resources without executing their code. */
final class NeoIconPacks {
    static final class Pack { String pkg,label; Pack(String p,String l){pkg=p;label=l;} }
    private final Context context;
    private String loaded="";
    private Resources resources;
    private final Map<String,String> mappings=new HashMap<>();
    NeoIconPacks(Context context){this.context=context.getApplicationContext();}

    private XmlPullParser parser(String pkg) throws Exception {
        Resources r=context.getPackageManager().getResourcesForApplication(pkg);
        int id=r.getIdentifier("appfilter","xml",pkg);
        if(id!=0)return r.getXml(id);
        // Assets parsers are consumed immediately by read(); the stream is owned there.
        return null;
    }
    List<Pack> installed() {
        List<Pack> out=new ArrayList<>();
        for(ApplicationInfo app:context.getPackageManager().getInstalledApplications(0)) {
            if(app.packageName.equals(context.getPackageName()))continue;
            try {
                Resources r=context.getPackageManager().getResourcesForApplication(app);
                boolean found=r.getIdentifier("appfilter","xml",app.packageName)!=0;
                if(!found)try(InputStream in=r.getAssets().open("appfilter.xml")){found=true;}catch(Exception ignored){}
                if(found)out.add(new Pack(app.packageName,app.loadLabel(context.getPackageManager()).toString()));
            }catch(Exception ignored){}
        }
        out.sort((a,b)->a.label.compareToIgnoreCase(b.label)); return out;
    }
    private void read(XmlPullParser parser) throws Exception {
        int count=0;
        for(int type=parser.getEventType();type!=XmlPullParser.END_DOCUMENT;type=parser.next()) {
            if(type!=XmlPullParser.START_TAG || !"item".equals(parser.getName()))continue;
            if(++count>100000)throw new IllegalArgumentException("Icon mapping limit exceeded");
            String component=parser.getAttributeValue(null,"component"),drawable=parser.getAttributeValue(null,"drawable");
            if(component==null || drawable==null)continue;
            if(component.startsWith("ComponentInfo{") && component.endsWith("}"))component=component.substring(14,component.length()-1);
            ComponentName name=ComponentName.unflattenFromString(component);
            if(name!=null)mappings.put(name.flattenToString(),drawable);
        }
    }
    synchronized void load(String pkg) {
        if(pkg.equals(loaded))return;loaded=pkg;resources=null;mappings.clear();
        if(pkg.isEmpty())return;
        try {
            resources=context.getPackageManager().getResourcesForApplication(pkg);
            XmlPullParser p=parser(pkg);
            if(p!=null) {try {read(p);} finally {if(p instanceof android.content.res.XmlResourceParser)((android.content.res.XmlResourceParser)p).close();}}
            else try(InputStream stream=resources.getAssets().open("appfilter.xml")) {p=Xml.newPullParser();p.setInput(stream,"UTF-8");read(p);}
        } catch(Exception e){resources=null;mappings.clear();}
    }
    synchronized Drawable icon(ComponentName component,Drawable fallback) {
        if(resources==null)return fallback;
        String drawable=mappings.get(component.flattenToString());if(drawable==null)return fallback;
        int id=resources.getIdentifier(drawable,"drawable",loaded);
        if(id==0)id=resources.getIdentifier(drawable,"mipmap",loaded);
        if(id==0)return fallback;
        try{return resources.getDrawable(id,null);}catch(Exception e){return fallback;}
    }
}
