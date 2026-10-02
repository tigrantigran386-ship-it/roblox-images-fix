package android.net;
public class NetworkCapabilities {
    public static final int TRANSPORT_WIFI = 1;
    public static final int TRANSPORT_VPN = 4;
    public static final int TRANSPORT_CELLULAR = 0;
    public boolean hasTransport(int t) { return false; }
}
