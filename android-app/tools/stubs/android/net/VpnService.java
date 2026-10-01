package android.net;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
public class VpnService extends Service {
    public static Intent prepare(Context c) { return null; }
    public boolean protect(java.net.Socket s) { return true; }
    public boolean protect(java.net.DatagramSocket s) { return true; }
    public void onRevoke() {}
    public class Builder {
        public Builder setSession(String s) { return this; }
        public Builder addAddress(String a, int m) { return this; }
        public Builder addDnsServer(String d) { return this; }
        public Builder addRoute(String r, int m) { return this; }
        public Builder setMtu(int m) { return this; }
        public Builder addAllowedApplication(String p) throws android.content.pm.PackageManager.NameNotFoundException { return this; }
        public ParcelFileDescriptor establish() { return null; }
    }
}
