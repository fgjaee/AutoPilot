package com.fgjaee.autopilot;

import android.util.Log;
import org.json.JSONObject;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * PASSIVE Meijer-only hook. Never changes a request or a response.
 * Logs HTTP method + path, body JSON KEY NAMES, and HTTP status only.
 * No URLs with queries, no headers, no credential or data values.
 */
public final class MeijerNetworkHook implements IXposedHookLoadPackage {
    private static final String TARGET = "com.meijer.mobile.meijer";
    private static final String TAG = "AutoPilotMeijer";
    private static final AtomicInteger EVENTS = new AtomicInteger(0);
    private static final int MAX_EVENTS = 100;

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!TARGET.equals(lp.packageName)) return;
        Log.i(TAG, "observer attached to Meijer process=" + lp.processName);
        final ClassLoader cl = lp.classLoader;
        // React Native JavaScript networking -> Android bridge.
        hookBridge(cl);
        // Independent OkHttp fallback; not all requests pass through RN bridge.
        hookOkHttp(cl);
    }

    private static void hookBridge(ClassLoader cl) {
        try {
            Class<?> type = XposedHelpers.findClassIfExists(
                "com.facebook.react.modules.network.NetworkingModule", cl);
            if (type == null) { Log.i(TAG, "rn_networking_missing"); return; }
            XposedBridge.hookAllMethods(type, "sendRequest", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (p.args.length < 5) return;
                        String method = String.valueOf(p.args[0]);
                        String url = String.valueOf(p.args[1]);
                        Object data = p.args[4];
                        if (!isClip(url, data)) return;
                        emit("bridge", method, url, payloadKeys(data), "");
                    } catch (Throwable ignored) { /* tracing must never break Meijer */ }
                }
            });
            Log.i(TAG, "rn_sendRequest_hook_ready");
        } catch (Throwable e) { Log.i(TAG, "rn_hook_unavailable:" + e.getClass().getSimpleName()); }
    }

    private static void hookOkHttp(ClassLoader cl) {
        try {
            Class<?> builder = XposedHelpers.findClassIfExists("okhttp3.Request$Builder", cl);
            if (builder == null) { Log.i(TAG, "okhttp_builder_missing"); return; }
            XposedBridge.hookAllMethods(builder, "build", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        Object request = p.getResult();
                        if (request == null) return;
                        String url = String.valueOf(XposedHelpers.callMethod(request, "url"));
                        if (!isClip(url, null)) return;
                        String method = String.valueOf(XposedHelpers.callMethod(request, "method"));
                        // Deliberately do not read RequestBody or HTTP headers.
                        emit("okhttp_request", method, url, "", "");
                    } catch (Throwable ignored) { }
                }
            });
            Class<?> responseBuilder = XposedHelpers.findClassIfExists("okhttp3.Response$Builder", cl);
            if (responseBuilder != null) {
                XposedBridge.hookAllMethods(responseBuilder, "build", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        try {
                            Object resp = p.getResult();
                            if (resp == null) return;
                            Object request = XposedHelpers.callMethod(resp, "request");
                            if (request == null) return;
                            String url = String.valueOf(XposedHelpers.callMethod(request, "url"));
                            if (!isClip(url, null)) return;
                            String method = String.valueOf(XposedHelpers.callMethod(request, "method"));
                            int code = ((Number) XposedHelpers.callMethod(resp, "code")).intValue();
                            emit("okhttp_response", method, url, "", "status=" + code);
                        } catch (Throwable ignored) { }
                    }
                });
            }
            Log.i(TAG, "okhttp_hooks_ready");
        } catch (Throwable e) { Log.i(TAG, "okhttp_hook_unavailable:" + e.getClass().getSimpleName()); }
    }

    static boolean isClip(String url, Object data) {
        String path = pathOnly(url).toLowerCase(Locale.ROOT);
        if (path.endsWith("/offers/clip")) return true;
        // GraphQL fallback only when operation name unambiguously mentions clipOffer.
        if (path.endsWith("/graphql") && data != null) {
            String body = payloadString(data);
            return body != null && body.contains("clipOffer") && !body.contains("unclipOffer");
        }
        return false;
    }

    static String pathOnly(String url) {
        if (url == null) return "";
        try {
            URI uri = URI.create(url);
            String p = uri.getPath();
            if (p != null && !p.isEmpty()) return p;
        } catch (Exception ignored) { }
        String cleaned = url.split("[?#]", 2)[0];
        return cleaned.matches("[A-Za-z0-9_./-]{1,150}") ? cleaned : "";
    }

    private static String payloadString(Object data) {
        try {
            Object map = XposedHelpers.callMethod(data, "toHashMap");
            if (!(map instanceof Map)) return null;
            Object value = ((Map<?, ?>)map).get("string");
            if (value instanceof String) return (String)value;
        } catch (Throwable ignored) { }
        return null;
    }

    static String payloadKeys(Object data) {
        try {
            Object result = XposedHelpers.callMethod(data, "toHashMap");
            if (!(result instanceof Map)) return "unknown";
            Map<?, ?> map = (Map<?, ?>) result;
            List<String> keys = new ArrayList<>();
            for (Object key : map.keySet()) addSafeKey(keys, String.valueOf(key));
            Object json = map.get("string");
            if (json instanceof String && ((String) json).length() < 32768) {
                try {
                    JSONObject obj = new JSONObject((String) json);
                    for (java.util.Iterator<String> it = obj.keys(); it.hasNext();) {
                        addSafeKey(keys, it.next());
                    }
                } catch (Exception ignored) { }
            }
            Collections.sort(keys);
            return String.join(",", keys);
        } catch (Throwable ignored) { return "unavailable"; }
    }

    private static void addSafeKey(List<String> keys, String key) {
        if (!key.matches("[A-Za-z][A-Za-z0-9_]{0,39}")) return;
        String k = key.toLowerCase(Locale.ROOT);
        for (String secret : new String[]{"password","auth","token","cookie","secret","email","session","credential"}) {
            if (k.contains(secret)) return;
        }
        if (!keys.contains(key) && keys.size() < 32) keys.add(key);
    }

    private static String safeEndpointPath(String url) {
        String p = pathOnly(url).toLowerCase(Locale.ROOT);
        if (p.endsWith("/offers/clip")) return "/offers/Clip";
        if (p.endsWith("/graphql")) return "/graphql";
        return "/unknown";
    }

    private static void emit(String origin, String method, String url, String keys, String extra) {
        if (EVENTS.incrementAndGet() > MAX_EVENTS) return;
        String m = method.matches("[A-Za-z]{3,8}") ? method : "UNKNOWN";
        Log.i(TAG, "event=" + origin + " method=" + m + " path=" + safeEndpointPath(url)
            + (keys.isEmpty() ? "" : " json_keys=" + keys)
            + (extra.isEmpty() ? "" : " " + extra));
    }
}
