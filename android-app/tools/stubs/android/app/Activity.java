package android.app;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
public abstract class Activity extends Service {
    public static final int RESULT_OK = -1;
    protected void onCreate(Bundle b) {}
    protected void onResume() {}
    public void setContentView(int l) {}
    public <T extends View> T findViewById(int id) { return null; }
    public void startActivityForResult(Intent i, int c) {}
    protected void onActivityResult(int r, int res, Intent d) {}
    public void runOnUiThread(Runnable r) {}
    public void requestPermissions(String[] p, int c) {}
    public void onRequestPermissionsResult(int c, String[] p, int[] g) {}
}
