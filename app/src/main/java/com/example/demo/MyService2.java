package com.example.demo;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.widget.ViewUtils;

import java.util.zip.Inflater;

public class MyService2 extends Service {
    static  Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
    private Handler handler = new Handler();
    private boolean isStop = false;
    private CurrentActivity currentActivity;
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            setCurrentActivity();
            if (currentActivity.getCurrentActivity().contains("com.willme.topactivity")) {
                generateFullScreenParams().gravity = Gravity.RIGHT | Gravity.TOP;
                generateFullScreenParams().setTitle("Load Average");
                WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                wm.addView(getOverLayout(getApplicationContext()), generateFullScreenParams()); ;
            }
            handler.postDelayed(this, 5000);
        }
    };
    public MyService2() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {

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
        handler.postDelayed(runnable, 5000);
        Log.e("Service", "onStartCommand: " );
        return super.onStartCommand(intent, flags, startId);
    }

    public static WindowManager.LayoutParams generateFullScreenParams() {
        return new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,

                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
    }
    private View getOverLayout(Context context){
        return View.inflate(context,R.layout.overlay_view,null);
    }
}
