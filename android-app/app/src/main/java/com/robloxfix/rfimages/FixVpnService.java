package com.robloxfix.rfimages;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Сердце приложения: точечный VPN-туннель.
 *
 * В туннель уходит ТОЛЬКО DNS-трафик (маршрут /32 на фиктивный DNS-сервер
 * 10.111.222.3). Весь остальной интернет идёт напрямую, без туннеля —
 * скорость не страдает.
 *
 * Логика: запросы *.rbxcdn.com перехватываются и на них отвечаем IP-адресами
 * CloudFront-зеркала (официального, байт-в-байт того же контента). Остальные
 * запросы пересылаются на апстримы Cloudflare/Google (DoH) и возвращаются
 * клиенту как есть.
 */
public class FixVpnService extends VpnService implements DnsKit.SocketProtector {

    private static final String TAG = "RbxFix";
    private static final String CHANNEL_ID = "rbxfix_channel";
    private static final int NOTIFICATION_ID = 1;

    /** Фиктивный DNS-сервер внутри туннеля (уникальный диапазон для бенчмарков RFC 5737-стиля). */
    private static final byte[] DNS_SERVER_V4 = {10, 111, (byte) 222, 3};
    private static final String DNS_SERVER_STR = "10.111.222.3";

    public static volatile boolean running = false;

    private ParcelFileDescriptor tun;
    private FileOutputStream tunOut;
    private ExecutorService pool;
    private Thread loopThread;

    /** Кэш DNS-ответов: "имя/тип" → {байты ответа, срок жизни}. */
    private static final class CacheEntry {
        final byte[] response;
        final long expiresAt;
        CacheEntry(byte[] r, long e) { response = r; expiresAt = e; }
    }

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        DnsKit.protector = this;   // сокеты DoH не должны попадать в собственный туннель
        MirrorConfig.refreshAsync();
    }

    @Override
    public void protectSocket(java.net.Socket s) {
        try { protect(s); } catch (Exception ignored) { }
    }

    @Override
    public void protectSocket(java.net.DatagramSocket s) {
        try { protect(s); } catch (Exception ignored) { }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground();
        if (!establishTunnel()) {
            Log.e(TAG, "Не удалось поднять туннель");
            stopSelf();
            return START_NOT_STICKY;
        }
        running = true;
        if (pool == null) pool = Executors.newFixedThreadPool(4);
        startLoop();
        Log.i(TAG, "Туннель запущен");
        return START_STICKY;
    }

    private void startAsForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null && Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Roblox Images Fix", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                Build.VERSION.SDK_INT >= 31
                        ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                        : PendingIntent.FLAG_UPDATE_CURRENT);

        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    private boolean establishTunnel() {
        try {
            if (tun != null) { try { tun.close(); } catch (IOException ignored) { } tun = null; }
            Builder b = new Builder()
                    .setSession(getString(R.string.app_name))
                    .addAddress("10.111.222.1", 32)
                    .addDnsServer(DNS_SERVER_STR)
                    .addRoute(DNS_SERVER_STR, 32)   // В ТУННЕЛЬ только DNS — остальное напрямую!
                    .setMtu(1500);
            tun = b.establish();
            if (tun == null) return false;
            tunOut = new FileOutputStream(tun.getFileDescriptor());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "establish()", e);
            return false;
        }
    }

    private void startLoop() {
        if (loopThread != null && loopThread.isAlive()) return;
        final FileInputStream in;
        try {
            in = new FileInputStream(tun.getFileDescriptor());
        } catch (Exception e) {
            Log.e(TAG, "tun input", e);
            return;
        }
        loopThread = new Thread(() -> {
            byte[] buf = new byte[32768];
            try {
                while (running) {
                    int n = in.read(buf);
                    if (n >= 28) handlePacket(buf, n);
                }
            } catch (IOException e) {
                if (running) Log.w(TAG, "tun loop exit: " + e);
            }
        }, "rbxfix-loop");
        loopThread.start();
    }

    /** Разбирает пакет из TUN; реагирует только на DNS-запросы к нашему серверу. */
    private void handlePacket(byte[] pkt, int len) {
        final DnsKit.Query q = DnsKit.parseUdpDns(pkt, len);
        if (q == null) return;

        final String cacheKey = q.qname + "/" + q.qtype;
        CacheEntry hit = cache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (hit != null && hit.expiresAt > now) {
            sendDns(q, hit.response);
            return;
        }

        if (MirrorConfig.shouldRewrite(q.qname, q.qtype)) {
            byte[] resp = MirrorConfig.answer(q);
            if (resp != null) {
                cache.put(cacheKey, new CacheEntry(resp, now + 120_000));
                sendDns(q, resp);
            }
            return;
        }

        // Остальное — в апстрим (DoH Cloudflare → Google → UDP 1.1.1.1)
        final byte[] queryBytes = q.raw;
        pool.execute(() -> {
            byte[] upstreamResp = DnsKit.upstreamQuery(queryBytes);
            if (upstreamResp != null && upstreamResp.length >= 12) {
                cache.put(cacheKey, new CacheEntry(upstreamResp, now + 60_000));
                sendDns(q, upstreamResp);
            }
        });
    }

    /** Отправляет DNS-ответ клиенту обратно через TUN. */
    private void sendDns(DnsKit.Query q, byte[] dnsResp) {
        try {
            byte[] pkt = DnsKit.buildUdp4Packet(
                    DNS_SERVER_V4, 53, q.clientAddr, q.clientPort, dnsResp);
            synchronized (tunOut) {
                tunOut.write(pkt);
                tunOut.flush();
            }
        } catch (IOException e) {
            // туннель закрывается — нормально
        }
    }

    @Override
    public void onRevoke() {
        // Пользователь или система отключили VPN
        stopFix();
    }

    @Override
    public void onDestroy() {
        stopFix();
        super.onDestroy();
    }

    private void stopFix() {
        running = false;
        cache.clear();
        if (pool != null) {
            pool.shutdownNow();
            pool = null;
        }
        try {
            if (tun != null) {
                tun.close();
                tun = null;
                tunOut = null;
            }
        } catch (IOException ignored) { }
        Log.i(TAG, "Туннель остановлен");
    }
}
