package com.fgjaee.autopilot;

import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** One LSPosed module, one entry point, app-specific integrations as adapters. */
public final class AutoPilotBridge implements IXposedHookLoadPackage {
    private static final String TAG = "AutoPilotBridge";
    private final AppTargetAdapter[] adapters = new AppTargetAdapter[] {
        new MeijerNetworkHook()
    };

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        for (AppTargetAdapter adapter : adapters) {
            if (!adapter.packageName().equals(lp.packageName)) continue;
            try {
                adapter.onPackageLoaded(lp);
                Log.i(TAG, "attached app adapter=" + adapter.packageName());
            } catch (Throwable e) {
                Log.e(TAG, "adapter failed for " + adapter.packageName()
                    + " (" + e.getClass().getSimpleName() + ")");
            }
        }
    }
}
