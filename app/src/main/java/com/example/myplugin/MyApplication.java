package com.example.myplugin;

import android.app.Application;

/**
 * 作者：LiHonghui on 2020/6/10 0010 00:41
 * 邮箱：382039099@qq.com
 */
public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        PluginManager.getInstance().steerByAMSCheck(this);
    }
}
