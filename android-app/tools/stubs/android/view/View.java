package android.view;
public class View {
    public interface OnClickListener { void onClick(View v); }
    public void setOnClickListener(OnClickListener l) {}
    public boolean postDelayed(Runnable r, long d) { return true; }
    public void setEnabled(boolean e) {}
    public void setBackgroundResource(int r) {}
}
