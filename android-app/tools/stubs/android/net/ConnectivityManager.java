package android.net;
public class ConnectivityManager {
    public Network getActiveNetwork() { return null; }
    public LinkProperties getLinkProperties(Network n) { return null; }
    public NetworkCapabilities getNetworkCapabilities(Network n) { return null; }
    public static class NetworkCallback {
        public void onAvailable(Network n) {}
        public void onCapabilitiesChanged(Network n, NetworkCapabilities c) {}
        public void onLost(Network n) {}
    }
    public void registerDefaultNetworkCallback(NetworkCallback cb) {}
    public void unregisterNetworkCallback(NetworkCallback cb) {}
}
