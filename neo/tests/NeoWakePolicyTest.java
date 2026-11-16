package com.tvos.neo;

import java.util.Arrays;
import java.util.HashSet;

public final class NeoWakePolicyTest {
    private static void check(boolean condition,String name){if(!condition)throw new AssertionError(name);}
    public static void main(String[] ignored){
        NeoWakePolicy policy=new NeoWakePolicy("com.tvos.neo",new HashSet<>(Arrays.asList("vendor.home")),false,false);
        policy.window("com.tvos.neo","com.tvos.neo.NeoHomeActivity");policy.sleep();check(policy.pending(),"Home resumes on wake");
        policy.returnedHome();check(!policy.pending(),"Successful return consumes wake request");
        policy.window("com.tvos.neo","android.service.dreams.DreamActivity");policy.sleep();check(policy.pending(),"Dream over Home preserves Home origin");
        policy.window("com.android.systemui","Keyguard");check(policy.pending(),"Keyguard does not erase pending resume");
        policy.window("org.example.player","VideoActivity");check(!policy.pending(),"Opening another app cancels queued Home");policy.sleep();check(!policy.pending(),"Video resumes normally after sleep");
        policy.window("com.tvos.neo","android.service.dreams.DreamActivity");policy.sleep();check(!policy.pending(),"Dream over player preserves player origin");
        policy.window("vendor.home","HomeActivity");policy.sleep();check(policy.pending(),"OEM Home is eligible");
        NeoWakePolicy rebound=new NeoWakePolicy("com.tvos.neo",new HashSet<>(),true,true);check(rebound.pending(),"Process rebind retains persisted request");
        rebound.window("com.android.tv.settings","SettingsActivity");check(!rebound.pending(),"Explicit Settings navigation cancels pending Home");
        System.out.println("PASS: sleep/wake Home policy, dreams, keyguard, other-app preservation and rebind");
    }
}
