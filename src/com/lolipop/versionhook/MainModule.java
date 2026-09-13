package com.lolipop.versionhook;

import android.content.SharedPreferences;
import android.util.Log;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

import java.lang.reflect.Method;

/**
 * 作用域内进程的版本键统一回显期望值。
 * 覆盖 get(String[,String]) / getInt / getLong / getBoolean，
 * 并按 key 语义返回：incremental→完整串、name→OS主.次、code→数字。
 */
public class MainModule extends XposedModule {

    private static final String TAG = "VersionHook";
    public static final String CONF_GROUP = "config";
    public static final String KEY_FAKE = "fake_version";

    public MainModule() {
        super();
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        try {
            String fake = resolveFake();
            patchBuildFields(fake);
            installHooks(param.getPackageName(), fake);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "onPackageLoaded", t);
        }
    }

    /**
     * Build.VERSION.INCREMENTAL / Build.INCREMENTAL 是 zygote 启动时从系统属性
     * 读取固化的 static final 值 —— 属性污染会固化进所有进程。属性 hook 拦不住
     * 字段读取，只能用反射（清 final 位）把字段改回真值。
     */
    private void patchBuildFields(String fake) {
        setStaticField("android.os.Build$VERSION", "INCREMENTAL", fake);
        setStaticField("android.os.Build", "INCREMENTAL", fake);
    }

    private void setStaticField(String clazz, String field, String value) {
        try {
            Class<?> c = Class.forName(clazz);
            java.lang.reflect.Field f = c.getDeclaredField(field);
            f.setAccessible(true);
            try {
                java.lang.reflect.Field mods = java.lang.reflect.Field.class
                        .getDeclaredField("accessFlags");
                mods.setAccessible(true);
                mods.setInt(f, f.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
            } catch (Throwable ignored) {
            }
            f.set(null, value);
            log(Log.INFO, TAG, clazz + "." + field + " -> " + value);
        } catch (Throwable t) {
            log(Log.WARN, TAG, "patch " + clazz + "." + field + " failed", t);
        }
    }

    /** 读取链：RemotePrefs > 本地 prefs XML 直读 > APK 烘焙默认。 */
    private String resolveFake() {
        String v = null;
        try {
            v = getRemotePreferences(CONF_GROUP).getString(KEY_FAKE, null);
        } catch (Throwable ignored) {
        }
        if (v == null || v.isEmpty()) {
            v = readLocalPrefsXml();
        }
        if (v == null || v.isEmpty()) {
            v = bakedDefault();
        }
        return v;
    }

    private static String bakedDefault() {
        try {
            java.io.InputStream is = MainModule.class.getClassLoader()
                    .getResourceAsStream("config.properties");
            if (is != null) {
                java.util.Properties p = new java.util.Properties();
                p.load(is);
                is.close();
                String v = p.getProperty("fake_version", "").trim();
                if (!v.isEmpty()) return v;
            }
        } catch (Throwable ignored) {
        }
        return "OS4.0.11.0.XPNCNXM";
    }

    private static String readLocalPrefsXml() {
        try {
            java.io.File f = new java.io.File(
                    "/data/data/com.lolipop.versionhook/shared_prefs/config.xml");
            if (!f.canRead()) return null;
            byte[] data = new byte[(int) Math.min(f.length(), 65536)];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int n = in.read(data);
            in.close();
            if (n <= 0) return null;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "name=\"fake_version\"[^>]*>([^<]*)</string>").matcher(new String(data));
            if (m.find()) {
                String v = m.group(1).trim();
                return v.isEmpty() ? null : v;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void installHooks(String pkg, String fake) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            VersionReplacer replacer = new VersionReplacer(fake);
            hook(sp.getDeclaredMethod("get", String.class)).intercept(replacer);
            hook(sp.getDeclaredMethod("get", String.class, String.class)).intercept(replacer);
            hook(sp.getDeclaredMethod("getInt", String.class, int.class)).intercept(replacer);
            hook(sp.getDeclaredMethod("getLong", String.class, long.class)).intercept(replacer);
            hook(sp.getDeclaredMethod("getBoolean", String.class, boolean.class)).intercept(replacer);
            log(Log.INFO, TAG, "scope " + pkg + " -> " + fake);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook failed " + pkg, t);
        }
    }

    /** 拦截器：版本键按 key 语义/方法返回类型回显；其余键放行。 */
    public static class VersionReplacer implements XposedInterface.Hooker {
        private final String fakeInc;
        private final String fakeName;
        private final long fakeCode;

        public VersionReplacer(String fake) {
            this.fakeInc = fake;
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("^OS(\\d+)\\.(\\d+)").matcher(fake);
            if (m.find()) {
                this.fakeName = "OS" + m.group(1) + "." + m.group(2);
                this.fakeCode = Long.parseLong(m.group(1));
            } else {
                // 自定义串解析不出 OS 格式时，回退真实语义值（OS4.0 / 4）
                this.fakeName = "OS4.0";
                this.fakeCode = 4;
            }
        }

        @Override
        public Object intercept(Chain chain) throws Throwable {
            Object arg0 = chain.getArgs().isEmpty() ? null : chain.getArg(0);
            if (!(arg0 instanceof String) || !isVersionKey((String) arg0)) {
                return chain.proceed();
            }
            String key = (String) arg0;
            Method m = (Method) chain.getExecutable();
            Class<?> ret = m.getReturnType();
            if (ret == int.class) {
                return key.equals("ro.mi.os.version.code")
                        ? (int) fakeCode : (int) chain.proceed();
            }
            if (ret == long.class) {
                return key.equals("ro.mi.os.version.code")
                        ? fakeCode : chain.proceed();
            }
            if (ret == boolean.class) {
                return chain.proceed();
            }
            if (key.equals("ro.mi.os.version.name")) {
                return fakeName;
            }
            if (key.equals("ro.mi.os.version.code")) {
                // 数字键的 String 读取必须回纯数字 —— 消费方(如输入法门禁)会 toIntOrNull
                return String.valueOf(fakeCode);
            }
            return fakeInc;
        }

        private static boolean isVersionKey(String key) {
            return key.startsWith("ro.build.version")
                    || key.startsWith("ro.mi.os.version")
                    || key.startsWith("ro.system.build.version");
        }
    }
}
