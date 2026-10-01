package com.robloxfix.rfimages;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Конфиг переписывания: какой заблокированный хост rbxcdn.com на какое
 * CloudFront-зеркало смотреть, и какие у зеркал сейчас IP.
 *
 * При старте сервиса IP зеркал обновляются через DoH, поэтому hardcoded-список —
 * лишь страховка на случай, если всё вокруг заблокировано, а обновления нет.
 */
public final class MirrorConfig {

    /** Заблокированный хост → CloudFront-зеркало (тот же контент, те же пути). */
    public static final Map<String, String> HOST_TO_MIRROR = new ConcurrentHashMap<>();

    /**
     * Хосты, которые переписываем (актуально на 30.09.2026; проверялось реальными запросами).
     * tr — иконки/обложки/аватарки; t0-t7 — ассеты и текстуры, которые грузит ИГРА в плейсе.
     * Полное покрытие = быстрее грузится мир.
     */
    static {
        HOST_TO_MIRROR.put("tr.rbxcdn.com", "d77muyc5iodv8.cloudfront.net");
        HOST_TO_MIRROR.put("t0.rbxcdn.com", "djm1c8bbf58td.cloudfront.net");
        HOST_TO_MIRROR.put("t1.rbxcdn.com", "dy9nmzn7lz0hh.cloudfront.net");
        HOST_TO_MIRROR.put("t2.rbxcdn.com", "dfhkkpbuskwyw.cloudfront.net");
        HOST_TO_MIRROR.put("t3.rbxcdn.com", "d2jc9kq4sg41pr.cloudfront.net");
        HOST_TO_MIRROR.put("t4.rbxcdn.com", "dm7zr5njezali.cloudfront.net");
        HOST_TO_MIRROR.put("t5.rbxcdn.com", "d1cn2tk5nesoa7.cloudfront.net");
        HOST_TO_MIRROR.put("t6.rbxcdn.com", "d175vdehdjtrx5.cloudfront.net");
        HOST_TO_MIRROR.put("t7.rbxcdn.com", "d1w9tx3idyd562.cloudfront.net");
        // v1.7.0: контент-серверы плейса + API + настройки клиента
        HOST_TO_MIRROR.put("apis.rbxcdn.com", "d3smszjb1gn4q5.cloudfront.net");
        HOST_TO_MIRROR.put("fts.rbxcdn.com", "d2shmbw56nyjcv.cloudfront.net");
        HOST_TO_MIRROR.put("c0.rbxcdn.com", "d13im6y9zsyqh9.cloudfront.net");
        HOST_TO_MIRROR.put("c1.rbxcdn.com", "d1oarw5tzx06j3.cloudfront.net");
        HOST_TO_MIRROR.put("c2.rbxcdn.com", "dppubz653919u.cloudfront.net");
        HOST_TO_MIRROR.put("c3.rbxcdn.com", "dilj9xb91ln9g.cloudfront.net");
        HOST_TO_MIRROR.put("c4.rbxcdn.com", "d12kacufhf987f.cloudfront.net");
        HOST_TO_MIRROR.put("c5.rbxcdn.com", "d25sshj5zx2ni3.cloudfront.net");
        HOST_TO_MIRROR.put("c6.rbxcdn.com", "d2es4svb0oebfz.cloudfront.net");
        HOST_TO_MIRROR.put("c7.rbxcdn.com", "d1aly16ju3lgz1.cloudfront.net");
        HOST_TO_MIRROR.put("clientsettings.rbxcdn.com", "d2v57ias1m20gl.cloudfront.net");
        HOST_TO_MIRROR.put("static.rbxcdn.com", "d143j4fdqe1jki.cloudfront.net");
        // v1.8.0: sc0-sc7 (по отчёту пользователя — картинки/контент главной)
        HOST_TO_MIRROR.put("sc0.rbxcdn.com", "d2yzw3aiudktwi.cloudfront.net");
        HOST_TO_MIRROR.put("sc1.rbxcdn.com", "d19km468h1klz6.cloudfront.net");
        HOST_TO_MIRROR.put("sc2.rbxcdn.com", "d36u75xya9beit.cloudfront.net");
        HOST_TO_MIRROR.put("sc3.rbxcdn.com", "d1rs2ilrpmqh8j.cloudfront.net");
        HOST_TO_MIRROR.put("sc4.rbxcdn.com", "d1smospaako47m.cloudfront.net");
        HOST_TO_MIRROR.put("sc5.rbxcdn.com", "d1xetq74z9v97x.cloudfront.net");
        HOST_TO_MIRROR.put("sc6.rbxcdn.com", "d1dm3zyxk2nwhm.cloudfront.net");
        HOST_TO_MIRROR.put("sc7.rbxcdn.com", "d3ckmkdqmberzi.cloudfront.net");
    }

    /** Страховочные IP зеркал (если DoH-обновление не удалось). */
    private static final Map<String, String[][]> FALLBACK_IPS = new ConcurrentHashMap<>();
    static {
        FALLBACK_IPS.put("tr.rbxcdn.com", new String[][]{
                {"52", "85", "129", "61"}, {"52", "85", "129", "65"},
                {"52", "85", "129", "58"}, {"52", "85", "129", "52"}});
        FALLBACK_IPS.put("t0.rbxcdn.com", new String[][]{
                {"99", "86", "101", "39"}, {"99", "86", "101", "71"},
                {"99", "86", "101", "41"}, {"99", "86", "101", "99"}});
        FALLBACK_IPS.put("t1.rbxcdn.com", new String[][]{
                {"18", "172", "170", "102"}, {"18", "172", "170", "40"},
                {"18", "172", "170", "52"}, {"18", "172", "170", "19"}});
        FALLBACK_IPS.put("t5.rbxcdn.com", new String[][]{
                {"18", "238", "238", "32"}, {"18", "238", "238", "71"},
                {"18", "238", "238", "129"}});
        FALLBACK_IPS.put("t2.rbxcdn.com", new String[][]{
                {"99", "86", "101", "97"}, {"99", "86", "101", "66"},
                {"99", "86", "101", "88"}, {"99", "86", "101", "83"}});
        FALLBACK_IPS.put("t3.rbxcdn.com", new String[][]{
                {"143", "204", "160", "15"}, {"143", "204", "160", "12"},
                {"143", "204", "160", "4"}, {"143", "204", "160", "93"}});
        FALLBACK_IPS.put("t4.rbxcdn.com", new String[][]{
                {"52", "85", "129", "112"}, {"52", "85", "129", "125"},
                {"52", "85", "129", "115"}, {"52", "85", "129", "90"}});
        FALLBACK_IPS.put("t6.rbxcdn.com", new String[][]{
                {"52", "85", "129", "5"}, {"52", "85", "129", "15"},
                {"52", "85", "129", "10"}, {"52", "85", "129", "108"}});
        FALLBACK_IPS.put("t7.rbxcdn.com", new String[][]{
                {"99", "86", "101", "128"}, {"99", "86", "101", "21"},
                {"99", "86", "101", "112"}, {"99", "86", "101", "20"}});
        FALLBACK_IPS.put("apis.rbxcdn.com", new String[][]{
                {"108", "138", "94", "57"}, {"108", "138", "94", "13"}});
        FALLBACK_IPS.put("fts.rbxcdn.com", new String[][]{
                {"18", "238", "238", "72"}, {"18", "238", "238", "61"}});
        FALLBACK_IPS.put("c0.rbxcdn.com", new String[][]{
                {"52", "85", "129", "81"}, {"52", "85", "129", "33"}});
        FALLBACK_IPS.put("c1.rbxcdn.com", new String[][]{
                {"18", "238", "238", "125"}, {"18", "238", "238", "58"}});
        FALLBACK_IPS.put("c2.rbxcdn.com", new String[][]{
                {"18", "65", "238", "94"}, {"18", "65", "238", "92"}});
        FALLBACK_IPS.put("c3.rbxcdn.com", new String[][]{
                {"52", "85", "129", "4"}, {"52", "85", "129", "32"}});
        FALLBACK_IPS.put("c4.rbxcdn.com", new String[][]{
                {"99", "86", "101", "17"}, {"99", "86", "101", "93"}});
        FALLBACK_IPS.put("c5.rbxcdn.com", new String[][]{
                {"52", "85", "129", "2"}, {"52", "85", "129", "40"}});
        FALLBACK_IPS.put("c6.rbxcdn.com", new String[][]{
                {"143", "204", "160", "36"}, {"143", "204", "160", "28"}});
        FALLBACK_IPS.put("c7.rbxcdn.com", new String[][]{
                {"143", "204", "160", "90"}, {"143", "204", "160", "44"}});
        FALLBACK_IPS.put("clientsettings.rbxcdn.com", new String[][]{
                {"143", "204", "160", "74"}, {"143", "204", "160", "39"}});
        FALLBACK_IPS.put("static.rbxcdn.com", new String[][]{
                {"99", "86", "101", "7"}, {"99", "86", "101", "89"}});
        FALLBACK_IPS.put("sc0.rbxcdn.com", new String[][]{
                {"18", "65", "238", "95"}, {"18", "65", "238", "19"}});
        FALLBACK_IPS.put("sc1.rbxcdn.com", new String[][]{
                {"3", "165", "160", "54"}, {"3", "165", "160", "126"}});
        FALLBACK_IPS.put("sc2.rbxcdn.com", new String[][]{
                {"3", "165", "160", "82"}, {"3", "165", "160", "42"}});
        FALLBACK_IPS.put("sc3.rbxcdn.com", new String[][]{
                {"18", "65", "238", "16"}, {"18", "65", "238", "103"}});
        FALLBACK_IPS.put("sc4.rbxcdn.com", new String[][]{
                {"52", "85", "129", "18"}, {"52", "85", "129", "84"}});
        FALLBACK_IPS.put("sc5.rbxcdn.com", new String[][]{
                {"18", "172", "170", "88"}, {"18", "172", "170", "110"}});
        FALLBACK_IPS.put("sc6.rbxcdn.com", new String[][]{
                {"52", "85", "129", "46"}, {"52", "85", "129", "88"}});
        FALLBACK_IPS.put("sc7.rbxcdn.com", new String[][]{
                {"52", "85", "129", "103"}, {"52", "85", "129", "59"}});
    }

    /** Живые IP зеркал: rbxcdn-хост → список IPv4 (байты). */
    private static final Map<String, List<byte[]>> MIRROR_IPS = new ConcurrentHashMap<>();

    /** Динамически найденные (автопоиском) зеркала: хост → cloudfront-имя. */
    private static final Map<String, String> DYNAMIC = new ConcurrentHashMap<>();

    /** Akamai-кандидаты для tr (строки "a.b.c.d") — вторая нога CDN Roblox. */
    private static final java.util.Set<String> AKAMAI_TR =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Akamai-IP, зашитые заранее: резолв trak через DNS оператора может быть отравлен. */
    private static final String[] AKAMAI_TR_STATIC = { "23.219.78.197", "23.219.78.207" };

    /** Зашитый запасной путь тестовой картинки (если thumbnails API недоступен). */
    private static final String FALLBACK_IMAGE_PATH =
            "/180DAY-f6cc8a94434eb8708aaab2f7732bac01/420/420/Image/Png/noFilter";

    /** Базовая (здоровая) проба чемпиона: EWMA по здоровым замерам. */
    private static volatile int BASELINE = -1;

    /** Журнал событий: переживает тумблер, хвост показываем в отчёте. */
    private static volatile java.io.File JOURNAL_FILE;
    private static volatile long SESSION_START;

    /** «Боковые двери» (sc0ak, sc0gcp, ...): хост → истинные IP через DoH (не отравить). */
    private static final Map<String, List<byte[]>> DIRECT_IPS = new ConcurrentHashMap<>();
    private static final java.util.Set<String> DIRECT_TRIED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Последний контрольный замер обычной сети ВНЕ CDN (gstatic), мс. */
    public static volatile int neutralProbe = -1;
    private static volatile String neutralIp;
    private static volatile long neutralIpAt;

    /** Крошечный запрос к нейтральному хосту вне CDN: та же минута, та же сеть. */
    private static int probeNeutral() {
        javax.net.ssl.SSLSocket s = null;
        try {
            String ip = neutralIp;
            long now = System.currentTimeMillis();
            if (ip == null || now - neutralIpAt > 600_000) {
                List<byte[]> l = DnsKit.resolveA4Merged("www.gstatic.com");
                if (l == null || l.isEmpty()) l = DnsKit.resolveA4("www.gstatic.com");
                if (l == null || l.isEmpty()) { neutralProbe = -1; return -1; }
                ip = dotted(l.get(0));
                neutralIp = ip;
                neutralIpAt = now;
            }
            s = (javax.net.ssl.SSLSocket) javax.net.ssl.SSLSocketFactory.getDefault().createSocket();
            javax.net.ssl.SSLParameters sp = s.getSSLParameters();
            sp.setServerNames(java.util.Collections.singletonList(
                    new javax.net.ssl.SNIHostName("www.gstatic.com")));
            s.setSSLParameters(sp);
            if (DnsKit.protector != null) DnsKit.protector.protectSocket(s);
            long t0 = System.currentTimeMillis();
            s.connect(new java.net.InetSocketAddress(java.net.InetAddress.getByAddress(parseDotted(ip)), 443), 3000);
            s.startHandshake();
            java.io.OutputStream os = s.getOutputStream();
            os.write(("GET /generate_204 HTTP/1.1\r\nHost: www.gstatic.com\r\n"
                    + "User-Agent: rbxfix-probe\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));
            os.flush();
            int first = s.getInputStream().read();
            int ms = (int) (System.currentTimeMillis() - t0);
            neutralProbe = first < 0 ? Integer.MAX_VALUE : ms;
            return neutralProbe;
        } catch (Throwable t) {
            neutralProbe = Integer.MAX_VALUE;
            return neutralProbe;
        } finally {
            try { if (s != null) s.close(); } catch (Exception ignored) { }
        }
    }

    /** Строка для отчёта по контролю сети. */
    public static String neutralInfo() {
        int v = neutralProbe;
        if (v < 0) return "нет";
        if (v == Integer.MAX_VALUE) return "нет ответа";
        return v + " мс";
    }

    /** Порог «канал деградировал»: 700 мс абсолютно, или в 2.2 раза хуже здоровой базы. */
    private static int degradedThreshold() {
        int t = 700;
        if (BASELINE > 0) t = Math.max(t, BASELINE * 220 / 100);
        return Math.min(t, 2500);
    }
    private static final java.util.Set<String> DISCOVERY_TRIED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final AtomicLong LAST_REFRESH = new AtomicLong(0);
    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000; // 5 минут — быстрее заменяем умершие IP

    private MirrorConfig() {}

    private static void putFallback(String host) {
        String[][] raw = FALLBACK_IPS.get(host);
        if (raw == null) return;
        byte[][] ips = new byte[raw.length][4];
        for (int i = 0; i < raw.length; i++)
            for (int k = 0; k < 4; k++)
                ips[i][k] = (byte) Integer.parseInt(raw[i][k]);
        MIRROR_IPS.put(host, Arrays.asList(ips));
    }

    static {
        for (String h : HOST_TO_MIRROR.keySet()) putFallback(h);
    }

    private static volatile Thread watchdog;
    private static final java.util.concurrent.atomic.AtomicInteger BAD_STREAK =
            new java.util.concurrent.atomic.AtomicInteger();

    /** Часовой: раз в минуту проверяет чемпиона; 2 плохих замера подряд — внеплановый рефреш. */
    private static void startWatchdog() {
        if (watchdog != null && watchdog.isAlive()) return;
        watchdog = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(30_000);   // реакция за 0.5-1 мин вместо 1-2
                    List<byte[]> ips = MIRROR_IPS.get("tr.rbxcdn.com");
                    if (ips == null || ips.isEmpty()) continue;

                    // 1) проба РЕАЛЬНОЙ загрузки у чемпиона + контроль обычной сети
                    int champProbe = probeThroughput(ips.get(0));
                    probeChamp = champProbe;
                    probeNeutral();
                    boolean dead = champProbe == Integer.MAX_VALUE;
                    String ctl = ", контроль сети " + neutralInfo();

                    if (!dead && champProbe < degradedThreshold()) {
                        BASELINE = BASELINE < 0 ? champProbe : (BASELINE * 7 + champProbe) / 8;
                        if (BAD_STREAK.get() >= 2) jrnl("канал tr восстановился: " + champProbe + " мс" + ctl);
                        BAD_STREAK.set(0);
                        LAST_RTT_MS = champProbe;
                        continue;                  // канал здоров
                    }
                    jrnl("проба tr: " + (dead ? "нет ответа" : champProbe + " мс")
                            + " — деградация (порог " + degradedThreshold() + " мс" + ctl + ")");

                    // 2) Урок vc10: мёртвый канал чиним СРАЗУ (мёртв — однозначен),
                    //    медленную деградацию подтверждаем второй пробой (30 с)
                    int streak = BAD_STREAK.incrementAndGet();
                    if (!dead && streak < 2) continue;

                    // 3) канал плохой 2 минуты подряд — меряем кандидатов ПАРАЛЛЕЛЬНО:
                    //    сначала Akamai (зашитые), потом CF — чтобы CF-пул не выдавил Akamai
                    List<byte[]> fresh = DnsKit.resolveA4Merged("tr.rbxcdn.com");
                    List<byte[]> cands = new ArrayList<>();
                    for (String a : AKAMAI_TR_STATIC) {
                        byte[] ab = parseDotted(a);
                        if (ab != null && !containsIp(cands, ab) && cands.size() < 5) cands.add(ab);
                    }
                    if (fresh != null) for (byte[] ip : fresh) if (!containsIp(cands, ip) && cands.size() < 6) cands.add(ip);
                    for (byte[] ip : ips) if (!containsIp(cands, ip) && cands.size() < 6) cands.add(ip);
                    List<byte[]> trak = DnsKit.resolveA4Merged("trak.rbxcdn.com");
                    if (trak != null) for (byte[] ip : trak) {
                        AKAMAI_TR.add(dotted(ip));
                        if (!containsIp(cands, ip) && cands.size() < 8) cands.add(ip);
                    }

                    final java.util.Map<String, Integer> wsc = new java.util.concurrent.ConcurrentHashMap<>();
                    List<Thread> wj = new ArrayList<>();
                    for (byte[] ip : cands) {
                        if (Arrays.equals(ip, ips.get(0))) continue;
                        Thread t2 = new Thread(() -> wsc.put(dotted(ip), probeThroughput(ip)), "rbxfix-wprobe");
                        wj.add(t2); t2.start();
                    }
                    for (Thread t2 : wj) { try { t2.join(9000); } catch (InterruptedException ignored) { } }

                    int bestProbe = Integer.MAX_VALUE;
                    byte[] bestIp = null;
                    for (Map.Entry<String, Integer> en : wsc.entrySet()) {
                        if (en.getValue() < bestProbe) { bestProbe = en.getValue(); bestIp = parseDotted(en.getKey()); }
                    }
                    // при почти равной силе — Akamai: уход из душимого CF-диапазона
                    if (bestIp != null && !AKAMAI_TR.contains(dotted(bestIp))) {
                        for (Map.Entry<String, Integer> en : wsc.entrySet()) {
                            if (AKAMAI_TR.contains(en.getKey()) && en.getValue() < Integer.MAX_VALUE
                                    && en.getValue() <= bestProbe * 110 / 100) {
                                bestProbe = en.getValue(); bestIp = parseDotted(en.getKey());
                                break;
                            }
                        }
                    }

                    boolean swap = false;
                    if (dead && bestProbe < Integer.MAX_VALUE) {
                        swap = true;                                 // чемпион мёртв → любой живой
                    } else if (!dead && bestIp != null && bestProbe * 10 < champProbe * 6) {
                        swap = true;                                 // кандидат быстрее в 1.6+ раза
                    }

                    if (swap && bestIp != null) {
                        List<byte[]> next = new ArrayList<>();
                        next.add(bestIp);
                        for (byte[] ip : ips) if (!containsIp(next, ip)) next.add(ip);
                        MIRROR_IPS.put("tr.rbxcdn.com", next);
                        champChanges++;
                        champType = AKAMAI_TR.contains(dotted(bestIp)) ? "Akamai" : "CF";
                        probeChamp = bestProbe;
                        BAD_STREAK.set(0);
                        jrnl("смена tr (watchdog): " + dotted(ips.get(0)) + " → " + dotted(bestIp)
                                + " (" + champProbe + " → " + bestProbe + " мс)");
                    } else if (streak >= 4) {
                        BAD_STREAK.set(0);
                        LAST_REFRESH.set(0);
                        refreshAsync(true);        // все эджи плохи — обновим пул целиком
                        jrnl("все эджи плохи — форс-обновление пула (контроль сети " + neutralInfo() + ")");
                    }
                } catch (InterruptedException e) {
                    return;
                } catch (Throwable ignored) { }
            }
        }, "rbxfix-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    /** Асинхронно обновляет IP всех зеркал через DoH (троттлинг, watchdog умеет форсировать). */
    public static void refreshAsync() { refreshAsync(false); }

    /**
     * Урок vc10: процесс приложения ПЕРЕЖИВАЕТ тумблер (статики живы), и 5-минутный
     * предохранитель проглатывал обновление при повторном включении → Roblox получал
     * мёртвого чемпиона из прошлого сеанса («вообще не грузило»). Явное включение
     * пользователем = всегда свежая проба.
     */
    public static void refreshAsync(boolean force) {
        long now = System.currentTimeMillis();
        long prev = LAST_REFRESH.get();
        if (!force && now - prev < REFRESH_INTERVAL_MS) return;
        if (!LAST_REFRESH.compareAndSet(prev, now)) return;
        startWatchdog();

        Thread t = new Thread(() -> {
            // все хосты — параллельно (их теперь 20)
            Map<String, List<byte[]>> results = new ConcurrentHashMap<>();
            List<Thread> jobs = new ArrayList<>();
            for (Map.Entry<String, String> e : HOST_TO_MIRROR.entrySet()) {
                final boolean isTr = "tr.rbxcdn.com".equals(e.getKey());
                Thread j = new Thread(() -> {
                    List<byte[]> ips = DnsKit.resolveA4Merged(e.getValue());
                    if (ips == null || ips.isEmpty()) ips = DnsKit.resolveA4(e.getValue());
                    if (ips == null || ips.isEmpty()) return;

                    if (isTr) {
                        // Урок vc9: пул CF (до 10 IP) заполнял лимит, а на пробу брали
                        // первые 6 → Akamai МАТЕМАТИЧЕСКИ не участвовал в гонке,
                        // «канал: CF» в каждом отчёте. Теперь CF ≤4 слота, Akamai — всегда.
                        List<byte[]> all = new ArrayList<>(ips.size() > 4 ? ips.subList(0, 4) : ips);
                        for (String akHost : new String[]{"trak.rbxcdn.com", "tr.rbxcdn.com.edgesuite.net"}) {
                            List<byte[]> aka = DnsKit.resolveA4Merged(akHost);
                            if (aka == null) continue;
                            for (byte[] ip : aka) {
                                AKAMAI_TR.add(dotted(ip));
                                boolean dup = false;
                                for (byte[] have : all) if (Arrays.equals(have, ip)) { dup = true; break; }
                                if (!dup && all.size() < 8) all.add(ip);
                            }
                        }
                        for (String a : AKAMAI_TR_STATIC) {   // зашитые: резолв trak может быть отравлен
                            byte[] ab = parseDotted(a);
                            if (ab == null) continue;
                            AKAMAI_TR.add(a);
                            boolean dup = false;
                            for (byte[] have : all) if (Arrays.equals(have, ab)) { dup = true; break; }
                            if (!dup && all.size() < 8) all.add(ab);
                        }
                        // ранжируем ПРОБОЙ РЕАЛЬНОЙ ЗАГРУЗКИ (все кандидаты, ≤8)
                        List<byte[]> probeList = all;
                        final Map<String, Integer> scores = new ConcurrentHashMap<>();
                        List<Thread> pj = new ArrayList<>();
                        for (byte[] ip : probeList) {
                            Thread t2 = new Thread(() -> {
                                String d = (ip[0] & 0xFF) + "." + (ip[1] & 0xFF) + "." + (ip[2] & 0xFF) + "." + (ip[3] & 0xFF);
                                scores.put(d, probeThroughput(ip));
                            }, "rbxfix-probe");
                            pj.add(t2); t2.start();
                        }
                        for (Thread t2 : pj) { try { t2.join(7000); } catch (InterruptedException ignored) { } }
                        probeList.sort((a, b) -> {
                            String da = (a[0]&0xFF) + "." + (a[1]&0xFF) + "." + (a[2]&0xFF) + "." + (a[3]&0xFF);
                            String db = (b[0]&0xFF) + "." + (b[1]&0xFF) + "." + (b[2]&0xFF) + "." + (b[3]&0xFF);
                            return Integer.compare(scores.getOrDefault(da, Integer.MAX_VALUE),
                                                   scores.getOrDefault(db, Integer.MAX_VALUE));
                        });
                        results.put(e.getKey(), pickTrStable(probeList, scores));
                    } else {
                        results.put(e.getKey(), stableMerge(e.getKey(), sortByRtt(ips)));
                    }
                }, "rbxfix-refresh-" + e.getKey());
                jobs.add(j);
                j.start();
            }
            for (final String h : DIRECT_IPS.keySet()) {   // боковые двери: свежие истинные IP
                Thread j = new Thread(() -> {
                    List<byte[]> ips2 = DnsKit.resolveA4Merged(h);
                    if (ips2 != null && !ips2.isEmpty()) {
                        List<byte[]> cap = ips2.size() > 6 ? new ArrayList<>(ips2.subList(0, 6)) : new ArrayList<>(ips2);
                        DIRECT_IPS.put(h, cap);
                    }
                }, "rbxfix-refresh-direct-" + h);
                jobs.add(j);
                j.start();
            }
            for (Thread j : jobs) {
                try { j.join(8000); } catch (InterruptedException ignored) { }
            }
            MIRROR_IPS.putAll(results);
        }, "rbxfix-mirror-refresh");
        t.setDaemon(true);
        t.start();
    }

    /** Последняя измеренная задержка до лучшего IP зеркала (мс). */
    private static volatile int LAST_RTT_MS = Integer.MAX_VALUE;

    /** Сколько раз менялся чемпион ГЛАВНОГО хоста tr (для отчёта). */
    public static volatile int champChanges;

    /** Последний результат пробы доставки чемпиона, мс (реальная загрузка). */
    public static volatile int probeChamp = -1;

    /** Тип канала чемпиона: "CF" (CloudFront) или "Akamai". */
    public static volatile String champType = "CF";

    /** Кэш пути тестовой картинки (из thumbnails API, 10 минут). */
    private static volatile String cachedImagePath;
    private static volatile long imagePathAt;

    private static String getImagePath() {
        long now = System.currentTimeMillis();
        if (cachedImagePath != null && now - imagePathAt < 600_000) return cachedImagePath;
        try {
            javax.net.ssl.HttpsURLConnection c = (javax.net.ssl.HttpsURLConnection)
                    new java.net.URL("https://thumbnails.roblox.com/v1/places/gameicons?placeIds=920587237&size=420x420&format=Png")
                            .openConnection();
            c.setSSLSocketFactory(ProtectedSSLSocketFactory.get());
            c.setConnectTimeout(4000);
            c.setReadTimeout(4000);
            java.io.InputStream in = c.getInputStream();
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
            in.close();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "\"imageUrl\":\"([^\"]+)\"").matcher(o.toString("UTF-8"));
            if (m.find()) {
                String u = m.group(1);
                int slash = u.indexOf(".com");
                if (slash > 0) {
                    cachedImagePath = u.substring(slash + 4);
                    imagePathAt = now;
                }
            }
        } catch (Throwable ignored) { }
        return cachedImagePath != null ? cachedImagePath : FALLBACK_IMAGE_PATH;
    }

    /**
     * ПРОБА РЕАЛЬНОЙ ЗАГРУЗКИ: TCP + TLS (SNI tr.rbxcdn.com) + GET настоящей
     * картинки + чтение до ~300 КБ (или EOF). Показывает то, что чувствует
     * Roblox: даже если рукопожатия быстрые, а передача душится — это видно.
     */
    private static int probeThroughput(byte[] ip) {
        javax.net.ssl.SSLSocket s = null;
        try {
            s = (javax.net.ssl.SSLSocket) javax.net.ssl.SSLSocketFactory.getDefault().createSocket();
            javax.net.ssl.SSLParameters sp = s.getSSLParameters();
            sp.setServerNames(java.util.Collections.singletonList(
                    new javax.net.ssl.SNIHostName("tr.rbxcdn.com")));
            s.setSSLParameters(sp);
            if (DnsKit.protector != null) DnsKit.protector.protectSocket(s);
            String imgPath = getImagePath();   // вне секундомера: API/кэш не портят замер
            long t0 = System.currentTimeMillis();
            s.connect(new java.net.InetSocketAddress(java.net.InetAddress.getByAddress(ip), 443), 3000);
            s.startHandshake();
            java.io.OutputStream os = s.getOutputStream();
            os.write(("GET " + imgPath + " HTTP/1.1\r\nHost: tr.rbxcdn.com\r\n"
                    + "User-Agent: rbxfix-probe\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));
            os.flush();
            java.io.InputStream is = s.getInputStream();
            if (is.read() < 0) return Integer.MAX_VALUE;
            long deadline = t0 + 4000;
            byte[] buf = new byte[16384];
            int total = 1;
            while (total < 300_000 && System.currentTimeMillis() < deadline) {
                int n = is.read(buf);
                if (n < 0) break;
                total += n;
            }
            return (int) (System.currentTimeMillis() - t0);
        } catch (Throwable t) {
            return Integer.MAX_VALUE;
        } finally {
            try { if (s != null) s.close(); } catch (Exception ignored) { }
        }
    }

    /**
     * ПРОБА ДОСТАВКИ: TCP+TLS (SNI=tr.rbxcdn.com) + HTTP GET / до первого байта.
     * Измеряет РЕАЛЬНУЮ скорость канала до эджа — то, чего не видит TCP-пинг
     * (оператор может пропускать рукопожатия, но душить передачу).
     */
    private static int probeTtfb(byte[] ip) {
        javax.net.ssl.SSLSocket s = null;
        try {
            s = (javax.net.ssl.SSLSocket) javax.net.ssl.SSLSocketFactory.getDefault().createSocket();
            javax.net.ssl.SSLParameters sp = s.getSSLParameters();
            sp.setServerNames(java.util.Collections.singletonList(
                    new javax.net.ssl.SNIHostName("tr.rbxcdn.com")));
            s.setSSLParameters(sp);
            if (DnsKit.protector != null) DnsKit.protector.protectSocket(s);
            long t0 = System.currentTimeMillis();
            s.connect(java.net.InetAddress.getByAddress(ip) != null
                    ? new java.net.InetSocketAddress(java.net.InetAddress.getByAddress(ip), 443)
                    : null, 2000);
            s.startHandshake();
            java.io.OutputStream os = s.getOutputStream();
            os.write("GET / HTTP/1.1\r\nHost: tr.rbxcdn.com\r\nUser-Agent: rbxfix-probe\r\nConnection: close\r\n\r\n"
                    .getBytes("US-ASCII"));
            os.flush();
            java.io.InputStream is = s.getInputStream();
            if (is.read() < 0) return Integer.MAX_VALUE;   // нет ответа
            return (int) (System.currentTimeMillis() - t0);
        } catch (Throwable t) {
            return Integer.MAX_VALUE;
        } finally {
            try { if (s != null) s.close(); } catch (Exception ignored) { }
        }
    }

    /** Лучший из N TCP-замеров (одиночный пинг шумит). */
    private static int bestRtt(byte[] ip, int samples) {
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < samples; i++) {
            int r = rttMs(ip);
            if (r < best) best = r;
        }
        return best;
    }

    private static boolean containsIp(List<byte[]> list, byte[] ip) {
        for (byte[] x : list) if (Arrays.equals(x, ip)) return true;
        return false;
    }

    /**
     * СТАБИЛЬНОЕ слияние: текущий «чемпион» остаётся первым, пока жив
     * (переезд только если он мёртв или новый быстрее на 40+ мс).
     * Свежие IP добавляются как ЗАПАСНЫЕ, старые живые — в конец списка.
     * Это убирает ротацию IP, из-за которой Roblox периодически долбился
     * в «умирающий» адрес.
     */
    private static List<byte[]> stableMerge(String host, List<byte[]> fresh) {
        List<byte[]> old = MIRROR_IPS.get(host);
        if (old == null || old.isEmpty() || fresh == null || fresh.isEmpty()) return fresh;

        List<byte[]> result = new ArrayList<>();
        byte[] champ = old.get(0);

        boolean champInFresh = false;
        for (byte[] ip : fresh) if (Arrays.equals(ip, champ)) { champInFresh = true; break; }
        int champRtt = champInFresh ? bestRtt(champ, 2) : rttMs(champ);
        boolean champAlive = champRtt < Integer.MAX_VALUE;
        int bestFreshRtt = bestRtt(fresh.get(0), 2);

        boolean move;
        if (!champAlive) {
            move = true;                          // чемпион мёртв
        } else if (champRtt > 200 && bestFreshRtt + 50 < champRtt) {
            move = true;                          // чемпион ПЛОХОЙ, а кандидат ощутимо лучше
        } else if (champRtt > bestFreshRtt + 120) {
            move = true;                          // разрыв огромный
        } else {
            move = false;                         // мелкие колебания — стабильность важнее
        }

        if (move) {
            if (champAlive) result.add(fresh.get(0));   // новый чемпион, старый — запасным
            else result.add(fresh.get(0));
            if (host.equals("tr.rbxcdn.com")) champChanges++;
        }
        if (champAlive) {
            result.add(champ);
            LAST_RTT_MS = champRtt;
        }

        for (byte[] ip : fresh) {
            if (!containsIp(result, ip) && result.size() < 5) result.add(ip);
        }
        for (byte[] ip : old) {
            if (!containsIp(result, ip) && result.size() < 6) result.add(ip);
        }
        return result;
    }

    /** TCP-подключение на 443 — быстрая оценка задержки (для сортировки). */
    private static int rttMs(byte[] ip) {
        java.net.Socket s = new java.net.Socket();
        try {
            if (DnsKit.protector != null) DnsKit.protector.protectSocket(s);
            long t0 = System.currentTimeMillis();
            s.connect(new java.net.InetSocketAddress(
                    java.net.InetAddress.getByAddress(ip), 443), 1500);
            return (int) (System.currentTimeMillis() - t0);
        } catch (Exception e) {
            return Integer.MAX_VALUE;   // мёртвый IP — в конец списка
        } finally {
            try { s.close(); } catch (Exception ignored) { }
        }
    }

    /** Сортирует IP по возрастанию задержки — самые быстрые первыми в DNS-ответе. */
    private static List<byte[]> sortByRtt(List<byte[]> ips) {
        try {
            List<byte[]> copy = new java.util.ArrayList<>(ips);
            java.util.Map<byte[], Integer> rtt = new java.util.HashMap<>();
            for (byte[] ip : copy) rtt.put(ip, rttMs(ip));
            copy.sort((a, b) -> Integer.compare(rtt.get(a), rtt.get(b)));
            LAST_RTT_MS = rtt.get(copy.get(0));
            return copy;
        } catch (Exception e) {
            return ips;   // без сортировки, если что-то пошло не так
        }
    }

    /**
     * Выбор чемпиона tr. ГЛАВНОЕ ПРАВИЛО (урок «цикличных подвисаний»):
     * ЗДОРОВОГО ЧЕМПИОНА НЕ ТРОГАЕМ. Один замер шумит на ±30-50%, поэтому
     * «лучше на 25%» = лотерея, ломающая работающий канал каждые 5-15 минут.
     * Переезд только если чемпион РЕАЛЬНО плох: >=2500 мс или не ответил.
     */
    private static List<byte[]> pickTrStable(List<byte[]> ranked, Map<String, Integer> scores) {
        if (ranked == null || ranked.isEmpty()) return ranked;
        List<byte[]> old = MIRROR_IPS.get("tr.rbxcdn.com");
        String bestD = dotted(ranked.get(0));
        int bestScore = scores.getOrDefault(bestD, Integer.MAX_VALUE);

        if (old != null && !old.isEmpty()) {
            String champD = dotted(old.get(0));
            Integer champScore = scores.get(champD);
            if (champScore == null && containsIp(ranked, old.get(0))) champScore = probeThroughput(old.get(0));
            if (champScore != null && champScore < degradedThreshold()) {
                // чемпион здоров — оставляем ЛЮБОЙ ценой (стабильность решает всё)
                List<byte[]> out = new ArrayList<>();
                out.add(old.get(0));
                for (byte[] ip : ranked) if (!containsIp(out, ip) && out.size() < 6) out.add(ip);
                for (byte[] ip : old) if (!containsIp(out, ip) && out.size() < 6) out.add(ip);
                probeChamp = champScore;
                champType = AKAMAI_TR.contains(champD) ? "Akamai" : "CF";
                return out;
            }
            // чемпион плохой — переезжаем на лучшего по пробе
            champChanges++;
            jrnl("смена tr (refresh): " + champD + " ("
                    + (champScore == null ? "нет ответа" : champScore + " мс") + ") → " + bestD + " ("
                    + (bestScore == Integer.MAX_VALUE ? "нет ответа" : bestScore + " мс") + ")");
        }
        probeChamp = bestScore;
        champType = AKAMAI_TR.contains(bestD) ? "Akamai" : "CF";
        List<byte[]> out = new ArrayList<>();
        for (byte[] ip : ranked) if (out.size() < 6) out.add(ip);
        if (old != null) for (byte[] ip : old) if (!containsIp(out, ip) && out.size() < 6) out.add(ip);
        return out;
    }

    private static String dotted(byte[] ip) {
        return (ip[0] & 0xFF) + "." + (ip[1] & 0xFF) + "." + (ip[2] & 0xFF) + "." + (ip[3] & 0xFF);
    }

    private static byte[] parseDotted(String s2) {
        try {
            String[] q = s2.split("\\.");
            return new byte[]{(byte) Integer.parseInt(q[0]), (byte) Integer.parseInt(q[1]),
                    (byte) Integer.parseInt(q[2]), (byte) Integer.parseInt(q[3])};
        } catch (Exception e) {
            return null;
        }
    }

    /** Вызывается из FixVpnService.onCreate: старт нового сеанса журнала. */
    public static void initJournal(java.io.File dir) {
        try {
            JOURNAL_FILE = new java.io.File(dir, "rbxfix-journal.log");
            SESSION_START = System.currentTimeMillis();
            jrnl("— сеанс запущен —");
        } catch (Throwable ignored) { }
    }

    private static void jrnl(String msg) {
        java.io.File f = JOURNAL_FILE;
        if (f == null) return;
        try {
            long sec = (System.currentTimeMillis() - SESSION_START) / 1000;
            StringBuilder b = new StringBuilder("+");
            if (sec / 60 < 10) b.append('0');
            b.append(sec / 60).append(':');
            if (sec % 60 < 10) b.append('0');
            b.append(sec % 60).append(' ').append(msg).append('\n');
            java.io.FileOutputStream o = new java.io.FileOutputStream(f, true);
            o.write(b.toString().getBytes("UTF-8"));
            o.close();
            if (f.length() > 65536) {   // не даём файлу расти вечно
                long skip = f.length() - 8192;
                java.io.FileInputStream in = new java.io.FileInputStream(f);
                in.skip(skip);
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                in.close();
                byte[] tail = bo.toByteArray();
                int nl = 0;
                while (nl < tail.length && tail[nl] != '\n') nl++;   // режем первую полустроку
                java.io.FileOutputStream w = new java.io.FileOutputStream(f, false);
                if (nl < tail.length) w.write(tail, nl + 1, tail.length - nl - 1);
                w.close();
            }
        } catch (Throwable ignored) { }
    }

    /** Хвост журнала для отчёта: последние строки, включая сеанс до тумблера. */
    public static String journalTail() {
        java.io.File f = JOURNAL_FILE;
        if (f == null || !f.exists()) return "";
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            String[] lines = bo.toString("UTF-8").trim().split("\n");
            StringBuilder b = new StringBuilder();
            int from = Math.max(0, lines.length - 10);
            for (int i = from; i < lines.length; i++) {
                if (b.length() > 0) b.append('\n');
                b.append(lines[i].trim());
            }
            return b.toString();
        } catch (Throwable ignored) { }
        return "";
    }

    /** Строка для отчёта: лучший IP зеркала и его задержка. */
    public static String bestMirrorInfo() {
        List<byte[]> ips = MIRROR_IPS.get("tr.rbxcdn.com");
        if (ips == null || ips.isEmpty()) return "нет IP";
        byte[] b = ips.get(0);
        String ipStr = (b[0] & 0xFF) + "." + (b[1] & 0xFF) + "." + (b[2] & 0xFF) + "." + (b[3] & 0xFF);
        return ipStr + ", канал: " + champType
                + (probeChamp > 0 ? ", проба (реальная загрузка): " + probeChamp + " мс" : "")
                + ", смен IP (tr): " + champChanges;
    }

    /** Переписывать ли этот запрос (только A/AAAA по известным хостам). */
    public static boolean shouldRewrite(String qname, int qtype) {
        if (qtype != DnsKit.TYPE_A && qtype != DnsKit.TYPE_AAAA) return false;
        return HOST_TO_MIRROR.containsKey(qname) || DYNAMIC.containsKey(qname)
                || DIRECT_IPS.containsKey(qname) || qname.endsWith(".rbxcdn.com");
    }

    /**
     * Автопоиск зеркала для незнакомого rbxcdn-хоста (один раз на хост).
     * Если найдено — хост начинает переписываться без перезапуска.
     */
    public static void discoverAsync(final String host) {
        if (HOST_TO_MIRROR.containsKey(host) || DYNAMIC.containsKey(host)) return;
        if (!DISCOVERY_TRIED.add(host)) return;
        Thread t = new Thread(() -> {
            String cf = DnsKit.discoverCloudfrontViaAws(host);
            if (cf != null) {
                List<byte[]> ips = DnsKit.resolveA4(cf);
                if (ips != null && !ips.isEmpty()) {
                    DYNAMIC.put(host, cf);
                    MIRROR_IPS.put(host, sortByRtt(ips));
                }
            }
        }, "rbxfix-discover-" + host);
        t.setDaemon(true);
        t.start();
    }

    /** Строка для отчёта: найденные автопоиском зеркала. */
    public static String dynamicInfo() {
        if (DYNAMIC.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("автопоиск: ");
        for (Map.Entry<String, String> e : DYNAMIC.entrySet()) {
            sb.append(e.getKey().split("\\.")[0]).append(" ").append(" ");
        }
        return sb.toString().trim() + "\n";
    }

    /** Готовый DNS-ответ для переписываемого запроса. */
    public static byte[] answer(DnsKit.Query q) {
        if (q.qtype == DnsKit.TYPE_A) {
            List<byte[]> ips = MIRROR_IPS.get(q.qname);
            if (ips == null) {
                ips = DIRECT_IPS.get(q.qname);
                if (ips == null && q.qname.endsWith(".rbxcdn.com")) ips = discoverDirect(q.qname);
            }
            if (ips != null) return DnsKit.buildAAnswers(q, ips);
        }
        return DnsKit.buildEmptyAnswer(q); // AAAA → пусто, клиент пойдёт по IPv4
    }

    /**
     * «Боковые двери»: Roblox начал раздавать часть ассетов через sc0ak/sc0gcp и т.п. —
     * этих хостов нет в карте, раньше они уходили на DNS оператора (отравлено → ассеты
     * не грузятся даже при идеальном tr). Узнаём истинные IP через merged-резолвер
     * (DoH неотравляем) и кэшируем; обновляется в refreshAsync.
     */
    private static List<byte[]> discoverDirect(String host) {
        List<byte[]> have = DIRECT_IPS.get(host);
        if (have != null) return have;
        if (!DIRECT_TRIED.add(host)) return DIRECT_IPS.get(host);   // уже копает другой поток
        try {
            List<byte[]> ips = DnsKit.resolveA4Merged(host);
            if (ips == null || ips.isEmpty()) ips = DnsKit.resolveA4(host);
            if (ips != null && !ips.isEmpty()) {
                List<byte[]> cap = ips.size() > 6 ? new ArrayList<>(ips.subList(0, 6)) : new ArrayList<>(ips);
                DIRECT_IPS.put(host, cap);
                jrnl("новый rbxcdn-хост: " + host.split("\\.")[0] + " → прямой CDN (" + cap.size() + " IP)");
                return cap;
            }
        } catch (Throwable ignored) { }
        DIRECT_TRIED.remove(host);   // не вышло — попробуем при следующем запросе
        return null;
    }

    /** Текущие IP зеркала хоста (для проверки из UI). */
    public static List<byte[]> currentIps(String host) {
        return MIRROR_IPS.get(host);
    }

    /** Входит ли IP в список текущих IP зеркал. */
    public static boolean isMirrorIp(byte[] ip4) {
        for (List<byte[]> list : MIRROR_IPS.values()) {
            for (byte[] m : list) {
                if (Arrays.equals(m, ip4)) return true;
            }
        }
        return false;
    }
}
