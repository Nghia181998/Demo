package com.example.demo;

import android.app.Application;
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
    public String getCurrentActivity(Context context) {
        String packageName,activityName;
        if (AccessibilityServiceUtils.isEnabled(context, MyService.class)) {
            packageName = MyService.foregroundPackageName();
            activityName = MyService.foregroundClassName();
            if (packageName.equals("") || activityName.equals(""))
                return "";
        } else {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return ("làm gì đó đi");
        }
        return packageName + "/n" + activityName;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = getApplicationContext();
    }
}
