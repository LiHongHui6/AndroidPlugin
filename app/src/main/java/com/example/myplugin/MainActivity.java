package com.example.myplugin;


import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;

import java.io.File;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.tv_load_plugin).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //加载插件
                PluginManager.getInstance().loadPlugin(getApplication(), Environment.getExternalStorageDirectory() + File.separator + "plugin-debug.apk");
            }
        });
        findViewById(R.id.tv_stat_plugin_activity).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //启动插件Activity
                Intent intent = new Intent();
                intent.setComponent(new ComponentName("com.example.plugin", "com.example.plugin.MainActivity"));
                startActivity(intent);
            }
        });

        findViewById(R.id.tv_load_plugin2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //加载插件
                PluginManager.getInstance().loadPlugin(getApplication(), Environment.getExternalStorageDirectory() + File.separator + "plugin2-debug.apk");
            }
        });
        findViewById(R.id.tv_stat_plugin2_activity).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //启动插件Activity
                Intent intent = new Intent();
                intent.setComponent(new ComponentName("com.example.plugin2", "com.example.plugin2.MainActivity"));
                startActivity(intent);
            }
        });

        findViewById(R.id.tv_stat_activity).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, TestActivity.class);
                startActivity(intent);
            }
        });
    }
}
