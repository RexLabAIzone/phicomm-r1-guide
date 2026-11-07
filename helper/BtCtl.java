public class BtCtl {
    public static void main(String[] args) throws Exception {
        String cmd = args.length > 0 ? args[0] : "status";
        Class<?> c = Class.forName("android.bluetooth.BluetoothAdapter");
        Object adapter = c.getMethod("getDefaultAdapter").invoke(null);
        if (adapter != null && cmd.equals("off")) {
            System.out.println("disable=" + c.getMethod("disable").invoke(adapter));
            Thread.sleep(3000);
        } else if (adapter != null && cmd.equals("on")) {
            System.out.println("enable=" + c.getMethod("enable").invoke(adapter));
            Thread.sleep(3000);
        }
        if (adapter != null) {
            System.out.println("enabled=" + c.getMethod("isEnabled").invoke(adapter));
            System.out.println("state=" + c.getMethod("getState").invoke(adapter));
            System.out.println("scan=" + c.getMethod("getScanMode").invoke(adapter));
        }
        try {
            Object binder = Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class).invoke(null, "FXSystemPrivate");
            Class<?> stub = Class.forName("android.os.ISysPrivate$Stub");
            Object iface = stub.getMethod("asInterface", Class.forName("android.os.IBinder"))
                    .invoke(null, binder);
            System.out.println("sysMode=" + iface.getClass().getMethod("getSysMode").invoke(iface));
            if (args.length > 1) {
                int v = Integer.parseInt(args[1]);
                System.out.println("setSysMode(" + v + ")="
                        + iface.getClass().getMethod("setSysMode", int.class).invoke(iface, v));
                Thread.sleep(1000);
                System.out.println("sysModeAfter=" + iface.getClass().getMethod("getSysMode").invoke(iface));
            }
        } catch (Throwable t) {
            System.out.println("sysMode error: " + t);
        }
    }
}
