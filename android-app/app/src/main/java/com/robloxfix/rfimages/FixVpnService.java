package com.robloxfix.rfimages;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ConnectivityManager;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.LinkProperties;
import android.net.Network;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Сердце приложения: точечный VPN-туннель ДЛЯ ROBLOX (per-app).
 *
 * Ключевые принципы v1.1.0:
 *  1. В туннель попадает ТОЛЬКО приложение Roblox (addAllowedPackage) —
 *     остальные приложения живут своим обычным интернетом.
 *  2. В туннель идёт только маршрут DNS-сервера /32, т.е. исключительно DNS.
 *  3. Обычные запросы пересылаются на ОБЫЧНЫЙ DNS оператора (защищённым
 *     сокетом) — так же быстро, как без VPN. DoH — только запасной путь.
 *  4. Имена *.rbxcdn.com переписываются на CloudFront-зеркало.
 *  5. Если апстрим не ответил — клиенту мгновенно уходит SERVFAIL
 *     (чтобы Roblox не вис в вечном ожидании).
 */
public class FixVpnService extends VpnService implements DnsKit.SocketProtector {

    private static final String TAG = "RbxFix";
    private static final String CHANNEL_ID = "rbxfix_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final String ROBLOX_PKG = "com.roblox.client";

    /** Фиктивный DNS-сервер внутри туннеля. */
    private static final byte[] DNS_SERVER_V4 = {10, 111, (byte) 222, 3};
    private static final String DNS_SERVER_STR = "10.111.222.3";

    /** Запасные DNS, если не удалось узнать DNS оператора (Яндекс — работает в РФ). */
    private static final String[] FALLBACK_DNS = {"77.88.8.8", "8.8.8.8", "1.1.1.1"};

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
        DnsKit.protector = this;          // наши DoH/UDP-сокеты не должны попадать в свой же туннель
        captureUpstreamDns();             // ДО установления туннеля: узнаём DNS оператора
        MirrorConfig.refreshAsync();
    }

    /** Узнаём DNS-серверы текущей сети (то, что телефон использует без VPN). */
    private void captureUpstreamDns() {
        LinkedHashSet<String> servers = new LinkedHashSet<>();
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            Network net = cm.getActiveNetwork();
            LinkProperties lp = cm.getLinkProperties(net);
            if (lp != null) {
                for (InetAddress a : lp.getDnsServers()) {
                    if (a instanceof Inet4Address) servers.add(a.getHostAddress());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "не удалось узнать DNS сети, использую запасные");
        }
        for (String fb : FALLBACK_DNS) servers.add(fb);
        List<byte[]> ips = new ArrayList<>();
        for (String s : servers) {
            try {
                byte[] b = InetAddress.getByName(s).getAddress();
                if (b.length == 4) ips.add(b);
            } catch (Exception ignored) { }
        }
        DnsKit.setUpstreams(ips);
        Log.i(TAG, "апстримы DNS: " + servers);
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
        if (pool == null || pool.isShutdown()) pool = Executors.newFixedThreadPool(8);
        startLoop();
        Log.i(TAG, "Туннель запущен (только для " + ROBLOX_PKG + ", только DNS)");
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
                    .addRoute(DNS_SERVER_STR, 32)   // в туннель — только DNS
                    .setMtu(1500);

            // ПЕРЕ-APP РЕЖИМ: туннель видит только Roblox.
            // Если пакет Roblox не найден — работаем для всех (старое поведение).
            boolean robloxInstalled = false;
            try {
                getPackageManager().getPackageInfo(ROBLOX_PKG, 0);
                robloxInstalled = true;
            } catch (Exception ignored) { }
            if (robloxInstalled) {
                try {
                    b.addAllowedPackage(ROBLOX_PKG);
                    Log.i(TAG, "per-app режим: только " + ROBLOX_PKG);
                } catch (Exception e) {
                    Log.w(TAG, "addAllowedPackage не сработал: " + e);
                }
            }

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

    /** Разбирает пакет из TUN; реагирует только на DNS-запросы. */
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

        // Остальное — на обычный DNS оператора (быстро), DoH в запасе
        final byte[] queryBytes = q.raw;
        pool.execute(() -> {
            byte[] resp = DnsKit.forwardPlain(queryBytes);
            if (resp == null) resp = DnsKit.upstreamQuery(queryBytes); // DoH-запасной путь
            if (resp != null && resp.length >= 12) {
                cache.put(cacheKey, new CacheEntry(resp, now + 30_000));
            } else {
                // мгновенный SERVFAIL, чтобы Roblox не вис в ожидании
                resp = DnsKit.buildServFail(q);
            }
            sendDns(q, resp);
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
    public void protectSocket(java.net.Socket s) {
        try { protect(s); } catch (Exception ignored) { }
    }

    @Override
    public void protectSocket(java.net.DatagramSocket s) {
        try { protect(s); } catch (Exception ignored) { }
    }

    @Override
    public void onRevoke() {
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
