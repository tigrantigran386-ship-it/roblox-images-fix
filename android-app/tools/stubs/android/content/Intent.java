package android.content;
import android.net.Uri;
public class Intent {
    public static final String ACTION_VIEW = "android.intent.action.VIEW";
    public Intent() {}
    public Intent(String a, Uri u) {}
    public Intent(Context c, Class<?> cls) {}
    public String getAction() { return null; }
    public Intent setAction(String a) { return this; }
}
