package com.example.demo;

import android.app.Application;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

public class CurrentActivity extends Application {
    private static Context mContext;

    public static Context getContext() {
        return mContext;
    }
    public String getCurrentActivity() {
        String currentActivity;
        synchronized (this) {
            currentActivity = getCurrentActivity(getContext());
        }
        return currentActivity;
    }
    public static String getCurrentActivity(Context context) {
        String packageName,activityName;
        if (AccessibilityServiceUtils.isEnabled(context, GetPackageNameService.class)) {
            packageName = GetPackageNameService.foregroundPackageName();
            activityName = GetPackageNameService.foregroundClassName();
            if (packageName.equals("") || activityName.equals(""))
                return "";
        } else {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return ("làm gì đó đi");
        }
        return packageName + "\n" + activityName;
    }
    public static String getCurrentOpenApp(Context context) {
        String packageName = "";
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.LOLLIPOP) {
            UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            UsageEvents usageEvents = usageStatsManager.queryEvents(System.currentTimeMillis() - 12000,
                    System.currentTimeMillis());
            // lấy app đang hiển thị trên màn hình
            while (usageEvents.hasNextEvent()) {
                UsageEvents.Event event = new UsageEvents.Event();
                usageEvents.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    packageName = event.getPackageName();
                }
            }
        }
        if (packageName.length() == 0) {
            if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.LOLLIPOP) {
                UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
                UsageEvents usageEvents = usageStatsManager.queryEvents(System.currentTimeMillis() - 12000,
                        System.currentTimeMillis());
                UsageEvents.Event event = new UsageEvents.Event();
                while (usageEvents.hasNextEvent()) {{
                    usageEvents.getNextEvent(event);
                    packageName = event.getPackageName();
                }}
            }
        }
        return packageName;
    }
    @Override
    public void onCreate() {
        super.onCreate();
        mContext = getApplicationContext();
    }
}
