package com.neotvos.support;
import android.content.*;import android.content.pm.*;import java.util.*;import org.json.*;
/** Read-only shell-side label resolver. Never changes packages or system settings. */
public final class InventoryMain{
 public static void main(String[] args)throws Exception{
  if(android.os.Looper.myLooper()==null)android.os.Looper.prepareMainLooper();Class<?> activityThread=Class.forName("android.app.ActivityThread");Object thread=activityThread.getDeclaredMethod("systemMain").invoke(null);Context context=(Context)activityThread.getDeclaredMethod("getSystemContext").invoke(thread);PackageManager pm=context.getPackageManager();
  Set<String> homes=new HashSet<>();for(ResolveInfo r:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0))homes.add(r.activityInfo.packageName);
  JSONArray rows=new JSONArray();for(ApplicationInfo a:pm.getInstalledApplications(0)){
   boolean system=(a.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0;
   if(!CleanupPolicy.canRemove(a.packageName,!system,homes))continue;
   boolean launchable=pm.getLeanbackLaunchIntentForPackage(a.packageName)!=null||pm.getLaunchIntentForPackage(a.packageName)!=null;
   if(!launchable)continue;CharSequence label=pm.getApplicationLabel(a);JSONObject row=new JSONObject();row.put("package",a.packageName);row.put("name",label==null?a.packageName:label.toString());row.put("system",system);row.put("launchable",true);rows.put(row);
  }
  System.out.println("NEO_INVENTORY_BEGIN");System.out.println(rows.toString());System.out.println("NEO_INVENTORY_END");System.exit(0);
 }
}
