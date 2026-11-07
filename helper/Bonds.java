import java.util.Set;
public class Bonds {
    public static void main(String[] args) throws Exception {
        Class<?> c = Class.forName("android.bluetooth.BluetoothAdapter");
        Object a = c.getMethod("getDefaultAdapter").invoke(null);
        Object set = c.getMethod("getBondedDevices").invoke(a);
        if (set == null) { System.out.println("null"); return; }
        for (Object d : (Set<?>) set) {
            Class<?> dc = Class.forName("android.bluetooth.BluetoothDevice");
            System.out.println(dc.getMethod("getAddress").invoke(d) + " | " +
                dc.getMethod("getName").invoke(d) + " | bond=" +
                dc.getMethod("getBondState").invoke(d));
        }
    }
}
