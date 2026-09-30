package com.robloxfix.rfimages;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.net.ConnectivityManager;
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
    /** Запасной список, если автопоиск не сработал. */
    private static final String[] ROBLOX_PACKAGES = {
            "com.roblox.client", "com.roblox.client.samsung", "com.roblox.client.huawei"
    };

    /** Автопоиск всех установленных пакетов Roblox (по подстроке в имени). */
    private java.util.List<String> findRobloxPackages() {
        java.util.List<String> out = new ArrayList<>();
        try {
            String self = getPackageName();
            for (android.content.pm.PackageInfo pi
                    : getPackageManager().getInstalledPackages(0)) {
                String n = pi.packageName;
                if (n != null && !n.equals(self)
                        && n.toLowerCase(java.util.Locale.ROOT).contains("roblox")) {
                    out.add(n);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "автопоиск пакетов не удался: " + e);
        }
        return out;
    }

    /** Счётчики диагностики (видны в UI, кнопка «Скопировать отчёт»). */
    public static final class Stats {
        public volatile long startedAt;
        public volatile int packets, dnsQueries, rewritten, forwarded, upstreamFail, servfail, sent, errors;
        public volatile String lastError = "-";
        /** Сколько каких rbxcdn-хостов переписано: {"t5.rbxcdn.com": 42, ...} */
        public final java.util.concurrent.ConcurrentHashMap<String, Integer> rewriteByHost =
                new java.util.concurrent.ConcurrentHashMap<>();
        public volatile int passthrough;
        public final java.util.concurrent.ConcurrentHashMap<String, Integer> passthroughByHost =
                new java.util.concurrent.ConcurrentHashMap<>();
        public volatile String perApp = "?";
        public String report() {
            long up = startedAt == 0 ? 0 : (System.currentTimeMillis() - startedAt) / 1000;
            StringBuilder byHost = new StringBuilder();
            for (Map.Entry<String, Integer> e : rewriteByHost.entrySet()) {
                byHost.append(e.getKey().split("\\.")[0]).append("=").append(e.getValue()).append(" ");
            }
            StringBuilder byPass = new StringBuilder();
            for (Map.Entry<String, Integer> e : passthroughByHost.entrySet()) {
                byPass.append(e.getKey().split("\\.")[0]).append("=").append(e.getValue()).append(" ");
            }
            return "Roblox Images Fix v1.7.0\n"
                    + "работает: " + (running ? "да (" + up + " c)" : "нет") + "\n"
                    + "per-app: " + perApp + "\n"
                    + "зеркало картинок: " + com.robloxfix.rfimages.MirrorConfig.bestMirrorInfo() + "\n"
                    + "пакетов из TUN: " + packets + "\n"
                    + "DNS-запросов: " + dnsQueries + "\n"
                    + "переписано (rbxcdn): " + rewritten
                    + (byHost.length() > 0 ? " [" + byHost.toString().trim() + "]" : "") + "\n"
                    + (byPass.length() > 0
                        ? "мимо карты (апстрим): [" + byPass.toString().trim() + "]\n" : "")
                    + "переслано апстриму: " + forwarded + "\n"
                    + "ошибок апстрима: " + upstreamFail + " (SERVFAIL: " + servfail + ")\n"
                    + "ответов отправлено: " + sent + "\n"
                    + "внутренних ошибок: " + errors + (errors > 0 ? " (последняя: " + lastError + ")" : "") + "\n";
        }
    }

    public static final Stats STATS = new Stats();

    /** Фиктивный DNS-сервер внутри туннеля. */
    private static final byte[] DNS_SERVER_V4 = {10, 111, (byte) 222, 3};
    private static final String DNS_SERVER_STR = "10.111.222.3";

    /** Запасные DNS, если не удалось узнать DNS оператора (Яндекс — работает в РФ). */
    private static final String[] FALLBACK_DNS = {"77.88.8.8", "8.8.8.8", "1.1.1.1"};

    public static volatile boolean running = false;
    /** Флаг «пользователь хочет выключить»: переживает воскрешение сервиса системой. */
    public static volatile boolean userWantsOff = false;

    /** Команда явной остановки (переживает любые воскрешения). */
    public static final String ACTION_STOP = "com.robloxfix.rfimages.STOP";

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
        boolean stopRequested = userWantsOff
                || (intent != null && ACTION_STOP.equals(intent.getAction()));
        if (stopRequested) {
            // система может воскресить сервис — но команда остановки непобедима
            userWantsOff = true;
            stopFix();
            try {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } catch (Exception ignored) { }
            stopSelf();
            return START_NOT_STICKY;
        }
        userWantsOff = false;
        startAsForeground();
        if (!establishTunnel()) {
            Log.e(TAG, "Не удалось поднять туннель");
            stopSelf();
            return START_NOT_STICKY;
        }
        running = true;
        STATS.startedAt = System.currentTimeMillis();
        if (pool == null || pool.isShutdown()) pool = Executors.newFixedThreadPool(8);
        startLoop();
        Log.i(TAG, "Туннель запущен (только Roblox, только DNS)");
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

            // ПЕРЕ-APP РЕЖИМ: сначала автопоиск по имени, потом запасной список.
            java.util.LinkedHashSet<String> pkgs = new java.util.LinkedHashSet<>(findRobloxPackages());
            for (String pkg : ROBLOX_PACKAGES) {
                try {
                    getPackageManager().getPackageInfo(pkg, 0);
                    pkgs.add(pkg);
                } catch (Exception ignored) { }
            }
            int added = 0;
            for (String pkg : pkgs) {
                try {
                    b.addAllowedApplication(pkg);
                    added++;
                } catch (Exception ignored) { }
            }
            STATS.perApp = added > 0
                    ? "вкл (" + pkgs + ")"
                    : "ВЫКЛ — пакет Roblox не найден, туннель для всех";
            Log.i(TAG, "per-app: " + STATS.perApp);

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
            while (running) {
                try {
                    int n = in.read(buf);
                    if (n < 0) break;
                    if (n >= 28) handlePacket(buf, n);
                } catch (Throwable t) {           // НИКАКОЕ исключение не убивает цикл
                    STATS.errors++;
                    String m = String.valueOf(t);
                    STATS.lastError = m.length() > 60 ? m.substring(0, 60) : m;
                    if (running) Log.w(TAG, "packet err: " + t);
                    try { Thread.sleep(20); } catch (InterruptedException ie) { break; }
                }
            }
        }, "rbxfix-loop");
        loopThread.start();
    }

    /** Разбирает пакет из TUN; реагирует только на DNS-запросы. */
    private void handlePacket(byte[] pkt, int len) {
        STATS.packets++;
        final DnsKit.Query q = DnsKit.parseUdpDns(pkt, len);
        if (q == null) return;
        STATS.dnsQueries++;

        final String cacheKey = q.qname + "/" + q.qtype;
        CacheEntry hit = cache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (hit != null && hit.expiresAt > now) {
            sendDns(q, hit.response);
            return;
        }

        if (MirrorConfig.shouldRewrite(q.qname, q.qtype)) {
            // A → IP зеркала; AAAA/HTTPS(65) → пустой ответ (клиент пойдёт по IPv4)
            byte[] resp;
            if (q.qtype == DnsKit.TYPE_A) {
                resp = MirrorConfig.answer(q);
            } else {
                resp = DnsKit.buildEmptyAnswer(q);
            }
            if (resp != null) {
                cache.put(cacheKey, new CacheEntry(resp, now + 300_000));
                STATS.rewritten++;
                Integer c = STATS.rewriteByHost.get(q.qname);
                STATS.rewriteByHost.put(q.qname, c == null ? 1 : c + 1);
                sendDns(q, resp);
            }
            return;
        }

        // rbxcdn без зеркала — ЧЕСТНАЯ пересылка апстриму.
        // (Раньше отвечали пусто — это ломало apis/fts внутри игры!)
        if (q.qname.endsWith(".rbxcdn.com") || q.qname.equals("rbxcdn.com")) {
            STATS.passthrough++;
            Integer pc = STATS.passthroughByHost.get(q.qname);
            STATS.passthroughByHost.put(q.qname, pc == null ? 1 : pc + 1);
        }

        // Остальное — на обычный DNS оператора (быстро), DoH в запасе
        final byte[] queryBytes = q.raw;
        pool.execute(() -> {
            byte[] resp = null;
            try {
                resp = DnsKit.forwardPlain(queryBytes);
                if (resp == null) resp = DnsKit.upstreamQuery(queryBytes); // DoH-запасной путь
            } catch (Throwable t) {
                STATS.errors++;
            }
            if (resp != null && resp.length >= 12) {
                STATS.forwarded++;
                cache.put(cacheKey, new CacheEntry(resp, now + 30_000));
            } else {
                STATS.upstreamFail++; STATS.servfail++;
                STATS.lastError = "апстрим DNS не ответил";
                resp = DnsKit.buildServFail(q);   // мгновенный отказ вместо зависания
            }
            sendDns(q, resp);
        });
    }

    /** Отправляет DNS-ответ клиенту обратно через TUN. */
    private void sendDns(DnsKit.Query q, byte[] dnsResp) {
        FileOutputStream out = tunOut;
        if (out == null || dnsResp == null) return;
        try {
            byte[] pkt = DnsKit.buildUdp4Packet(
                    DNS_SERVER_V4, 53, q.clientAddr, q.clientPort, dnsResp);
            synchronized (out) {
                out.write(pkt);
            }
            STATS.sent++;
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
        boolean wasRunning = running;
        running = false;
        if (wasRunning) {
            try {
                if (Build.VERSION.SDK_INT >= 24) {
                    stopForeground(STOP_FOREGROUND_REMOVE);
                } else {
                    stopForeground(true);
                }
            } catch (Exception ignored) { }
        }
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
