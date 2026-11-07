public class GetScan {
    public static void main(String[] args) throws Exception {
        Class<?> c = Class.forName("android.bluetooth.BluetoothAdapter");
        Object a = c.getMethod("getDefaultAdapter").invoke(null);
        System.out.println("enabled=" + c.getMethod("isEnabled").invoke(a));
        System.out.println("scanMode=" + c.getMethod("getScanMode").invoke(a));
    }
}
