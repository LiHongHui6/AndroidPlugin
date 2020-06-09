package com.example.myplugin;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

import dalvik.system.DexClassLoader;

/**
 * 作者：LiHonghui on 2020/6/8 0008 23:11
 * 邮箱：382039099@qq.com
 */
public class PluginManager {
    private static final String TAG = "PluginManager";
    private volatile static PluginManager instance = new PluginManager();

    public static PluginManager getInstance(){
        return instance;
    }

    public void addPlugin(Application context, String apkPath){
        /*
         * 为插件在宿主中创建LoadedApk，让宿主能够加载插件中的类及资源文件
         */
        try {
            hookLoadedApk(context, apkPath);
        } catch (Exception e) {
            Log.d(TAG, "hookLoadedApk fail:" + e.getMessage());
            e.printStackTrace();
        }
    }

    public void steerByAMSCheck(Application context){
        /*
         * 绕过ActivityManagerService检测
         * 由于插件Activity没有在宿主的AndroidManifest中注册，宿主直接启动会抛出异常ActivityNotFoundException
         * 所以当宿主启动插件Activity时需要现将插件Activity替换为宿主中预先定义好的占位Activity，占位Activity需要在
         * 宿主的AndroidManifest中进行注册
         * 以绕过ActivityManagerService检测
         */
        try {
            hookAMSCheck(context);
        } catch (Exception e) {
            Log.d(TAG, "hookAMSCheck fail:" + e.getMessage());
            e.printStackTrace();
        }

        /*
         * 启动Activity前将被替换的插件Activity恢复回来
         */
        try {
            hookLaunchActivity(context);
        } catch (Exception e) {
            Log.d(TAG, "hookLaunchActivity fail:" + e.getMessage());
            e.printStackTrace();
        }
    }

    private void hookAMSCheck(final Application context) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        // 动态代理
        Class mIActivityManagerClass = Class.forName("android.app.IActivityManager");


        Class mActivityManagerNativeClass2 = Class.forName("android.app.ActivityManagerNative");
        final Object mIActivityManager = mActivityManagerNativeClass2.getMethod("getDefault").invoke(null);

        Object mIActivityManagerProxy = Proxy.newProxyInstance(

                context.getClassLoader(),

                new Class[]{mIActivityManagerClass},

                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("startActivity".equals(method.getName())) {

                            //将插件Activity替换成PlaceHolderActivity绕过AMS检查
                            Log.d(TAG, "插件Activity替换成PlaceHolderActivity绕过AMS检查");
                            Intent intent = new Intent(context, PlaceHolderActivity.class);
                            intent.putExtra("actionIntent", ((Intent) args[2]));
                            args[2] = intent;
                        }
                        Log.d(TAG, "拦截到了IActivityManager里面的方法" + method.getName());
                        return method.invoke(mIActivityManager, args);
                    }
                });

        Class mActivityManagerNativeClass = Class.forName("android.app.ActivityManagerNative");
        Field gDefaultField = mActivityManagerNativeClass.getDeclaredField("gDefault");
        gDefaultField.setAccessible(true); // 授权
        Object gDefault = gDefaultField.get(null);

        Class mSingletonClass = Class.forName("android.util.Singleton");
        Field mInstanceField = mSingletonClass.getDeclaredField("mInstance");
        mInstanceField.setAccessible(true); // 让虚拟机不要检测 权限修饰符
        mInstanceField.set(gDefault, mIActivityManagerProxy); // 替换是需要gDefault
    }

    private void hookLaunchActivity(Application context) throws NoSuchFieldException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Field mCallbackFiled = Handler.class.getDeclaredField("mCallback");
        mCallbackFiled.setAccessible(true);

        Class mActivityThreadClass = Class.forName("android.app.ActivityThread");
        Object mActivityThread = mActivityThreadClass.getMethod("currentActivityThread").invoke(null);

        Field mHField = mActivityThreadClass.getDeclaredField("mH");
        mHField.setAccessible(true);
        Handler mH = (Handler) mHField.get(mActivityThread);

        mCallbackFiled.set(mH, new MyCallback(mH, context)); // 替换 增加我们自己的实现代码
    }
    public final int LAUNCH_ACTIVITY = 100;
    class MyCallback implements Handler.Callback {

        private Handler mH;
        private Application context;

        public MyCallback(Handler mH, Application context) {
            this.mH = mH;
            this.context = context;
        }

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {

                case LAUNCH_ACTIVITY:
                    //hook Activity启动将被替换的插件Activity恢复回来
                    Object obj = msg.obj; //ActivityClientRecord
                    try {
                        Field intentField = obj.getClass().getDeclaredField("intent");
                        intentField.setAccessible(true);

                        Intent intent = (Intent) intentField.get(obj);
                        //hook点1中存入的真实的插件Intent
                        Intent actionIntent = intent.getParcelableExtra("actionIntent");
                        if (actionIntent != null) {
                            //把占位Activity换成插件Activity
                            intentField.set(obj, actionIntent);
                            Field activityInfoField = obj.getClass().getDeclaredField("activityInfo");
                            activityInfoField.setAccessible(true);
                            ActivityInfo activityInfo = (ActivityInfo) activityInfoField.get(obj);

                            if (actionIntent.getPackage() == null) { //插件
                                activityInfo.applicationInfo.packageName = actionIntent.getComponent().getPackageName();
                                hookGetPackageInfo(context);
                            } else { // 宿主
                                activityInfo.applicationInfo.packageName = actionIntent.getPackage();
                            }
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
            }
            mH.handleMessage(msg);
            return true;
        }
    }
    private void hookGetPackageInfo(Context context) {
        try {
            // sPackageManager 替换  我们自己的动态代理
            Class mActivityThreadClass = Class.forName("android.app.ActivityThread");
            Field sCurrentActivityThreadField = mActivityThreadClass.getDeclaredField("sCurrentActivityThread");
            sCurrentActivityThreadField.setAccessible(true);

            Field sPackageManagerField = mActivityThreadClass.getDeclaredField("sPackageManager");
            sPackageManagerField.setAccessible(true);
            final Object packageManager = sPackageManagerField.get(null);

            /**
             * 动态代理
             */
            Class mIPackageManagerClass = Class.forName("android.content.pm.IPackageManager");

            Object mIPackageManagerProxy = Proxy.newProxyInstance(context.getClassLoader(),

                    new Class[]{mIPackageManagerClass}, // 要监听的接口

                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            if ("getPackageInfo".equals(method.getName())) {
                                return new PackageInfo();
                            }
                            // 让系统正常继续执行下去
                            return method.invoke(packageManager, args);
                        }
                    });


            // 替换  狸猫换太子   换成我们自己的 动态代理
            sPackageManagerField.set(null, mIPackageManagerProxy);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void hookLoadedApk(Context context, String apkPath) throws FileNotFoundException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException, InstantiationException {
        File file = new File(apkPath);
        if (!file.exists()) {
            throw new FileNotFoundException("插件包不存在..." + file.getAbsolutePath());
        }
        String pluginPath = file.getAbsolutePath();

        // mPackages 添加 自定义的LoadedApk
        // final ArrayMap<String, WeakReference<LoadedApk>> mPackages 添加自定义LoadedApk
        Class mActivityThreadClass = Class.forName("android.app.ActivityThread");

        // 执行此方法 public static ActivityThread currentActivityThread() 拿到 ActivityThread对象
        Object mActivityThread = mActivityThreadClass.getMethod("currentActivityThread").invoke(null);

        Field mPackagesField = mActivityThreadClass.getDeclaredField("mPackages");
        mPackagesField.setAccessible(true);
        Object mPackagesObj = mPackagesField.get(mActivityThread);

        Map mPackages = (Map) mPackagesObj;

        Class mCompatibilityInfoClass = Class.forName("android.content.res.CompatibilityInfo");
        Field defaultField = mCompatibilityInfoClass.getDeclaredField("DEFAULT_COMPATIBILITY_INFO");
        defaultField.setAccessible(true);
        Object defaultObj = defaultField.get(null);

        ApplicationInfo applicationInfo = getApplicationInfoAction(apkPath);

        Method mLoadedApkMethod = mActivityThreadClass.getMethod("getPackageInfoNoCheck", ApplicationInfo.class, mCompatibilityInfoClass);
        Object mLoadedApk = mLoadedApkMethod.invoke(mActivityThread, applicationInfo, defaultObj);

        File fileDir = context.getDir("pulginPathDir", Context.MODE_PRIVATE);

        // 自定义 加载插件的 ClassLoader
        ClassLoader classLoader = new DexClassLoader(pluginPath,fileDir.getAbsolutePath(), null, context.getClassLoader());

        Field mClassLoaderField = mLoadedApk.getClass().getDeclaredField("mClassLoader");
        mClassLoaderField.setAccessible(true);
        mClassLoaderField.set(mLoadedApk, classLoader); // 替换 LoadedApk 里面的 ClassLoader

        WeakReference weakReference = new WeakReference(mLoadedApk); // 放入 自定义的LoadedApk --》 插件的
        mPackages.put(applicationInfo.packageName, weakReference); // 增加了我们自己的LoadedApk
    }

    private ApplicationInfo getApplicationInfoAction(String apkPath) throws InstantiationException, IllegalAccessException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException {
        // 执行此public static ApplicationInfo generateApplicationInfo方法，拿到ApplicationInfo
        Class mPackageParserClass = Class.forName("android.content.pm.PackageParser");

        Object mPackageParser = mPackageParserClass.newInstance();

        // generateApplicationInfo方法的类类型
        Class $PackageClass = Class.forName("android.content.pm.PackageParser$Package");
        Class mPackageUserStateClass = Class.forName("android.content.pm.PackageUserState");

        Method mApplicationInfoMethod = mPackageParserClass.getMethod("generateApplicationInfo",$PackageClass,
                int.class, mPackageUserStateClass);

        File file = new File(apkPath);
        String pulginPath = file.getAbsolutePath();

        Object mPackage = null;
        try {
            Method mPackageMethod = mPackageParserClass.getMethod("parsePackage", File.class, int.class);
            mPackage = mPackageMethod.invoke(mPackageParser, file, PackageManager.GET_ACTIVITIES);
        } catch (Exception e) {
            e.printStackTrace();
        }

        ApplicationInfo applicationInfo = (ApplicationInfo)
                mApplicationInfoMethod.invoke(mPackageParser, mPackage, 0, mPackageUserStateClass.newInstance());

        applicationInfo.publicSourceDir = pulginPath;
        applicationInfo.sourceDir = pulginPath;

        return applicationInfo;
    }
}
