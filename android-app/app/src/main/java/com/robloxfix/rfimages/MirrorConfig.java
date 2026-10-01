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
                    Thread.sleep(60_000);
                    List<byte[]> ips = MIRROR_IPS.get("tr.rbxcdn.com");
                    if (ips == null || ips.isEmpty()) continue;
                    int r = rttMs(ips.get(0));
                    if (r < 250) {
                        BAD_STREAK.set(0);
                        LAST_RTT_MS = r;
                        continue;
                    }
                    if (BAD_STREAK.incrementAndGet() >= 2) {
                        BAD_STREAK.set(0);
                        LAST_REFRESH.set(0);       // снять троттлинг
                        refreshAsync();            // внеплановое обновление СЕЙЧАС
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
    public static void refreshAsync() {
        long now = System.currentTimeMillis();
        long prev = LAST_REFRESH.get();
        if (now - prev < REFRESH_INTERVAL_MS) return;
        if (!LAST_REFRESH.compareAndSet(prev, now)) return;
        startWatchdog();

        Thread t = new Thread(() -> {
            // все хосты — параллельно (их теперь 20)
            Map<String, List<byte[]>> results = new ConcurrentHashMap<>();
            List<Thread> jobs = new ArrayList<>();
            for (Map.Entry<String, String> e : HOST_TO_MIRROR.entrySet()) {
                Thread j = new Thread(() -> {
                    List<byte[]> ips = DnsKit.resolveA4(e.getValue());
                    if (ips != null && !ips.isEmpty()) {
                        results.put(e.getKey(), stableMerge(e.getKey(), sortByRtt(ips)));
                    }
                }, "rbxfix-refresh-" + e.getKey());
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

    /** TCP-подключение на 443 — меряем реальную задержку до эджа. */
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

    /** Строка для отчёта: лучший IP зеркала и его задержка. */
    public static String bestMirrorInfo() {
        List<byte[]> ips = MIRROR_IPS.get("tr.rbxcdn.com");
        if (ips == null || ips.isEmpty()) return "нет IP";
        byte[] b = ips.get(0);
        String ipStr = (b[0] & 0xFF) + "." + (b[1] & 0xFF) + "." + (b[2] & 0xFF) + "." + (b[3] & 0xFF);
        return ipStr + (LAST_RTT_MS < Integer.MAX_VALUE ? " (" + LAST_RTT_MS + " мс)" : "")
                + ", смен IP (tr): " + champChanges;
    }

    /** Переписывать ли этот запрос (только A/AAAA по известным хостам). */
    public static boolean shouldRewrite(String qname, int qtype) {
        if (qtype != DnsKit.TYPE_A && qtype != DnsKit.TYPE_AAAA) return false;
        return HOST_TO_MIRROR.containsKey(qname) || DYNAMIC.containsKey(qname);
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
            return DnsKit.buildAAnswers(q, ips);
        }
        return DnsKit.buildEmptyAnswer(q); // AAAA → пусто, клиент пойдёт по IPv4
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
