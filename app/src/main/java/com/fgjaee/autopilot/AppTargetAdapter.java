package com.fgjaee.autopilot;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** App hook integration; a workflow does NOT need its own APK. */
public interface AppTargetAdapter {
    String packageName();
    void onPackageLoaded(XC_LoadPackage.LoadPackageParam lp);
}
