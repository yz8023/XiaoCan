package io.github.jessire.xiaocanpurify;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class MainHook extends XposedModule {
    public static final String TARGET_PACKAGE = "com.realtech.xiaocan";
    public static final String LOG_TAG = "XiaoCanPurify";
    private static final AtomicBoolean HOOKED = new AtomicBoolean(false);
    private static volatile MainHook instance;

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        instance = this;
        ModuleLog.bind(this);
        log("XiaoCanPurify API 102 module loaded.");
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }
        instance = this;
        log("Target package ready: " + param.getPackageName());

        ClassLoader initialLoader = param.getClassLoader();
        if (canLoadTargetClasses(initialLoader)) {
            installAll(this, initialLoader);
            return;
        }

        // App uses ShellApplication / Aliyun Jiagu unpacker, hook Application & Activity lifecycle
        hookLifecycle(this);
    }

    private static boolean canLoadTargetClasses(ClassLoader cl) {
        if (cl == null) return false;
        try {
            Class.forName("com.realtech.xiaocan.MainActivity", false, cl);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void hookLifecycle(XposedInterface xposed) {
        try {
            Method attachBaseContext = Application.class.getDeclaredMethod("attachBaseContext", Context.class);
            xposed.hook(attachBaseContext).intercept(chain -> {
                Object result = chain.proceed();
                Context context = (Context) chain.getArg(0);
                if (context != null) {
                    ModuleLog.attachContext(context);
                    ClassLoader cl = context.getClassLoader();
                    if (canLoadTargetClasses(cl)) {
                        installAll(xposed, cl);
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            log("Hook Application.attachBaseContext failed: " + t);
        }

        try {
            Method onCreate = Application.class.getDeclaredMethod("onCreate");
            xposed.hook(onCreate).intercept(chain -> {
                Object result = chain.proceed();
                Application app = (Application) chain.getThisObject();
                if (app != null) {
                    ModuleLog.attachContext(app);
                    ClassLoader cl = app.getClassLoader();
                    if (canLoadTargetClasses(cl)) {
                        installAll(xposed, cl);
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            log("Hook Application.onCreate failed: " + t);
        }

        try {
            Method actOnCreate = Activity.class.getDeclaredMethod("onCreate", Bundle.class);
            xposed.hook(actOnCreate).intercept(chain -> {
                Activity activity = (Activity) chain.getThisObject();
                if (activity != null) {
                    ModuleLog.attachContext(activity);
                    ClassLoader cl = activity.getClassLoader();
                    if (canLoadTargetClasses(cl)) {
                        installAll(xposed, cl);
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            log("Hook Activity.onCreate failed: " + t);
        }
    }

    public static synchronized void installAll(XposedInterface xposed, ClassLoader classLoader) {
        if (!HOOKED.compareAndSet(false, true)) {
            return;
        }
        log("Target classes available, installing purifier hooks with ClassLoader: " + classLoader);

        installContextCapture(xposed, classLoader);

        try {
            AdBlocker.install(xposed, classLoader);
            log("AdBlocker installed successfully.");
        } catch (Throwable t) {
            log("AdBlocker install error: " + t);
        }

        try {
            PopupBlocker.install(xposed, classLoader);
            log("PopupBlocker installed successfully.");
        } catch (Throwable t) {
            log("PopupBlocker install error: " + t);
        }

        try {
            BottomBarPurifier.install(xposed, classLoader);
            log("BottomBarPurifier installed successfully.");
        } catch (Throwable t) {
            log("BottomBarPurifier install error: " + t);
        }

        try {
            HomePagePurifier.install(xposed, classLoader);
            log("HomePagePurifier installed successfully.");
        } catch (Throwable t) {
            log("HomePagePurifier install error: " + t);
        }

        try {
            OrderPagePurifier.install(xposed, classLoader);
            log("OrderPagePurifier installed successfully.");
        } catch (Throwable t) {
            log("OrderPagePurifier install error: " + t);
        }

        try {
            UserPagePurifier.install(xposed, classLoader);
            log("UserPagePurifier installed successfully.");
        } catch (Throwable t) {
            log("UserPagePurifier install error: " + t);
        }

        try {
            NetworkAdInterceptor.install(xposed, classLoader);
            log("NetworkAdInterceptor installed successfully.");
        } catch (Throwable t) {
            log("NetworkAdInterceptor install error: " + t);
        }


        try {
            FlutterPageGuard.install(xposed, classLoader);
            log("FlutterPageGuard installed successfully.");
        } catch (Throwable t) {
            log("FlutterPageGuard install error: " + t);
        }

        log("All XiaoCanPurify hooks successfully initialized!");
    }

    /**
     * Captures a {@link Context} as early as possible so {@link ModuleLog} can persist to a file.
     * {@code Instrumentation.callApplicationOnCreate} runs once per process with the Application
     * instance; {@code ContextWrapper.attachBaseContext} and {@code Activity.onCreate} act as
     * fallbacks on roms where Instrumentation is patched.
     */
    private static void installContextCapture(XposedInterface xposed, ClassLoader classLoader) {
        try {
            Class<?> instrumentation = Class.forName("android.app.Instrumentation", false, classLoader);
            Method callApplicationOnCreate = instrumentation.getDeclaredMethod("callApplicationOnCreate", Application.class);
            xposed.hook(callApplicationOnCreate).intercept(chain -> {
                Object app = chain.getArg(0);
                if (app instanceof Context) {
                    ModuleLog.attachContext((Context) app);
                }
                return chain.proceed();
            });
            log("Context capture hook installed (Instrumentation)");
        } catch (Throwable t) {
            log("Context capture hook failed (Instrumentation): " + t);
        }

        try {
            Method attachBaseContext = android.content.ContextWrapper.class
                    .getDeclaredMethod("attachBaseContext", Context.class);
            xposed.hook(attachBaseContext).intercept(chain -> {
                Object ctx = chain.getArg(0);
                if (ctx instanceof Context) {
                    ModuleLog.attachContext((Context) ctx);
                }
                return chain.proceed();
            });
            log("Context capture hook installed (ContextWrapper)");
        } catch (Throwable t) {
            log("Context capture hook failed (ContextWrapper): " + t);
        }
    }

    public static void log(String message) {
        if (!ModuleLog.isEnabled()) {
            return;
        }
        ModuleLog.log(message);
        MainHook hook = instance;
        if (hook != null) {
            try {
                hook.log(Log.INFO, LOG_TAG, message);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void log(String message, Throwable tr) {
        if (!ModuleLog.isEnabled()) {
            return;
        }
        ModuleLog.log(message, tr);
        MainHook hook = instance;
        if (hook != null) {
            try {
                hook.log(Log.ERROR, LOG_TAG, message, tr);
            } catch (Throwable ignored) {
            }
        }
    }
}
