package com.example.demo;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedDispatcher;
import androidx.appcompat.widget.ViewUtils;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.util.zip.Inflater;

public class OverlayService extends Service {

    private Handler handler = new Handler();
    private View view;
    private static WindowManager windowManager2;
    private CurrentActivity currentActivity;
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            if(!AccessibilityServiceUtils.isEnabled(getApplicationContext(),GetPackageNameService.class)){
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
            handler.postDelayed(this, 5000);
        }
    };

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
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

    @Override
    public void onDestroy() {
        Log.e("Service", "onDestroy: " );
        super.onDestroy();
    }

    private void setCurrentActivity() {
        currentActivity = new CurrentActivity();
        Toast.makeText(this,currentActivity.getCurrentActivity(),Toast.LENGTH_LONG).show();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
       handler.post(runnable);
        Log.e("Service", "onStartCommand: " );
        return super.onStartCommand(intent, flags, startId);
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
        params.gravity=Gravity.CENTER|Gravity.CENTER;
        params.x=30;
        params.y=30;
        params.width = dm.widthPixels;
        params.height = dm.heightPixels;
        windowManager2.addView(view, params);
    }
    public static void enableDisableView(View view, boolean enabled) {
        view.setEnabled(enabled);
        if ( view instanceof ViewGroup ) {
            ViewGroup group = (ViewGroup)view;

            for ( int idx = 0 ; idx < group.getChildCount() ; idx++ ) {
                enableDisableView(group.getChildAt(idx), enabled);
            }
        }
    }

}
