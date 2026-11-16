package com.tvos.neo;

import java.util.HashSet;
import java.util.Set;

/** Remember the pre-sleep surface; never replace an unrelated app on wake. */
final class NeoWakePolicy {
    private final String ownPackage;
    private final Set<String> homes;
    private boolean homeSurface;
    private boolean pending;

    NeoWakePolicy(String ownPackage, Set<String> homes, boolean homeSurface, boolean pending) {
        this.ownPackage = ownPackage; this.homes = new HashSet<>(homes);
        this.homeSurface = homeSurface; this.pending = pending;
    }
    void window(String pkg, String activity) {
        if (pkg == null || pkg.isEmpty()) return;
        if ("com.android.systemui".equals(pkg) || "android".equals(pkg)) return;
        // A system dream overlays the previous app. Keep its resume destination.
        if (activity != null && activity.endsWith(".DreamActivity")) return;
        if (ownPackage.equals(pkg) && activity != null && activity.endsWith(".NeoHomeActivity")) {
            returnedHome(); return;
        }
        homeSurface = ownPackage.equals(pkg) || homes.contains(pkg);
        if (!homeSurface) pending = false;
    }
    void sleep() { pending = homeSurface; }
    boolean isHomeSurface() { return homeSurface; }
    boolean pending() { return pending; }
    void returnedHome() { homeSurface = true; pending = false; }
}
