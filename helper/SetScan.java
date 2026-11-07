public class SetScan {
    public static void main(String[] args) throws Exception {
        Class<?> c = Class.forName("android.bluetooth.BluetoothAdapter");
        Object adapter = c.getMethod("getDefaultAdapter").invoke(null);
        if (adapter == null) { System.out.println("no adapter"); return; }
        int mode = args.length > 0 ? Integer.parseInt(args[0]) : 23;
        System.out.println("enabled=" + c.getMethod("isEnabled").invoke(adapter));
        System.out.println("before=" + c.getMethod("getScanMode").invoke(adapter));
        try {
            Object r = c.getMethod("setScanMode", int.class, int.class).invoke(adapter, mode, 0);
            System.out.println("setScanMode(" + mode + ")=" + r);
        } catch (Throwable t) {
            System.out.println("setScanMode failed: " + t);
            Throwable cause = t.getCause();
            while (cause != null) { System.out.println("cause: " + cause); cause = cause.getCause(); }
        }
        System.out.println("after=" + c.getMethod("getScanMode").invoke(adapter));
    }
}
