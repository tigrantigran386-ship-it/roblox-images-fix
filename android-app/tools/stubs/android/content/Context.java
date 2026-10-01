package android.content;
import android.content.pm.PackageManager;
public abstract class Context {
    public static final String CONNECTIVITY_SERVICE = "connectivity";
    public static final String CLIPBOARD_SERVICE = "clipboard";
    public String getString(int resId) { return null; }
    public <T> T getSystemService(Class<T> c) { return null; }
    public Object getSystemService(String name) { return null; }
    public void startActivity(Intent i) {}
    public void startForegroundService(Intent i) {}
    public void startService(Intent i) {}
    public boolean stopService(Intent i) { return false; }
    public PackageManager getPackageManager() { return null; }
    public int checkSelfPermission(String p) { return 0; }
    public String getPackageName() { return "com.robloxfix.rfimages"; }
    public ContentResolver getContentResolver() { return null; }
}
