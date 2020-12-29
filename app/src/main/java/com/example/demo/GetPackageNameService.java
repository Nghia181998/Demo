package com.example.demo;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;

import java.util.ArrayList;
import java.util.List;

public class GetPackageNameService extends AccessibilityService {
    private static volatile CharSequence foregroundPackageName;
    private static volatile CharSequence foregroundClassName;
    private  WindowManager windowManager2;
    CurrentActivity currentActivity;
    View view;
    public static String foregroundPackageName() {
        return String.valueOf(foregroundPackageName);
    }

    public static String foregroundClassName() {
        return String.valueOf(foregroundClassName);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        currentActivity = new CurrentActivity();
        LayoutInflater layoutInflater=(LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ConstraintLayout frameLayout = new ConstraintLayout(this) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode()==KeyEvent.KEYCODE_BACK) {
                    windowManager2.removeView(view);
//                    view = null;
                    Intent startMain = new Intent(Intent.ACTION_MAIN);
                    startMain.addCategory(Intent.CATEGORY_HOME);
                    startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(startMain);
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }

        };
        view=layoutInflater.inflate(R.layout.overlay_view, frameLayout);
        Log.e("Service", "onCreate: ");
    }

    public GetPackageNameService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        

    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            foregroundPackageName = event.getPackageName();
            foregroundClassName = event.getClassName();
            Toast.makeText(getApplicationContext(),CurrentActivity.getCurrentActivity(getApplicationContext()),Toast.LENGTH_LONG).show();
            Log.e("Active", "onAccessibilityEvent: "+ foregroundPackageName() );
            if (currentActivity.getCurrentActivity().contains("com.google.android.packageinstaller")) {
                showCustomPopupMenu();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sendBroadcast(new Intent(this,KilledServiceReceiver.class).setAction("kill.my.se"));
        Log.e("AccessibilityService", "onDestroy: " );
    }
    
    @Override
    public void onInterrupt() {

    }
    public void showCustomPopupMenu()
    {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        windowManager2 = (WindowManager)getSystemService(WINDOW_SERVICE);
        WindowManager.LayoutParams params = null;
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity= Gravity.CENTER|Gravity.CENTER;
        params.x=30;
        params.y=30;
        params.width = dm.widthPixels;
        params.height = dm.heightPixels;
        windowManager2.addView(view, params);
    }
}