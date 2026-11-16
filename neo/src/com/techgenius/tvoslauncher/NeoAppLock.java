package com.tvos.neo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import java.security.SecureRandom;
import java.security.MessageDigest;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Package-level PIN gates and monotonic, reboot-invalidated access windows. */
final class NeoAppLock {
 final Context context; final SharedPreferences p;
 NeoAppLock(Context c){context=c;p=c.getSharedPreferences("neo_locks",0);}
 boolean configured(){return p.contains("hash");}
 boolean eligible(String pkg){return pkg!=null&&!pkg.equals(context.getPackageName())&&!pkg.equals("android")&&!pkg.contains("settings")&&!pkg.contains("systemui")&&!pkg.contains("permissioncontroller")&&!pkg.contains("packageinstaller");}
 boolean enabled(String pkg){return eligible(pkg)&&configured()&&p.getBoolean("lock:"+pkg,false);}
 int minutes(String pkg){return p.getInt("minutes:"+pkg,15);}
 void configure(String pkg,boolean enabled,int minutes){if(!eligible(pkg))return;if(minutes<15||minutes>240||minutes%15!=0)throw new IllegalArgumentException();p.edit().putBoolean("lock:"+pkg,enabled).putInt("minutes:"+pkg,minutes).remove("until:"+pkg).apply();}
 int boot(){return Settings.Global.getInt(context.getContentResolver(),"boot_count",-1);}
 boolean blocked(String pkg){return enabled(pkg)&&(p.getInt("boot:"+pkg,-2)!=boot()||p.getLong("until:"+pkg,0)<=SystemClock.elapsedRealtime());}
 void grant(String pkg){p.edit().putInt("boot:"+pkg,boot()).putLong("until:"+pkg,SystemClock.elapsedRealtime()+minutes(pkg)*60000L).apply();}
 void revoke(){SharedPreferences.Editor e=p.edit();for(String key:p.getAll().keySet())if(key.startsWith("until:"))e.remove(key);e.apply();}
 static String hash(String pin,String salt){try{PBEKeySpec spec=new PBEKeySpec(pin.toCharArray(),android.util.Base64.decode(salt,0),60000,256);byte[] b=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();spec.clearPassword();return android.util.Base64.encodeToString(b,2);}catch(Exception e){throw new IllegalStateException(e);}}
 void setPin(String pin){if(!pin.matches("[0-9]{4}"))throw new IllegalArgumentException();byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);String s=android.util.Base64.encodeToString(salt,2);p.edit().putString("salt",s).putString("hash",hash(pin,s)).putInt("failures",0).remove("retry").apply();revoke();}
 boolean verify(String pin){if(System.currentTimeMillis()<p.getLong("retry",0)||!configured())return false;boolean ok=pin.matches("[0-9]{4}")&&MessageDigest.isEqual(hash(pin,p.getString("salt","")).getBytes(java.nio.charset.StandardCharsets.UTF_8),p.getString("hash","").getBytes(java.nio.charset.StandardCharsets.UTF_8));int failures=ok?0:p.getInt("failures",0)+1;p.edit().putInt("failures",failures>=5?0:failures).putLong("retry",failures>=5?System.currentTimeMillis()+30000:0).apply();return ok;}
}
