package android.app;
import android.content.Context;
public class Notification {
    public static class Builder {
        public Builder(Context c, String ch) {}
        public Builder setContentTitle(CharSequence t) { return this; }
        public Builder setContentText(CharSequence t) { return this; }
        public Builder setSmallIcon(int i) { return this; }
        public Builder setContentIntent(PendingIntent p) { return this; }
        public Builder setOngoing(boolean b) { return this; }
        public Notification build() { return new Notification(); }
    }
}
