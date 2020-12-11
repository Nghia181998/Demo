package com.example.demo;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.List;

public class MyService extends AccessibilityService {
    private static volatile CharSequence foregroundPackageName;
    private static volatile CharSequence foregroundClassName;

    public static String foregroundPackageName() {
        return String.valueOf(foregroundPackageName);
    }

    public static String foregroundClassName() {
        return String.valueOf(foregroundClassName);
    }

    public MyService() {
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            foregroundPackageName = event.getPackageName();
            foregroundClassName = event.getClassName();
        }
    }

    @Override
    public void onInterrupt() {

    }
}