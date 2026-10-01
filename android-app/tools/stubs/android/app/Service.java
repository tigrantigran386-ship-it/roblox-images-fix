package android.app;
import android.content.Context;
import android.content.Intent;
public abstract class Service extends Context {
    public static final int START_STICKY = 1;
    public static final int START_NOT_STICKY = 2;
    public static final int STOP_FOREGROUND_REMOVE = 1;
    public void onCreate() {}
    public int onStartCommand(Intent i, int f, int id) { return 0; }
    public void onDestroy() {}
    public final void startForeground(int id, Notification n) {}
    public final void startForeground(int id, Notification n, int t) {}
    public final void stopForeground(int f) {}
    public final void stopForeground(boolean b) {}
    public void stopSelf() {}
}
