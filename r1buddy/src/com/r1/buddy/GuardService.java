package com.r1.buddy;

import android.app.Notification;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Method;

public class GuardService extends Service {
    public static final String ACTION_RECONNECT = "com.r1.buddy.RECONNECT";

    private static final String TAG = "R1Buddy";
    private static final String ACTION_A2DP_STATE =
            "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED";
    private static final long BOOT_MUTE_WINDOW_MS = 120000L;
    private static final long MUTE_MS = 25000L;
    private static final long DISCOVERABLE_MS = 120000L;
    private static final long RECONNECT_DISCOVERABLE_MS = 60000L;
    private static final long ENFORCE_MS = 8000L;
    // BluetoothProfile.A2DP_SINK 是 @hide 常量，SDK 里没有，直接用字面值
    private static final int PROFILE_A2DP_SINK = 11;
    // 只在开机后这段时间内主动回连，避免用户手动断开后被反复拉回
    private static final long BOOT_RECONNECT_WINDOW_MS = 300000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothAdapter adapter;
    private AudioManager audio;
    private SharedPreferences prefs;
    private boolean receiverRegistered;
    private boolean a2dpConnected;
    private BluetoothProfile sink;
    private boolean sinkRequested;

    private final BroadcastReceiver a2dpReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
            if (state == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "A2DP connected");
                a2dpConnected = true;
                BluetoothDevice dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (dev != null) {
                    prefs.edit().putString("lastAddr", dev.getAddress()).apply();
                }
                restoreVolume();
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "A2DP disconnected, discoverable for reconnect");
                a2dpConnected = false;
                setScanMode(23);
                handler.postDelayed(new Runnable() {
                    public void run() {
                        setScanMode(21);
                    }
                }, RECONNECT_DISCOVERABLE_MS);
            }
        }
    };

    private final Runnable enforcer = new Runnable() {
        @Override
        public void run() {
            enforceScanMode();
            handler.postDelayed(this, ENFORCE_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        adapter = BluetoothAdapter.getDefaultAdapter();
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        prefs = getSharedPreferences("r1buddy", MODE_PRIVATE);

        Notification n = new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("R1Buddy")
                .setContentText("维持蓝牙可连接")
                .build();
        startForeground(1, n);

        registerReceiver(a2dpReceiver, new IntentFilter(ACTION_A2DP_STATE));
        receiverRegistered = true;
        ensureSink();

        if (prefs.getBoolean("mutePending", false)) {
            restoreVolume();
        }
        if (SystemClock.elapsedRealtime() < BOOT_MUTE_WINDOW_MS) {
            muteForBoot();
            setScanMode(23);
            handler.postDelayed(new Runnable() {
                public void run() {
                    setScanMode(21);
                }
            }, DISCOVERABLE_MS);
            scheduleBootReconnect();
        } else {
            enforceScanMode();
        }
        handler.post(enforcer);
    }

    /**
     * 开机后按固定节奏尝试由音箱侧发起 A2DP 回连。
     * 手机通常会自动接受已配对设备的入向连接，从而省掉「手动点一次」。
     */
    private void scheduleBootReconnect() {
        long[] delays = {12000L, 30000L, 60000L};
        for (long d : delays) {
            handler.postDelayed(new Runnable() {
                public void run() {
                    tryReconnect(false);
                }
            }, d);
        }
    }

    private void ensureSink() {
        if (adapter == null || sink != null || sinkRequested) {
            return;
        }
        sinkRequested = true;
        try {
            adapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    Log.i(TAG, "A2DP sink proxy ready");
                    sink = proxy;
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    sink = null;
                    sinkRequested = false;
                }
            }, PROFILE_A2DP_SINK);
        } catch (Throwable t) {
            sinkRequested = false;
            Log.w(TAG, "getProfileProxy failed: " + t);
        }
    }

    private void tryReconnect(boolean manual) {
        if (a2dpConnected) {
            return;
        }
        if (!manual && SystemClock.elapsedRealtime() > BOOT_RECONNECT_WINDOW_MS) {
            return;
        }
        String addr = prefs.getString("lastAddr", null);
        if (addr == null) {
            Log.i(TAG, "no last device, skip reconnect");
            return;
        }
        if (adapter == null) {
            return;
        }
        if (!adapter.isEnabled()) {
            Log.i(TAG, "bt disabled, skip reconnect");
            return;
        }
        ensureSink();
        if (sink == null) {
            Log.i(TAG, "sink not ready, skip reconnect");
            return;
        }
        setScanMode(21);
        try {
            BluetoothDevice dev = adapter.getRemoteDevice(addr);
            Method m = sink.getClass().getMethod("connect", BluetoothDevice.class);
            Object r = m.invoke(sink, dev);
            Log.i(TAG, "reconnect " + addr + " -> " + r);
        } catch (Throwable t) {
            Log.w(TAG, "reconnect failed: " + t);
        }
    }

    private void muteForBoot() {
        if (audio == null) {
            return;
        }
        int orig = android.provider.Settings.System.getInt(
                getContentResolver(), "volume_music",
                audio.getStreamVolume(AudioManager.STREAM_MUSIC));
        prefs.edit().putInt("origVol", orig).putBoolean("mutePending", true).apply();
        Log.i(TAG, "mute music stream for boot chime");
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
        handler.postDelayed(new Runnable() {
            public void run() {
                restoreVolume();
            }
        }, MUTE_MS);
    }

    private void restoreVolume() {
        if (audio == null || !prefs.getBoolean("mutePending", false)) {
            return;
        }
        int orig = prefs.getInt("origVol", 6);
        Log.i(TAG, "restore music volume to " + orig);
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, orig, 0);
        prefs.edit().putBoolean("mutePending", false).apply();
    }

    private void enforceScanMode() {
        if (adapter == null) {
            return;
        }
        if (!adapter.isEnabled()) {
            try {
                adapter.enable();
            } catch (Throwable t) {
                Log.w(TAG, "enable failed: " + t);
            }
            return;
        }
        int mode = adapter.getScanMode();
        if (mode != 21 && mode != 23) {
            Log.i(TAG, "scan mode was " + mode + ", fix to 21");
            setScanMode(21);
        }
    }

    private void setScanMode(int mode) {
        if (adapter == null) {
            return;
        }
        try {
            Method m = adapter.getClass().getMethod("setScanMode", int.class, int.class);
            m.invoke(adapter, mode, 0);
            Log.i(TAG, "setScanMode " + mode);
        } catch (Throwable t) {
            Log.w(TAG, "setScanMode failed: " + t);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_RECONNECT.equals(intent.getAction())) {
            Log.i(TAG, "manual reconnect requested");
            tryReconnect(true);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(a2dpReceiver);
            receiverRegistered = false;
        }
        if (adapter != null && sink != null) {
            try {
                adapter.closeProfileProxy(PROFILE_A2DP_SINK, sink);
            } catch (Throwable t) {
                Log.w(TAG, "closeProfileProxy failed: " + t);
            }
            sink = null;
        }
        restoreVolume();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
