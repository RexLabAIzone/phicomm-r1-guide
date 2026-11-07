/**
 * 以 shell(uid 2000) 身份直接调用 IWifiManager，不依赖 Context。
 * 用途：验证「蓝牙模式(sysMode=1) 下能否把 Wi-Fi 重新打开」。
 *
 * 用法：CLASSPATH=/data/local/tmp/wifictl.dex app_process /system/bin WifiCtl [on|off|status]
 */
public class WifiCtl {
    public static void main(String[] args) throws Exception {
        String cmd = args.length > 0 ? args[0] : "status";
        Object binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "wifi");
        if (binder == null) {
            System.out.println("no wifi service");
            return;
        }
        Object wm = Class.forName("android.net.wifi.IWifiManager$Stub")
                .getMethod("asInterface", Class.forName("android.os.IBinder"))
                .invoke(null, binder);

        if ("on".equals(cmd) || "off".equals(cmd)) {
            boolean on = "on".equals(cmd);
            Object r = wm.getClass().getMethod("setWifiEnabled", boolean.class).invoke(wm, on);
            System.out.println("setWifiEnabled(" + on + ")=" + r);
            Thread.sleep(4000);
        }
        Object st = wm.getClass().getMethod("getWifiEnabledState").invoke(wm);
        System.out.println("wifiEnabledState=" + st + " (0=disabling 1=disabled 2=enabling 3=enabled)");
    }
}
