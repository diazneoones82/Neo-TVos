package com.tvos.neo;
import android.content.*;
/** Explicit PackageInstaller callback, delivered into the install worker process. */
public final class NeoInstallResult extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){NeoInstallService.result(intent);}
}
