package com.robloxfix.rfimages;

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

    /** Хосты, которые переписываем (актуально на 30.09.2026; проверялось реальными запросами). */
    static {
        HOST_TO_MIRROR.put("tr.rbxcdn.com", "d77muyc5iodv8.cloudfront.net");
        HOST_TO_MIRROR.put("t0.rbxcdn.com", "djm1c8bbf58td.cloudfront.net");
        HOST_TO_MIRROR.put("t1.rbxcdn.com", "dy9nmzn7lz0hh.cloudfront.net");
        HOST_TO_MIRROR.put("t5.rbxcdn.com", "d1cn2tk5nesoa7.cloudfront.net");
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
    }

    /** Живые IP зеркал: rbxcdn-хост → список IPv4 (байты). */
    private static final Map<String, List<byte[]>> MIRROR_IPS = new ConcurrentHashMap<>();

    private static final AtomicLong LAST_REFRESH = new AtomicLong(0);
    private static final long REFRESH_INTERVAL_MS = 10 * 60 * 1000; // 10 минут

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

    /** Асинхронно обновляет IP всех зеркал через DoH (не чаще раза в 10 минут). */
    public static void refreshAsync() {
        long now = System.currentTimeMillis();
        long prev = LAST_REFRESH.get();
        if (now - prev < REFRESH_INTERVAL_MS) return;
        if (!LAST_REFRESH.compareAndSet(prev, now)) return;

        Thread t = new Thread(() -> {
            for (Map.Entry<String, String> e : HOST_TO_MIRROR.entrySet()) {
                List<byte[]> ips = DnsKit.resolveA4(e.getValue());
                if (ips != null && !ips.isEmpty()) {
                    MIRROR_IPS.put(e.getKey(), ips);
                }
            }
        }, "rbxfix-mirror-refresh");
        t.setDaemon(true);
        t.start();
    }

    /** Переписывать ли этот запрос (только A/AAAA по известным хостам). */
    public static boolean shouldRewrite(String qname, int qtype) {
        if (!HOST_TO_MIRROR.containsKey(qname)) return false;
        return qtype == DnsKit.TYPE_A || qtype == DnsKit.TYPE_AAAA;
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
