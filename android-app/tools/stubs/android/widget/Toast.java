package android.widget;
import android.content.Context;
public class Toast {
    public static final int LENGTH_SHORT = 0, LENGTH_LONG = 1;
    public static Toast makeText(Context c, int r, int d) { return new Toast(); }
    public static Toast makeText(Context c, CharSequence s, int d) { return new Toast(); }
    public void show() {}
}
