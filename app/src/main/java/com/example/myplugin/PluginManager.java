package com.example.myplugin;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
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
    private static final String TAG = "TAG_PluginManager";
    private volatile static PluginManager instance = new PluginManager();

    public static PluginManager getInstance(){
        return instance;
    }

    public void loadPlugin(Application context, String apkPath){

        /*
         * hookPMS避免插件LoadedApk创建Application时报错
         */
        try {
            hookGetPackageInfo(context);
        } catch (Exception e) {
            e.printStackTrace();
        }

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
            if (Build.VERSION.SDK_INT < 26){
                hookAMSCheck25(context);
            }else {
                hookAMSCheck26(context);
            }
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

    private void hookAMSCheck25(final Application context) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException {
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

                            Intent intentReal = (Intent) args[2];

                            String packageName = context.getPackageName();
                            String packageName1 = intentReal.getComponent().getPackageName();

                            if (!packageName.equals(packageName1)){
                                //跳转插件Activity
                                //将插件Activity替换成PlaceHolderActivity绕过AMS检查
                                Log.d(TAG, "插件Activity替换成PlaceHolderActivity绕过AMS检查");
                                Intent intent = new Intent(context, PlaceHolderActivity.class);
                                intent.putExtra("actionIntent", intentReal);
                                args[2] = intent;
                            }
                        }
                        //Log.d(TAG, "拦截到了IActivityManager里面的方法" + method.getName());
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

    private void hookAMSCheck26(final Application context) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        // 动态代理
        Class mIActivityManagerClass = Class.forName("android.app.IActivityManager");


        Class mActivityManager = Class.forName("android.app.ActivityManager");
        final Object mIActivityManager = mActivityManager.getMethod("getService").invoke(null);

        Object mIActivityManagerProxy = Proxy.newProxyInstance(

                context.getClassLoader(),

                new Class[]{mIActivityManagerClass},

                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("startActivity".equals(method.getName())) {

                            Intent intentReal = (Intent) args[2];

                            String packageName = context.getPackageName();
                            String packageName1 = intentReal.getComponent().getPackageName();

                            if (!packageName.equals(packageName1)){
                                //跳转插件Activity
                                //将插件Activity替换成PlaceHolderActivity绕过AMS检查
                                Log.d(TAG, "插件Activity替换成PlaceHolderActivity绕过AMS检查");
                                Intent intent = new Intent(context, PlaceHolderActivity.class);
                                intent.putExtra("actionIntent", intentReal);
                                args[2] = intent;
                            }
                        }
                        Log.d(TAG, "拦截到了IActivityManager里面的方法" + method.getName());
                        return method.invoke(mIActivityManager, args);
                    }
                });

        Field mIActivityManagerSingleton = mActivityManager.getDeclaredField("IActivityManagerSingleton");
        mIActivityManagerSingleton.setAccessible(true); // 授权
        Object oIActivityManagerSingleton = mIActivityManagerSingleton.get(null);

        Class mSingletonClass = Class.forName("android.util.Singleton");
        Field mInstanceField = mSingletonClass.getDeclaredField("mInstance");
        mInstanceField.setAccessible(true); // 让虚拟机不要检测 权限修饰符
        mInstanceField.set(oIActivityManagerSingleton, mIActivityManagerProxy); // 替换是需要gDefault
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

                            String packageName = actionIntent.getComponent().getPackageName();
                            activityInfo.name = actionIntent.getComponent().getClassName();
                            activityInfo.packageName = packageName;
                            activityInfo.applicationInfo.packageName = packageName;
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
            Class mActivityThreadClass = Class.forName("android.app.ActivityThread");
            Field sCurrentActivityThreadField = mActivityThreadClass.getDeclaredField("sCurrentActivityThread");
            sCurrentActivityThreadField.setAccessible(true);

            Field sPackageManagerField = mActivityThreadClass.getDeclaredField("sPackageManager");
            sPackageManagerField.setAccessible(true);
            final Object packageManager = sPackageManagerField.get(null);

            Class mIPackageManagerClass = Class.forName("android.content.pm.IPackageManager");

            Object mIPackageManagerProxy = Proxy.newProxyInstance(context.getClassLoader(),

                    new Class[]{mIPackageManagerClass}, // 要监听的接口

                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            if ("getPackageInfo".equals(method.getName())) {
                                return new PackageInfo();
                            }
                            return method.invoke(packageManager, args);
                        }
                    });


            sPackageManagerField.set(null, mIPackageManagerProxy);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * SDK源码中Activity由LoadedApk创建
     * LoadedApk缓存在ActivityThread的mPackages中
     * 所以需要将为插件创建LoadedApk并存放进ActivityThread的mPackages中
     */
    private void hookLoadedApk(Context context, String apkPath) throws FileNotFoundException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException, InstantiationException {
        File file = new File(apkPath);
        if (!file.exists()) {
            throw new FileNotFoundException("插件包不存在..." + file.getAbsolutePath());
        }
        String pluginPath = file.getAbsolutePath();

        Class mActivityThreadClass = Class.forName("android.app.ActivityThread");

        Object mActivityThread = mActivityThreadClass.getMethod("currentActivityThread").invoke(null);

        Field mPackagesField = mActivityThreadClass.getDeclaredField("mPackages");
        mPackagesField.setAccessible(true);
        Object mPackagesObj = mPackagesField.get(mActivityThread);

        Map mPackages = (Map) mPackagesObj;

        Class mCompatibilityInfoClass = Class.forName("android.content.res.CompatibilityInfo");
        Field defaultField = mCompatibilityInfoClass.getDeclaredField("DEFAULT_COMPATIBILITY_INFO");
        defaultField.setAccessible(true);
        /*
         * 创建LoadedApk需要的参数
         */
        Object defaultObj = defaultField.get(null);

        /*
         * 创建LoadedApk需要的参数
         */
        ApplicationInfo applicationInfo = getApplicationInfoAction(apkPath);

        /*
         * 反射调用ActivityThread的getPackageInfoNoCheck方法创建LoadedApk
         */
        Method mLoadedApkMethod = mActivityThreadClass.getMethod("getPackageInfoNoCheck", ApplicationInfo.class, mCompatibilityInfoClass);
        Object mLoadedApk = mLoadedApkMethod.invoke(mActivityThread, applicationInfo, defaultObj);

        File fileDir = context.getDir("pulginPathDir", Context.MODE_PRIVATE);

        ClassLoader classLoader = new DexClassLoader(pluginPath,fileDir.getAbsolutePath(), null, context.getClassLoader());

        Field mClassLoaderField = mLoadedApk.getClass().getDeclaredField("mClassLoader");
        mClassLoaderField.setAccessible(true);
        mClassLoaderField.set(mLoadedApk, classLoader);

        WeakReference weakReference = new WeakReference(mLoadedApk);
        mPackages.put(applicationInfo.packageName, weakReference);
    }

    private ApplicationInfo getApplicationInfoAction(String apkPath) throws InstantiationException, IllegalAccessException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException {
        Class mPackageParserClass = Class.forName("android.content.pm.PackageParser");

        Object mPackageParser = mPackageParserClass.newInstance();

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
