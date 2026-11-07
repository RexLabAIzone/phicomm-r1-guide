public class SysMode {
    public static void main(String[] args) throws Exception {
        Class<?> sm = Class.forName("android.os.ServiceManager");
        Object binder = sm.getMethod("getService", String.class).invoke(null, "FXSystemPrivate");
        System.out.println("binder=" + binder);
        Class<?> stub = Class.forName("android.os.ISysPrivate$Stub");
        Object iface = stub.getMethod("asInterface", Class.forName("android.os.IBinder")).invoke(null, binder);
        System.out.println("iface=" + iface);
        if (args.length == 0) {
            System.out.println("mode=" + iface.getClass().getMethod("getSysMode").invoke(iface));
        } else {
            int v = Integer.parseInt(args[0]);
            Object r = iface.getClass().getMethod("setSysMode", int.class).invoke(iface, v);
            System.out.println("setSysMode(" + v + ")=" + r);
            System.out.println("mode=" + iface.getClass().getMethod("getSysMode").invoke(iface));
        }
    }
}
