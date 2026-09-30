package com.robloxfix.rfimages;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_VPN = 1;
    private static final int REQ_NOTIF = 2;

    private TextView statusView;
    private TextView checkView;
    private Button toggleBtn;
    private Button checkBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusView = findViewById(R.id.status);
        checkView = findViewById(R.id.checkResult);
        toggleBtn = findViewById(R.id.toggle);
        checkBtn = findViewById(R.id.check);

        toggleBtn.setOnClickListener(v -> toggle());
        checkBtn.setOnClickListener(v -> runCheck());

        findViewById(R.id.copyReport).setOnClickListener(v -> copyReport());
        findViewById(R.id.repoLink).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/tigrantigran386-ship-it/roblox-images-fix"));
            try { startActivity(i); } catch (Exception ignored) { }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        boolean on = FixVpnService.running;
        statusView.setText(on ? R.string.status_on : R.string.status_off);
        toggleBtn.setText(on ? R.string.turn_off : R.string.turn_on);
        // Строгий Приватный DNS обходит наш перехват — предупреждаем сразу
        if (on && isStrictPrivateDnsOn()) checkView.setText(R.string.pdns_warning);
    }

    /** true, если включён строгий «Приватный DNS» (DoT к конкретному хосту). */
    private boolean isStrictPrivateDnsOn() {
        try {
            String mode = Settings.Global.getString(getContentResolver(), "private_dns_mode");
            return "hostname".equals(mode) || "specify".equals(mode);
        } catch (Exception e) {
            return false;
        }
    }

    private void toggle() {
        if (FixVpnService.running) {
            stopService(new Intent(this, FixVpnService.class));
            refreshStatus();
            checkView.setText(R.string.check_hint);
            return;
        }

        // Разрешение на уведомления (Android 13+) — не блокирует запуск
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }

        Intent consent = VpnService.prepare(this);
        if (consent != null) {
            startActivityForResult(consent, REQ_VPN);
        } else {
            startFix();
        }
    }

    private void startFix() {
        Intent i = new Intent(this, FixVpnService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(i);
        } else {
            startService(i);
        }
        MirrorConfig.refreshAsync();
        statusView.postDelayed(this::refreshStatus, 500);
        Toast.makeText(this, R.string.started_toast, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VPN && resultCode == Activity.RESULT_OK) {
            startFix();
        } else if (requestCode == REQ_VPN) {
            Toast.makeText(this, R.string.vpn_denied, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // результат про уведомления ни на что не влияет
    }

    /** Копирует диагностический отчёт в буфер обмена и показывает на экране. */
    private void copyReport() {
        String report = FixVpnService.STATS.report();
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("rbxfix", report));
            }
        } catch (Exception ignored) { }
        checkView.setText(report);
        Toast.makeText(this, R.string.report_copied, Toast.LENGTH_SHORT).show();
    }

    /** Проверка: живо ли CloudFront-зеркало прямо сейчас (с этого устройства). */
    private void runCheck() {
        checkBtn.setEnabled(false);
        checkView.setText(R.string.check_running);
        new Thread(() -> {
            final String result;
            if (!FixVpnService.running) {
                result = getString(R.string.check_off);
            } else {
                String r;
                try {
                    List<byte[]> ips = DnsKit.resolveA4(
                            MirrorConfig.HOST_TO_MIRROR.get("tr.rbxcdn.com"));
                    r = (ips != null && !ips.isEmpty())
                            ? getString(R.string.check_ok)
                            : getString(R.string.check_fail);
                } catch (Exception e) {
                    r = getString(R.string.check_fail);
                }
                result = r;
            }
            runOnUiThread(() -> {
                checkBtn.setEnabled(true);
                checkView.setText(result);
            });
        }, "rbxfix-check").start();
    }
}
