package com.r1.buddy;

import android.app.Notification;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
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
    private static final String TAG = "R1Buddy";
    private static final String ACTION_A2DP_STATE =
            "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED";
    private static final long BOOT_MUTE_WINDOW_MS = 120000L;
    private static final long MUTE_MS = 25000L;
    private static final long DISCOVERABLE_MS = 120000L;
    private static final long RECONNECT_DISCOVERABLE_MS = 60000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothAdapter adapter;
    private AudioManager audio;
    private SharedPreferences prefs;
    private boolean receiverRegistered;

    private final BroadcastReceiver a2dpReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
            if (state == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "A2DP connected");
                restoreVolume();
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "A2DP disconnected, discoverable for reconnect");
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
            handler.postDelayed(this, 20000L);
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
        } else {
            enforceScanMode();
        }
        handler.post(enforcer);
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
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(a2dpReceiver);
            receiverRegistered = false;
        }
        restoreVolume();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
