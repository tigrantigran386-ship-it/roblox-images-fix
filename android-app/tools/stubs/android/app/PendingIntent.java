package android.app;
import android.content.Context;
import android.content.Intent;
public final class PendingIntent {
    public static final int FLAG_IMMUTABLE = 67108864;
    public static final int FLAG_UPDATE_CURRENT = 33554432;
    public static PendingIntent getActivity(Context c, int r, Intent i, int f) { return new PendingIntent(); }
}
