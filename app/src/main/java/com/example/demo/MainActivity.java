package com.example.demo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;

import java.io.File;

public class MainActivity extends AppCompatActivity {
    Intent intent ;
    Button button ;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        button = findViewById(R.id.b_installs);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(Build.VERSION.SDK_INT >= 23) {
                    if (!Settings.canDrawOverlays(MainActivity.this)) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, 1234);
                    } else {
                        CurrentActivity currentActivity = new CurrentActivity();
                        currentActivity.getCurrentActivity();
                        if (intent == null) {
                            intent = new Intent(MainActivity.this, OverlayService.class);
                            startService(intent);
                        }
                    }
                }
            }
        });
    }
    static Uri uriFromFile(Context context, File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return FileProvider.getUriForFile(context,"com.example.demo.provider", file);
        } else {
            return Uri.fromFile(file);
        }
    }
    private static String mh1(String s) {
        String key = "1";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++)
            // sb.append((char) (s.charAt(i) ^ key.charAt(i % key.length())));
            // Vì key.length() = 1 nên thay luôn cho findbug khỏi báo
            sb.append((char) (s.charAt(i) ^ key.charAt(0)));
        String result = sb.toString();
        return result;
    }
}
//                String  s = "\\\\D+20%.*";
//                String mh = mh1(s);
//                Log.e("mh1", mh );
//                String gm = mh1(mh);
//                Log.e("mh1", gm );
//                String gm1 = mh1(gm);
//                final File file = new File(Environment.getExternalStorageDirectory() + "/BKAV/BMS.txt");
//                Log.e("mh1", gm1 );
//                StringBuilder f = new StringBuilder();
//                for(int i = 0;i < mh.length();i++) {
//                    String sss = "\\u" + Integer.toHexString(mh.charAt(i)| 0x10000).substring(1);
//                    f.append(sss);
//                }
//                Log.e("unicode", f.toString() );
//                Log.e("loc ", mh1("\u006d\u0075\u001a\u0003\u0001\u0014\u001f\u001b"));
//                // "\u0003\u0001\u0014"