package com.bkav.safebox.applock;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.fingerprint.FingerprintManager;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import androidx.core.view.ViewCompat;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bkav.safebox.R;
import com.bkav.safebox.applock.notification.BlockNotificationActivity;
import com.bkav.safebox.database.BkavHelperLockApp;
import com.bkav.safebox.main.MyPreference;
import com.bkav.safebox.main.SafeBoxConstants;
import com.bkav.safebox.setting.SafeBoxSettingActivity;
import com.bkav.util.AppUtils;
import com.bkav.util.Constants;
import com.bkav.util.preference.BMSPreferenceManager;
import com.bkav.util.security.FingerprintUiHelper;
import com.bkav.util.view.pattern.PatternUtils;
import com.bkav.util.view.pattern.PatternView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Class service thực hiện việc chạy và check tính năng khóa ứng dụng
 * KhanhVD create 01/2018
 */
public class AppLockService extends Service {

    // Send Back
    private static final String ACTION_GLOBAL_ACTION = "action_global_action";

    public static boolean isAppOpen = false;

    // Package hien tai
    public static String sCurrentPackageName = "";

    public static boolean isLock = false; // Biến check xem có thực hiện việc khóa không?
    //public static ScheduledExecutorService sScheduledExecutor;
    public static boolean isNeedLock = true;

    // Thoi gian Server thưc hien viec check app
    //----------------------------------------------------------------------------------------------
    public static final long TIMEOUT_CHECK_APP = 300L; // 0,5 S
    public static ScheduledExecutorService sScheduledExecutorService; // Service chay lien tuc de thuc hien viec check app thay doi
    //String mScheduledExecutorPackageName = "";

    // Chua PackageName ma Accessibility tra ve khi thay doi giao dien
    public static String sAccessibilityPackageName = "";
    public static boolean isBack = false;

    // Provider
    String mAppLockSelection = "";
    String[] mAppLockSelectionArgs = null;

    // Bien kiem tra su kien tra ve cua dong tat man hinh
    static final String LOCK_TIME = "_lock_time"; // Tien to luu thoi gian thoat ung dung
    static final String LOCK_SCREEN = "_lock_screen"; // Tien to luu su kien tat man hinh

    // View
    private FrameLayout sViewLockApp;
    FingerprintUiHelper mFingerprintUiHelper;

    // Quản lý add view overlay app
    private static WindowManager sWindowManager;

    // Info
    public static String sPackageNameLockOpen = ""; // Luu lai Package name da dc mo khoa
    MyPreference mPreference;
    private LockAppRecevier mLockAppRecevier;
    public static AppLockService sLockAppService = null;

    // Handle---------------------------------------------------------------------------------------
    private static final String HANDLE_KEY_PACKAGE_NAME = "data_package_name";
    private static final String HANDLE_KEY_FAKE_APP = "data_fake_app";

    private static final int WHAT_KEY_LOCK_APP = 1; // Khoa app
    private static final int WHAT_KEY_UNLOCK_APP = 2; // Mo khoa app
    private static final int WHAT_KEY_EXIT_APP = 3; // Tat, thoat khoi app

    //dannvb: bien nay dung de dem so man hinh da thay doi  khi khoa man hinh
    private int mScreenCount = 0;
    // bien day de check xem truoc khi tat man hinh dang o ung dung khoa hay ung dung khac
    private boolean mIsInBeforLock = false;

    // Dannvb: Biến dung de luu số lần nhap sai mat khau
    private int mCountWrongPasswordInput = 0;

    Handler mHandlerLockApp = new Handler() {
        public void handleMessage(Message msg) {
            // Get data
            String packageName = msg.getData().getString(HANDLE_KEY_PACKAGE_NAME, "");
            switch(msg.what) {
                case WHAT_KEY_LOCK_APP:
                    mAppLockSelection = msg.getData().getString(LockAppUtils.EXTRA_LOCK_SELECTION, "");
                    mAppLockSelectionArgs = msg.getData().getStringArray(LockAppUtils.EXTRA_LOCK_SELECTION_ARGS);
                    // check null
                    if (packageName != null && packageName.length() > 0) {
                        // Lay Kieu khoa du lieu
                        String lockType = mPreference.getString(getString(R.string.key_preference_lock_type),
                                SafeBoxConstants.LOCK_TYPE_PASSWORD);
                        // Lay Fake
                        boolean isFake = false;
                        if (!packageName.equals(AppLockedProvider.URI_APP_LOCK_PROVIDER)) {
                            isFake = msg.getData().getBoolean(HANDLE_KEY_FAKE_APP, false);
                        }
                        // Khoa
                        lockApp(getApplicationContext(), packageName, lockType, isFake);
                    }

                    break;
                case WHAT_KEY_UNLOCK_APP:
                    // Unlock App
                    openAppLock(packageName);

                    break;
                case WHAT_KEY_EXIT_APP:
                    // Luu lai thoi gian thoat app khoa
                    if (sPackageNameLockOpen.length() > 0) {
                        setTimePreferenceLockTime(sPackageNameLockOpen);
                        sPackageNameLockOpen = "";
                    }

                    isAppOpen = false;

                    // Dong view
                    closeViewAppLock();

                    break;
            }
            super.handleMessage(msg);
        }
    };

    /**
     * Gui Handle khoa ung dung
     * @param appItem
     */
    private void handleLockApp(AppInfo appItem) {
        // Gui Handle Khoa
        Message message = new Message();
        message.what = WHAT_KEY_LOCK_APP;
        Bundle data = new Bundle();
        data.putCharSequence(HANDLE_KEY_PACKAGE_NAME, appItem.getPackageName());
        try {
            data.putBoolean(HANDLE_KEY_FAKE_APP, Integer.parseInt(appItem.getNote()) == 1);
        } catch (Exception e) {

        }
        message.setData(data);
        mHandlerLockApp.sendMessage(message);
    }

    /**
     * Gui Handle khoa ung dung
     * @param what
     * @param packageName
     */
    private void handleSendWhat(int what, String packageName) {
        // Gui Handle Khoa
        Message message = new Message();
        message.what = what;
        if (packageName != null && packageName.length() > 0) {
            Bundle data = new Bundle();
            data.putCharSequence(HANDLE_KEY_PACKAGE_NAME, packageName);
            message.setData(data);
        }
        mHandlerLockApp.sendMessage(message);
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        // Khóa màn hình thong bao bi chan
        lockNotificationBlock(intent, mHandlerLockApp);
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 10; i++) {
                    checkLockApp(AppUtils.getCurrentOpenApp(getApplicationContext()));
                    if (mIsInBeforLock) {
                        break;
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
        return super.onStartCommand(intent, flags, startId);
        //return START_STICKY;
    }

    @Override
    public void onCreate() {
        // Init
        sLockAppService = this;
        mPreference = MyPreference.getInstance(this);

        // Dang ki cac su kien cua viec khoa ung dung
        mLockAppRecevier = new LockAppRecevier();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        intentFilter.addAction(LockAppUtils.ACTION_LOCK_APP);
        intentFilter.addAction(Intent.ACTION_USER_PRESENT);
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mLockAppRecevier, intentFilter);

        // Bat dau khoi tao dich vu bat su kien thay doi app
        startServiceCheckAppChanged();

        super.onCreate();
    }

    @Override
    public void onDestroy() {
        try {
            // Khoi dong lai service neu co ung dung nao trong app lock
            LockAppUtils.startServiceLockApp(getApplicationContext());

            // huy dang ki lang nghe su kien
            if (mLockAppRecevier != null) {
                unregisterReceiver(mLockAppRecevier);
            }
            // Set null
            sLockAppService = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    public static final int CHECK_SERVICE_NOT_APP = 0; // Khonog co ung dung
    public static final int CHECK_SERVICE_ACCESSIBILITY = 1; // Service Accessibility chay
    public static final int CHECK_SERVICE_NOT_ACCESSIBILITY  = 2; // Service Accessibility ko chay

    /**
     * Neu ma service Accessibity da chay thi ko can khoi dong service check lien tuc
     * con lai thi khoi dong
     *
     * @return
     */
    public int startServiceCheckAppChanged() {
        // Neu da co tk BMS va co ung dung trong app lock
        if (AppUtils.checkAccountLogin(getApplicationContext()) &&
                BkavHelperLockApp.getInstance(getApplicationContext()).isExistAppLock()) {
            if (!AppUtils.checkServiceRunning(getApplicationContext(),
                    Constants.BMS_ACCESSIBILITY_SERVICE)) {
                // Bat dau dich vu chay lien tuc
                startScheduledCheckAppChanged();

                return CHECK_SERVICE_NOT_ACCESSIBILITY;
            } else {
                // Stop, Shutdown dich vu di
                stopScheduledCheckAppChanged();

                return CHECK_SERVICE_ACCESSIBILITY;
            }
        } else {
            // Stop, Shutdown dich vu di
            stopScheduledCheckAppChanged();
            // stop service
            stopSelf();

            return CHECK_SERVICE_NOT_APP;
        }
    }

    /**
     * Start check viec khoa ung dung theo thoi gian
     */
    private void startScheduledCheckAppChanged() {
        try {
            // Stop
            stopScheduledCheckAppChanged();

            // Bat dau khoi tao va chay
            sScheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
            sScheduledExecutorService.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    try {
                        // check lock app - Lay package hien tai
                        String packageName = LockAppUtils.getForegroundApp(sLockAppService);
                        // Log.e("KhanhVD", " " + packageName);
                        if (packageName != null && packageName.length() > 0 &&
                                !packageName.equals(sAccessibilityPackageName) &&
                                !AppUtils.isWhitePackageName(sLockAppService, packageName)) {
                            // Check white list
                            sAccessibilityPackageName = packageName;
                            if (sLockAppService != null) {
                                // check khoa app
                                checkLockApp(packageName);
                            }
                        }

                        // Check Logout
                        AppUtils.checkLogOutLockAppPackageName(getApplicationContext(), packageName);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, 0, TIMEOUT_CHECK_APP, TimeUnit.MILLISECONDS);
        } catch(RuntimeException e){
            e.printStackTrace();
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * stop check viec khoa ung dung theo thoi gian
     */
    public void stopScheduledCheckAppChanged() {
        try {
            if (sScheduledExecutorService != null) {
                sScheduledExecutorService.shutdown();
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Thuc hien viec kiem tra xem packageName hien tai co can khoa man hinh hay khong
     */
    public void checkLockApp(String packageName) {
        try {
            if (!TextUtils.isEmpty(packageName)) {
                // Lay check xem co la dung dung khoa ko
                AppInfo appItem = BkavHelperLockApp.getInstance(sLockAppService).loadAppInfo(sLockAppService, packageName);
                // check
                if (appItem != null) { // Co ung dung khoa
                    mIsInBeforLock = true;
                    // Neu dang khoa thi thoi
                    if (sViewLockApp == null || !ViewCompat.isAttachedToWindow(sViewLockApp)) {
                        // Check thoi gian
                        if (checkLockTime(packageName)) {
                            // Goi khoa
                            handleLockApp(appItem);
                        } else {
                            // Neu thuoc tuy chon khong bi khoa, nhu sau bao lau moi khoa thi xem nhu la mo khoa
                            handleSendWhat(WHAT_KEY_UNLOCK_APP, packageName);
                        }
                    }
                } else {
                    handleSendWhat(WHAT_KEY_EXIT_APP, packageName);
                    mIsInBeforLock = false;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Thuc hien viec kiem tra xem packageName hien tai co can khoa man hinh hay khong
     */
    public void checkLockAppWhenScreenOn(String packageName) {
        //Log.e("Khanhvd", "handleLockApp packageName = " + packageName);
        try {
            if (packageName != null && packageName.length() > 0) {
                // Lay check xem co la ung dung khoa ko
                AppInfo appItem = BkavHelperLockApp.getInstance(sLockAppService)
                        .loadAppInfo(sLockAppService, packageName);
                if (appItem != null) { // Co ung dung khoa
                    // Log.e("Khanhvd", "handleLockApp view = " + (sViewLockApp == null));
                    // Neu dang khoa thi thoi
                    if (sViewLockApp == null || !ViewCompat.isAttachedToWindow(sViewLockApp)) {
                        //Log.e("Khanhvd", "handleLockApp 1");
                        // Check thoi gian
                        if (checkLockWhenScreenOff(packageName) || // Check che do khoa tat ca khi man hinh tat
                                checkLockTimeWhenScreenOff(packageName) || // Khoa ung dung thi tat man hinh
                                !isAppOpen) {
                            //Log.e("Khanhvd", "handleLockApp 2");
                            // Goi khoa
                            handleLockApp(appItem);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Check xem co thuc hien viec khoa voi thoi gian khoa hay khong
     * @param packageName
     * @return true : Khoa, false : Khong
     */
    private boolean checkLockTime(String packageName) {
        int time = mPreference.getInt(getString(R.string.key_setting_lock_app_time_int), 0);
        // Check time khoa man hinh xu ly theo cai khac
        if (time != SafeBoxSettingActivity.KEY_LOCK_APP_SCREEN_OFF) {
            if (System.currentTimeMillis() -  getTimePreferenceLockTime(packageName) > time * 1000) {
                return true;
            }
        } else if (getPreferenceLockAppWhenLockScreen(packageName)) { // Tat sau khi khoa man hinh
            return true;
        }
        return false;
    }

    /**
     * Check xem co thuc hien viec khoa voi thoi gian sau khi khoa man hinh hay ko
     * @param packageName
     * @return true : Khoa, false : Khong
     */
    private boolean checkLockTimeWhenScreenOff(String packageName) {
        int time = mPreference.getInt(getString(R.string.key_setting_lock_app_time_int), 0);
        // Check time khoa man hinh xu ly theo cai khac
        return time == SafeBoxSettingActivity.KEY_LOCK_APP_SCREEN_OFF ||
                getPreferenceLockAppWhenLockScreen(packageName);
    }

    /**
     * Check su kien khoa man hinh, va tuy chon khoa man hinh luc bat may
     * @param packageName
     * @return true khoa, false khong
     */
    private boolean checkLockWhenScreenOff(String packageName) {
        // Thuc hien viec khoa ung dung vaf Ghi lai thoi diem dong ung dung
        return mPreference.getBoolean(getString(R.string.KEY_LOCK_WHEN_SCREEN_OFF), false);
    }

    /**
     * Lay thoi gian thoat lan truoc cua app duoc Khoa
     * @param packageName
     * @return
     */
    private long getTimePreferenceLockTime(String packageName) {
        return mPreference.getLong(packageName + LOCK_TIME, 0);
    }

    /**
     * Lay thoi gian thoat lan truoc cua app duoc Khoa
     * cu thay doi PackageName, hoac bi khoa la update lai
     * @param packageName
     * @return
     */
    private void setTimePreferenceLockTime(String packageName) {
        mPreference.putLong(packageName + LOCK_TIME, System.currentTimeMillis());
    }

    /**
     * Tra ve viec co khoa man hinh khi bat man hinh hay khong
     * @param packageName
     * @return true khoa, fale khong khoa
     */
    private boolean getPreferenceLockAppWhenLockScreen(String packageName) {
        return  mPreference.getBoolean(packageName + LOCK_SCREEN, true);
    }

    /**
     * Ghi lai bien co khoa man hinh hay khong trong tinh nang khoa tat ca cac app khi khoa man hinh
     * @param packageName
     * @param isLock
     * @return
     */
    private void setPreferenceLockAppWhenLockScreen(String packageName, boolean isLock) {
        mPreference.putBoolean(packageName + LOCK_SCREEN, isLock);
    }

    /**
     * Reset tat cac cac bien khoa app luc tat man hinh cua tat ca cac app khoa
     * @param context
     * @param isLock
     */
    public static void setPreferenceLockAppWhenLockScreenAllApp(Context context, boolean isLock) {
        // Lay danh sach cac app
        ArrayList<String> listPackageName = BkavHelperLockApp.getInstance(context).loadPackageNameAppLock();
        for (String packageName : listPackageName) {
            MyPreference.getInstance(context).putBoolean(packageName + LOCK_SCREEN, isLock);
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Ham thuc hien viec goi khoa ung dung
     * @param context
     * @param packageName
     */
    private void lockApp(final Context context,
                         final String packageName,
                         final String lockType,
                         final boolean isFake) {
        isAppOpen = false;

        String pinhash = BMSPreferenceManager.getInstance(context).getKeyPin();
        String patternhash = BMSPreferenceManager.getInstance(context).getKeyPattern();
        boolean isBuildLog = BMSPreferenceManager.getInstance(getApplicationContext()).isBuildLog(false);

        // Khoi tao app
        try {
            // View
            if (sViewLockApp == null) {
                sViewLockApp = new FrameLayout(context);
                //sViewLockApp.setFocusableInTouchMode(true);
            }
        } catch (Exception e) {

        }

        // Pattern
        if (lockType.equals(SafeBoxConstants.LOCK_TYPE_PATTERN) && !TextUtils.isEmpty(patternhash)) {
            // Log
            if (isBuildLog) {
                AppUtils.writeLog("Safebox::startPattern");
            }
            lockAppPattern(context, packageName, isFake);
            // Pin
        } else if (lockType.equals(SafeBoxConstants.LOCK_TYPE_PIN) && !TextUtils.isEmpty(pinhash)) {
            // Log
            if (isBuildLog) {
                AppUtils.writeLog("Safebox::startPIN");
            }

            lockAppPIN(context, packageName, isFake);
            // Password
        } else {
            // Log
            if (isBuildLog) {
                AppUtils.writeLog("Safebox::startLockPassword");
            }
            lockAppPassword(context, packageName, isFake);
        }
    }

    /**
     * Khoi tao giao dien fake
     * @param context
     * @param packageName
     * @param lockType
     * @param viewError De khoi tao van tay
     */
    private void initViewFake(final Context context,
                              final String packageName,
                              final String lockType,
                              final View viewError,
                              final View viewMain) {
        // Layout Fake
        final LinearLayout llFake = (LinearLayout) viewMain.findViewById(R.id.ll_app_lock_fake_crash);
        llFake.setVisibility(View.VISIBLE);

        // Tittle
        String message = getString(R.string.lock_fake_message1) + " " +
                AppUtils.getAppName(context, packageName) + " " +
                getString(R.string.lock_fake_message2);
        // Set View
        ((TextView) viewMain.findViewById(R.id.tv_app_lock_fake_crash_message)).setText(message);

        // Button
        Button bOk = (Button) viewMain.findViewById(R.id.b_app_lock_fake_crash_ok);

        // Click
        bOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // Tat man hinh khoa
                closeViewAppLockAndGotoHome();
            }
        });

        // Long Click
        bOk.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                // View Gone
                llFake.setVisibility(View.GONE);

                // Khoi tao khoa van tay
                initPingerprintLock(packageName, lockType, viewError, viewMain);
                return true;
            }
        });
    }

    /**
     * Khoi tao khoa bang van tay
     * @param packageName
     * @param lockType SafeBoxConstants.LOCK_TYPE_PATTERN, SafeBoxConstants.LOCK_TYPE_PIN
     * @param viewError
     */
    private void initPingerprintLock(final String packageName,
                                     final String lockType,
                                     final View viewError,
                                     View viewMain) {
        // Check FingerPrint
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                mPreference.getBoolean(getString(R.string.key_setting_fingerprint), true)) {
            // Lay service
            FingerprintManager mFingerprintManager =
                    (FingerprintManager) getSystemService(Context.FINGERPRINT_SERVICE);

            // Check service
            if (mFingerprintManager != null && mFingerprintManager.isHardwareDetected()) {
                // Tat su kien lang nghe
                stopListeningFingerprint();

                // Khoi tao lai
                mFingerprintUiHelper = new FingerprintUiHelper
                        .FingerprintUiHelperBuilder(mFingerprintManager)
                        .build(this, new FingerprintUiHelper.Callback() {
                            @Override
                            public void onAuthenticated() {
                                // Mo khoa app
                                openAppLock(packageName);
                            }

                            @Override
                            public void onError() {
                                // Pattern
                                if (lockType.equals(SafeBoxConstants.LOCK_TYPE_PATTERN)) {
                                    TextView tvDrawPattern = (TextView)  viewError;
                                    tvDrawPattern.setText(getString(R.string.pl_fingerprint_fail));
                                    tvDrawPattern.startAnimation(AppUtils.textShakeError());
                                } else if (lockType.equals(SafeBoxConstants.LOCK_TYPE_PIN) ||
                                        lockType.equals(SafeBoxConstants.LOCK_TYPE_PASSWORD)) {
                                    EditText etInputPass = (EditText)  viewError;
                                    etInputPass.setText("");
                                    etInputPass.setHint(getString(R.string.pl_fingerprint_fail));
                                    etInputPass.startAnimation(AppUtils.textShakeError());
                                }
                            }
                        });

                // Kiem tra xe co ho tro Fingerprint khong?
                if (mFingerprintUiHelper != null && mFingerprintUiHelper.isFingerprintAuthAvailable()) {
                    mFingerprintUiHelper.startListening();
                    // Hien thi anh Fingerprint
                    ((ImageView) viewMain.findViewById(R.id.iv_banner_app_lock_fingerprint)).setVisibility(View.VISIBLE);
                }
            }
        }
    }

    /**
     * Thuc hien viec khoa ung dung theo Pattern
     * @param context
     * @param packageName
     * @param isFake
     */
    private void lockAppPattern(final Context context,
                                final String packageName,
                                final boolean isFake) {
        // View
        View viewLockApp = (sLockAppService != null) ? View.inflate(sLockAppService, R.layout.app_lock_pattern, null) :
                View.inflate(context, R.layout.app_lock_pattern, null);

        // View
        final TextView tvDrawPattern = (TextView) viewLockApp.findViewById(R.id.tv_app_lock_pattern_note);
        final PatternView pvBoard = (PatternView) viewLockApp.findViewById(R.id.pv_app_lock_pattern_board);
        pvBoard.setInStealthMode(!mPreference.getBoolean(getString(R.string.key_setting_make_pattern_visible), true));
        pvBoard.setOnPatternListener(new PatternView.OnPatternListener() {
            @Override
            public void onPatternStart() {

            }

            @Override
            public void onPatternCleared() {

            }

            @Override
            public void onPatternCellAdded(List<PatternView.Cell> pattern) {

            }

            @Override
            public void onPatternDetected(List<PatternView.Cell> pattern) {
                if (BMSPreferenceManager.getInstance(getApplicationContext()).getKeyPattern()
                        .equals(PatternUtils.patternToSha1String(pattern))) {
                    // Pattern khớp với caí đã lưu thì tắt màn hình khóa
                    openAppLock(packageName);
                } else {
                    pvBoard.clearPattern();
                    tvDrawPattern.setText(getString(R.string.pattern_not_math));
                    tvDrawPattern.startAnimation(AppUtils.textShakeError());
                    captureImage(packageName);
                }
            }
        });

        // Tao giao dien Blur
        AppUtils.initBackgroundBlur(sLockAppService,
                (LinearLayout) viewLockApp.findViewById(R.id.ll_app_lock_pattern_background));

        // Check Fake
        if (isFake) {
            initViewFake(context, packageName, SafeBoxConstants.LOCK_TYPE_PATTERN, tvDrawPattern, viewLockApp);
        } else {
            // Khoi tao khoa van tay
            initPingerprintLock(packageName, SafeBoxConstants.LOCK_TYPE_PATTERN, tvDrawPattern, viewLockApp);
        }

        // Chek xem view da duoc them vao chuwa
        addViewLockApp(context, viewLockApp, packageName);
    }

    // Key quay lai
    final static int KEY_CODE_BACK = 66;

    // Key xoa so
    final static int KEY_CODE_DELETE = 67;

    /**
     * Thuc hien viec khoa ung dung theo PIN
     * @param context
     * @param packageName
     * @param isFake
     */
    private void lockAppPIN(final Context context,
                            final String packageName,
                            final boolean isFake) {
        // View
        View viewLockApp = (sLockAppService != null) ? View.inflate(sLockAppService, R.layout.app_lock_pin, null) :
                View.inflate(context, R.layout.app_lock_pin, null);

        // Edit Text
        final EditText etInputPass = (EditText) viewLockApp.findViewById(R.id.et_app_lock_pin_input_password);
        etInputPass.setText("");
        etInputPass.requestFocus();
        etInputPass.setFocusable(true);

        // Keyboard
        KeyboardView  keyboardView = (KeyboardView) viewLockApp.findViewById(R.id.keyboard_app_lock_pin);
        keyboardView.setKeyboard(new Keyboard(sLockAppService, R.xml.keyboard));
        keyboardView.setPreviewEnabled(false);
        keyboardView.setOnKeyboardActionListener(new KeyboardView.OnKeyboardActionListener() {
            @Override
            public void onPress(int primaryCode) {
                KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, primaryCode);
                etInputPass.dispatchKeyEvent(event);
            }

            @Override
            public void onRelease(int primaryCode) {
                if (primaryCode == KEY_CODE_DELETE && (etInputPass.getText().toString().length() == 0 || etInputPass.getText().toString().length() == 1)) {
                    etInputPass.setHint(getString(R.string.login_input_pass));
                } else if (primaryCode == KEY_CODE_BACK) {
                    closeViewAppLockAndGotoHome();
                }
            }

            @Override
            public void onKey(int primaryCode, int[] keyCodes) {

            }

            @Override
            public void onText(CharSequence text) {

            }

            @Override
            public void swipeLeft() {

            }

            @Override
            public void swipeRight() {

            }

            @Override
            public void swipeDown() {

            }

            @Override
            public void swipeUp() {

            }
        });

        // Check Button
        ((TextView) viewLockApp.findViewById(R.id.tv_app_lock_pin_login))
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // Nếu dùng activity để mở khóa thì chỉ cần check MD5 so với pass đã lưu
                        if (AppUtils.md5(etInputPass.getText().toString().getBytes())
                                .equals(BMSPreferenceManager.getInstance(getApplicationContext()).getKeyPin())) {
                            // Mo khoa app
                            openAppLock(packageName);
                        } else {
                            etInputPass.setText("");
                            etInputPass.setHint(getString(R.string.wrong_pass));
                            etInputPass.startAnimation(AppUtils.textShakeError());
                            captureImage(packageName);
                        }
                    }
                });
        // Tao giao dien Blur
        AppUtils.initBackgroundBlur(sLockAppService,
                (LinearLayout) viewLockApp.findViewById(R.id.ll_app_lock_pin_background));

        // Check Fake
        if (isFake) {
            initViewFake(context, packageName, SafeBoxConstants.LOCK_TYPE_PIN, etInputPass, viewLockApp);
        } else {
            // Khoi tao khoa van tay
            initPingerprintLock(packageName, SafeBoxConstants.LOCK_TYPE_PIN, etInputPass, viewLockApp);
        }

        // Chek xem view da duoc them vao chuwa
        addViewLockApp(context, viewLockApp, packageName);
    }

    /**
     * Thuc hien viec khoa ung dung theo Password mac dinh cua may
     * @param context
     * @param packageName
     * @param isFake
     */
    private void lockAppPassword(final Context context, final String packageName, boolean isFake) {
        // View
        View viewLockApp = (sLockAppService != null) ? View.inflate(sLockAppService, R.layout.app_lock_password, null) :
                View.inflate(context, R.layout.app_lock_password, null);

        // Edit Text
        final EditText etInputPass = (EditText) viewLockApp.findViewById(R.id.et_app_lock_password_input_pass);
        etInputPass.requestFocus();
        etInputPass.setFocusable(true);
        etInputPass.setClickable(true);

        // Text change
        etInputPass.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (etInputPass != null && s.length() == 1) {
                    etInputPass.setHint(getString(R.string.login_input_pass));
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        // Check Button
        ((TextView) viewLockApp.findViewById(R.id.tv_app_lock_password_login))
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // Tim Pass khi nhap
                        String password = AppUtils.md5Password(etInputPass.getText().toString()) ;
                        // Nếu đúng pass thì remove màn hình khóa
                        if (!TextUtils.isEmpty(password) && password.equals(BMSPreferenceManager.getInstance(sLockAppService).getMd5Pass(""))) {
                            // An ban phim
                            AppUtils.hideKeyboardInput(getApplicationContext(), etInputPass);
                            // Mo khoa app
                            openAppLock(packageName);
                            mCountWrongPasswordInput = 0;
                        } else {
                            etInputPass.setText("");
                            etInputPass.setHint(getString(R.string.wrong_pass));
                            etInputPass.startAnimation(AppUtils.textShakeError());
                            captureImage(packageName);
                        }
                    }
                });
        // Tao giao dien Blur
        AppUtils.initBackgroundBlur(sLockAppService,
                (LinearLayout) viewLockApp.findViewById(R.id.ll_app_lock_password));

        // Check Fake
        if (isFake) {
            initViewFake(context, packageName, SafeBoxConstants.LOCK_TYPE_PASSWORD, etInputPass, viewLockApp);
        } else {
            // Khoi tao khoa van tay
            initPingerprintLock(packageName, SafeBoxConstants.LOCK_TYPE_PASSWORD, etInputPass, viewLockApp);
        }

        // Chek xem view da duoc them vao chuwa
        addViewLockApp(context, viewLockApp, packageName);
    }

    /**
     * Ve view cho hien thi viec khoa app
     * @param context
     * @param view
     * @param packageName
     */
    private void addViewLockApp(final Context context,final View view, String packageName) {
        try {
            sViewLockApp = new FrameLayout(this) {
                public boolean dispatchKeyEvent(KeyEvent event) {
                    if (event.getKeyCode() == KeyEvent.KEYCODE_MOVE_HOME) {
                        if (ViewCompat.isAttachedToWindow(sViewLockApp)&& view != null) {
                            try {
                                sWindowManager.removeView(sViewLockApp);
                                AppUtils.gotoHomeLauncher(context);
                            } catch (Exception e) {

                            }
                        }
                    } else if (event.getKeyCode() == KeyEvent.KEYCODE_BACK){
                        if (ViewCompat.isAttachedToWindow(sViewLockApp) && view != null) {
                            try {
                                sWindowManager.removeView(sViewLockApp);
                                AppUtils.gotoHomeLauncher(context);
                            } catch (Exception e) {

                            }
                        }
                    }
                    return super.dispatchKeyEvent(event);
                }};
            sViewLockApp.addView(view);
            // Set Onclick back

            // WindowManager
            sWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

            // check and addview
            if (!ViewCompat.isAttachedToWindow(sViewLockApp)) {
                // Ve the moi loai
                if (!packageName.equals(AppLockedProvider.URI_APP_LOCK_PROVIDER)) {
                    sViewLockApp.setFocusableInTouchMode(false);
                    sWindowManager.addView(sViewLockApp, AppUtils.getLayoutParamsLockAppFocusable());
                } else {
                    sViewLockApp.setFocusableInTouchMode(true);
                    sWindowManager.addView(sViewLockApp, AppUtils.getLayoutParamsLockApp());
                }
            } else {
                // Neu co roi thi update
                sWindowManager.updateViewLayout(sViewLockApp, sViewLockApp.getLayoutParams());
            }
        } catch (Exception e) {

        }
    }

    /**
     * Ham mo thanh conng cua so khoa
     * @param packageName
     */
    private void openAppLock(String packageName) {
        try {
            // Set animation
            try {
                if (ViewCompat.isAttachedToWindow(sViewLockApp)) {
                    WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) sViewLockApp.getLayoutParams();
                    layoutParams.windowAnimations = R.style.Animation_AppLock;
                    final WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                    windowManager.updateViewLayout(sViewLockApp, sViewLockApp.getLayoutParams());
                }
            } catch (Exception e) {

            }

            if (!packageName.equals(AppLockedProvider.URI_APP_LOCK_PROVIDER)) {
                isAppOpen = true;
                // Luu lai Packge mo khoa
                sPackageNameLockOpen = packageName;
                // Luu lai viec mo khoa
                setPreferenceLockAppWhenLockScreen(packageName, false);
                // Dong view khoa ung dung
                closeViewAppLock();
            } else {
                // Xoa khoi DB theo Provider
                LockAppUtils.unlockAppDB(getApplicationContext(),
                        Uri.parse(AppLockedProvider.URI_APP_LOCK_PROVIDER),
                        mAppLockSelection, mAppLockSelectionArgs);
                // Close
                closeViewAppLockAndGotoHome();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        stopListeningFingerprint();
    }

    /**
     * Check App lock
     * @param event
     */
    public static void checkAppLockAccessibility(Context context, AccessibilityEvent event) {
        // Log.e("KhanhVD", "checkAppLockAccessibility: " + event.getPackageName() + " : " + event.getClassName());

        // Check Acount
        if (!AppUtils.checkAccountLogin(context)) {
            return;
        }

        // check lock app - Lay package hien tai
        String packageName = "";

        // get package name kiểu này trước để nhận diện cả app clone (multi user)
        packageName = event.getPackageName().toString();

        if (packageName.length() == 0) {
            packageName = LockAppUtils.getForegroundApp(sLockAppService);
        }

        //packageName = LockAppUtils.getForegroundApp(sLockAppService);

        // Trong android 10 không hiểu vì sao không lấy được package name hiện tại nên lấy theo kiểu khác
        if (packageName == null || packageName.length() == 0) {
            packageName = AppUtils.getCurrentOpenApp(context);
        }

        if (packageName != null && packageName.length() > 0 &&
                !packageName.equals(sAccessibilityPackageName)) {
            // Check white list
            sAccessibilityPackageName = packageName;
            if (sLockAppService != null && !AppUtils.isWhitePackageName(sLockAppService, packageName)) {
                // check khoa app
                sLockAppService.checkLockApp(packageName);
            }
        }

        // Check Logout
        AppUtils.checkLogOutLockAppPackageName(context, packageName);

        //        // Check Name
        //        if (AppLockService.sAccessibilityPackageName != null &&
        //                AppLockService.sAccessibilityPackageName.length() == 0 &&
        //                AppLockService.isBack) {
        //            AppLockService.isBack = false;
        //            return;
        //        }
        //        // Ghi lai PackageName
        //        CharSequence packageName = event.getPackageName();
        //
        //        // AppUtils.writeLog("checkAppLockAccessibility " + packageName);
        //        // check Lock app
        //        if (!TextUtils.isEmpty(packageName) &&
        //                !packageName.equals(AppLockService.sAccessibilityPackageName)) {
        //            // check whitelist
        //            if (!AppUtils.isWhitePackageNameClassName(context, packageName, event.getClassName(),
        //                    BMSPreferenceManager.getInstance(context)
        //                            .getBoolean(context.getString(R.string.key_setting_lock_chat_mini), false))) {
        //                // Log.e("KhanhVD", " " + event.getPackageName() + " : " + event.getClassName());
        //                AppLockService.sAccessibilityPackageName = packageName.toString();
        //                AppLockService.isBack = false;
        //                if (AppLockService.sLockAppService != null) {
        //                    // check khoa app
        //                    AppLockService.sLockAppService.checkLockApp(packageName.toString());
        //                }
        //            }
        //        }
    }

    /**
     * Thuc hien viec dong cua so va quay ve home
     */
    private void closeViewAppLockAndGotoHome() {
        // goto Home
        //        if (!AppUtils.checkServiceRunning(getApplicationContext(),
        //                com.bkav.util.Constants.BMS_ACCESSIBILITY_SERVICE)) {
        //            AppUtils.gotoHomeLauncher(getApplicationContext());
        //        } else {
        //            if (sLockAppService != null) {
        //                sLockAppService.sendBroadcast(new Intent().setAction(ACTION_GLOBAL_ACTION));
        //            }
        //        }

        AppUtils.sendKeyEvent(KeyEvent.KEYCODE_BACK);
        AppUtils.gotoHomeLauncher(getApplicationContext());

        // Close App lock
        closeViewAppLock();

        isBack = true;
        sAccessibilityPackageName = "";
        // startView();
    }

    /**
     * Dong view hien thi khoa ung dung
     */
    private void closeViewAppLock() {
        // Dong view
        try {
            // Dong tat ca cac view
            try {
                sViewLockApp.removeAllViews();
                sViewLockApp.startAnimation(AnimationUtils.loadAnimation(getApplicationContext(),
                        R.anim.flyin));
            } catch (Exception e) {

            }
            // Huy view tren window
            WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            windowManager.removeView(sViewLockApp);
        } catch (Exception e) {

        }

        // tat su kien lang nghe van tay
        stopListeningFingerprint();
    }

    /**
     * Tat su kien lang nghe khoa van tay
     */
    private void stopListeningFingerprint() {
        // Tat lang nghe su kien
        if (mFingerprintUiHelper != null) {
            mFingerprintUiHelper.stopListening();
            mFingerprintUiHelper = null;
        }
    }

    /**
     * Class: Nhận các sự kiện đăng kí sử dụng cho việc khóa ứng dụng
     */
    class LockAppRecevier extends BroadcastReceiver {
        final String SYSTEM_DIALOG_REASON_KEY = "reason";
        final String SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS = "globalactions";
        final String SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps"; // Recent task
        final String SYSTEM_DIALOG_REASON_HOME_KEY = "homekey";

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // check null
            if (action == null || action.length() <= 0) {
                return;
            }
            //Dannvb: dung de khoi tao lai van tay sau khi man hinh tat
            // phai khoi tao lai do he thong nham lan su kien van tay mo khoa man hinh với mo app
            mScreenCount ++; //dem so man hinh da duoc mo tu khi mo khoa ung dung
            //mo khoa xong den luc mo vao man hinh khoa cua app la 2 thay doi man hinh
            if(mScreenCount == 2 && mIsInBeforLock && checkLockWhenScreenOff(sCurrentPackageName)) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // Lay Kieu khoa du lieu
                        String lockType = mPreference.getString(
                                getString(R.string.key_preference_lock_type),
                                SafeBoxConstants.LOCK_TYPE_PASSWORD);
                        lockApp(getApplicationContext(), sCurrentPackageName, lockType, false);
                    }
                }, 1);
            }

            // Su kien dong tat cac cua so system
            if (action.equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
                String reason = intent.getStringExtra(SYSTEM_DIALOG_REASON_KEY);
                if (reason != null && reason.equals(SYSTEM_DIALOG_REASON_RECENT_APPS) &&
                        !AppUtils.checkServiceRunning(getApplicationContext(),
                                Constants.BMS_ACCESSIBILITY_SERVICE)) {
                    // Exit Lock
                    sAccessibilityPackageName = "";
                    closeViewAppLock();
                } else if (reason.equals(SYSTEM_DIALOG_REASON_HOME_KEY)) {
                    sAccessibilityPackageName = "";
                    closeViewAppLock();
                }
                // Su kien Mo khoa man hinh
            } else if (action.equals(Intent.ACTION_SCREEN_ON)) { // action.equals(Intent.ACTION_USER_PRESENT) ||
                // Start dich vu check app
                int ret = startServiceCheckAppChanged();
                //Log.e("Khanhvd", "ret " + ret);
                // Log.e("Khanhvd", "action " + action);

                // check cac co khoa khi man hinh mo, tat
                checkLockAppWhenScreenOn(sAccessibilityPackageName);
                // Su kien tat man hinh
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                // Log.e("Khanhvd", "ACTION_SCREEN_OFF");
                mScreenCount = 0;
                // Tat man hinh khoa
                closeViewAppLock();

                // Reset cac flag
                if (mPreference.getInt(getString(R.string.key_setting_lock_app_time_int), 0) ==
                        SafeBoxSettingActivity.KEY_LOCK_APP_SCREEN_OFF) {
                    // Reset
                    setPreferenceLockAppWhenLockScreenAllApp(context, true);
                }

                // Stop, Shutdown dich vu check app di
                stopScheduledCheckAppChanged();
                // Khoa ung dung
            } else if (action.equals(LockAppUtils.ACTION_LOCK_APP)) {
                // Check
                String packageName = intent.getStringExtra(LockAppUtils.EXTRA_LOCK_PACKAGE_NAME);
                Bundle data = new Bundle();
                if (packageName != null && packageName.equals(AppLockedProvider.URI_APP_LOCK_PROVIDER)) { // Sự kiện mở khóa app từ Provider
                    data = intent.getExtras();
                }
                data.putCharSequence(HANDLE_KEY_PACKAGE_NAME, packageName);
                Message message = new Message();
                message.what = WHAT_KEY_LOCK_APP;
                message.setData(data);
                mHandlerLockApp.sendMessage(message);
            }
        }
    }

    public static void lockNotificationBlock(Intent intent, Handler mHandlerLockApp) {
        if (intent != null && intent.getBooleanExtra(BlockNotificationActivity.EXTRA_LOCK_NOTIFICATION, false)) {
            Message message = new Message();
            message.what = WHAT_KEY_LOCK_APP;
            Bundle data = new Bundle();
            data.putCharSequence(HANDLE_KEY_PACKAGE_NAME, Constants.PACKAGE_BMS);
            try {
                data.putBoolean(HANDLE_KEY_FAKE_APP, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
            message.setData(data);
            mHandlerLockApp.sendMessage(message);
        }
    }

    /**
     * Dannvb: Hàm chụp ảnh khi nhập sai mat khau
     */
    public void captureImage(String packageName) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                int numberInputWrongSetting = BMSPreferenceManager.getInstance(getApplicationContext())
                        .getTimeWrongPasswordInput();
                // Nếu numberInputWrongSetting == 3 tương đương với việc không chụp ảnh khi nhập sai
                if (numberInputWrongSetting == 3) {
                    return;
                } else {
                    mCountWrongPasswordInput++;
                    // nếu Mà số lần nhập sai bằng với cài đặt thì chụp ảnh
                    if (mCountWrongPasswordInput == (numberInputWrongSetting * 2) + 1) {

                        // Lấy hướng hiện tại của màn hình
                        Display display = ((WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
                        int rotation = display.getRotation();

                        // Gọi hàm chụp ảnh
                        CaptureImage.captureImage(getApplicationContext(), packageName, rotation);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
