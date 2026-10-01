package android.content.pm;
import java.util.List;
public class PackageManager {
    public static final int PERMISSION_GRANTED = 0;
    public static class NameNotFoundException extends Exception {}
    public PackageInfo getPackageInfo(String p, int f) throws NameNotFoundException { return null; }
    public List<PackageInfo> getInstalledPackages(int f) { return null; }
}
