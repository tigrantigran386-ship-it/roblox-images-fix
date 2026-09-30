package com.robloxfix.rfimages;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Минималистичный набор для работы с DNS-пакетами:
 * разбор запросов, сборка ответов, DoH-клиент (RFC 8484) и UDP-фолбэк.
 * Только стандартная библиотека — без сторонних зависимостей.
 */
public final class DnsKit {

    public static final int TYPE_A = 1;
    public static final int TYPE_AAAA = 28;

    /** Кто-то, кто умеет «защищать» сокеты от нашего же VPN (реализует FixVpnService). */
    public interface SocketProtector {
        void protectSocket(java.net.Socket s);
        void protectSocket(java.net.DatagramSocket s);
    }

    /** Устанавливается сервисом при старте; null = защиты нет (тестовое окружение). */
    public static volatile SocketProtector protector;

    /** Разобранный DNS-запрос + метаданные для сборки ответа. */
    public static final class Query {
        public int id;
        public String qname = "";   // нижний регистр, без завершающей точки
        public int qtype;
        public int qEnd;            // смещение сразу за секцией Question
        public int clientPort;      // UDP-порт клиента (кому отвечаем)
        public byte[] clientAddr;   // IP клиента (4 байта для IPv4)
        public byte[] raw;          // исходный DNS-запрос (для копирования секции Question)
    }

    private DnsKit() {}

    // ─────────────────────────── разбор ───────────────────────────

    /**
     * Разбирает UDP-пакет из TUN. Возвращает Query, если это валидный
     * DNS-запрос (QR=0, класс IN), иначе null.
     */
    public static Query parseUdpDns(byte[] pkt, int len) {
        try {
            int version = (pkt[0] >> 4) & 0xF;
            if (version != 4) return null;                 // работаем по IPv4 (туннель только v4)
            if (len < 28) return null;
            int ihl = (pkt[0] & 0x0F) * 4;
            int proto = pkt[9] & 0xFF;
            if (proto != 17) return null;                  // не UDP
            byte[] dst = Arrays.copyOfRange(pkt, 16, 20);
            byte[] src = Arrays.copyOfRange(pkt, 12, 16);
            int udpOff = ihl;
            if (len < udpOff + 8) return null;
            int dstPort = u16(pkt, udpOff + 2);
            if (dstPort != 53) return null;
            int srcPort = u16(pkt, udpOff + 0);
            int udpLen = u16(pkt, udpOff + 4);
            int dnsOff = udpOff + 8;
            int dnsLen = Math.min(udpLen - 8, len - dnsOff);
            if (dnsLen < 17) return null;
            byte[] dns = Arrays.copyOfRange(pkt, dnsOff, dnsOff + dnsLen);

            Query q = new Query();
            q.clientAddr = src;
            q.clientPort = srcPort;

            int id = u16(dns, 0);
            int flags = u16(dns, 2);
            if ((flags & 0x8000) != 0) return null;        // QR=1 — это ответ, не запрос
            int qdcount = u16(dns, 4);
            if (qdcount < 1) return null;

            int[] end = new int[1];
            String name = readName(dns, 12, end);
            if (name == null) return null;
            if (end[0] + 4 > dns.length) return null;
            int qtype = u16(dns, end[0]);
            int qclass = u16(dns, end[0] + 2);
            if (qclass != 1) return null;                  // только IN

            q.id = id;
            q.qname = name;
            q.qtype = qtype;
            q.qEnd = end[0] + 4;
            return q;
        } catch (Exception e) {
            return null;
        }
    }

    private static int u16(byte[] b, int off) {
        return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
    }

    /** Читает QNAME. end[0] = смещение после имени (учитывая возможный compression pointer). */
    private static String readName(byte[] p, int off, int[] end) {
        StringBuilder sb = new StringBuilder();
        int i = off;
        while (i < p.length) {
            int b = p[i] & 0xFF;
            if (b == 0) { end[0] = i + 1; return sb.toString(); }
            if ((b & 0xC0) == 0xC0) { end[0] = i + 2; return sb.toString(); } // указатель — выходим
            if (b > 63 || i + 1 + b > p.length) return null;
            if (sb.length() > 0) sb.append('.');
            for (int k = 1; k <= b; k++) {
                char c = (char) (p[i + k] & 0xFF);
                if (c >= 'A' && c <= 'Z') c += 32;
                sb.append(c);
            }
            i += 1 + b;
        }
        return null;
    }

    // ─────────────────────────── сборка ответов ───────────────────────────

    /** Ответ A-записями (IPv4) на домен из вопроса. */
    public static byte[] buildAAnswers(Query q, List<byte[]> ips) {
        try {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            int an = ips == null ? 0 : ips.size();
            writeHeader(o, q.id, 0x8180, an);              // QR=1 RD=1 RA=1 RCODE=0
            o.write(q.raw, 12, q.qEnd - 12);               // секция Question как есть
            if (ips != null) {
                for (byte[] ip : ips) {
                    o.write(0xC0); o.write(12);            // имя — указатель на вопрос (офсет 12)
                    o.write(0); o.write(TYPE_A);           // type A
                    o.write(0); o.write(1);                // class IN
                    o.write(0); o.write(0); o.write(0); o.write(120); // TTL 120 сек
                    o.write(0); o.write(4);                // RDLENGTH
                    o.write(ip, 0, 4);
                }
            }
            return o.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    /** Пустой успешный ответ (используем для AAAA по rbxcdn — «IPv6 нет», клиент пойдёт по IPv4). */
    public static byte[] buildEmptyAnswer(Query q) {
        try {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            writeHeader(o, q.id, 0x8180, 0);
            o.write(q.raw, 12, q.qEnd - 12);
            return o.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeHeader(ByteArrayOutputStream o, int id, int flags, int an)
            throws IOException {
        o.write((id >> 8) & 0xFF); o.write(id & 0xFF);
        o.write((flags >> 8) & 0xFF); o.write(flags & 0xFF);
        o.write(0); o.write(1);                            // QDCOUNT = 1
        o.write((an >> 8) & 0xFF); o.write(an & 0xFF);     // ANCOUNT
        o.write(0); o.write(0);                            // NSCOUNT
        o.write(0); o.write(0);                            // ARCOUNT
    }

    // ─────────────────────────── клиенты апстрима ───────────────────────────

    private static final String[] DOH_URLS = {
            "https://cloudflare-dns.com/dns-query",
            "https://dns.google/dns-query",
    };

    /** DoH по RFC 8484: POST application/dns-message. Возвращает ответ апстрима как есть. */
    public static byte[] dohQuery(String url, byte[] query) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        if (c instanceof javax.net.ssl.HttpsURLConnection) {
            ((javax.net.ssl.HttpsURLConnection) c).setSSLSocketFactory(
                    ProtectedSSLSocketFactory.get());
        }
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        c.setRequestProperty("Content-Type", "application/dns-message");
        c.setRequestProperty("Accept", "application/dns-message");
        OutputStream os = null;
        try {
            os = c.getOutputStream();
            os.write(query);
            os.flush();
        } finally {
            if (os != null) os.close();
        }
        int code = c.getResponseCode();
        if (code != 200) throw new IOException("DoH HTTP " + code);
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        return readAll(in);
    }

    // ── обычные (не переписываемые) запросы: пересылка на DNS оператора ──

    private static volatile List<byte[]> upstreams = new ArrayList<>();

    /** Список апстримов (DNS сети + запасные). Устанавливается сервисом. */
    public static void setUpstreams(List<byte[]> ips) {
        upstreams = new ArrayList<>(ips);
    }

    /** Один UDP-запрос на конкретный DNS-сервер (защищённый сокет). */
    private static byte[] udpTo(byte[] query, byte[] serverIp, int timeoutMs) throws IOException {
        DatagramSocket s = new DatagramSocket(null);
        if (protector != null) protector.protectSocket(s);
        s.bind(new java.net.InetSocketAddress(0));
        try {
            s.setSoTimeout(timeoutMs);
            s.send(new DatagramPacket(query, query.length,
                    InetAddress.getByAddress(serverIp), 53));
            byte[] buf = new byte[4096];
            DatagramPacket rp = new DatagramPacket(buf, buf.length);
            s.receive(rp);
            return Arrays.copyOf(rp.getData(), rp.getLength());
        } finally {
            s.close();
        }
    }

    /**
     * Быстрая пересылка на обычные DNS-серверы (оператора + запасные).
     * Ведёт себя ровно как DNS телефона без VPN — та же скорость.
     */
    public static byte[] forwardPlain(byte[] query) {
        for (byte[] ip : upstreams) {
            try {
                return udpTo(query, ip, 2500);
            } catch (Exception ignored) { }
        }
        return null;
    }

    /** Запасной путь: DoH Cloudflare → Google → обычный UDP на 1.1.1.1. */
    public static byte[] upstreamQuery(byte[] query) {
        for (String url : DOH_URLS) {
            try { return dohQuery(url, query); } catch (Exception ignored) { }
        }
        try { return udpTo(query, new byte[]{1, 1, 1, 1}, 3000); } catch (Exception ignored) { }
        return null;
    }

    /** SERVFAIL-ответ: клиент сразу понимает, что надо повторить, а не ждёт вечно. */
    public static byte[] buildServFail(Query q) {
        try {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            writeHeader(o, q.id, 0x8182, 0);           // QR=1, RCODE=2 (SERVFAIL)
            o.write(q.raw, 12, q.qEnd - 12);
            return o.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    /** Резолвит A-записи хоста: сперва обычный DNS, затем DoH (для IP зеркал). */
    public static List<byte[]> resolveA4(String host) {
        try {
            byte[] q = buildQuery(host, TYPE_A);
            byte[] resp = forwardPlain(q);
            if (resp == null) resp = upstreamQuery(q);
            if (resp == null) return null;
            return parseARecords(resp);
        } catch (Exception e) {
            return null;
        }
    }

    /** Собирает бинарный DNS-запрос (RD=1, один вопрос). */
    public static byte[] buildQuery(String host, int type) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(0x12); o.write(0x34);                      // ID — любой
        o.write(0x01); o.write(0x00);                      // RD=1
        o.write(0); o.write(1);                            // QDCOUNT
        o.write(0); o.write(0); o.write(0); o.write(0); o.write(0); o.write(0);
        for (String label : host.split("\\.")) {
            o.write(label.length());
            o.write(label.getBytes("US-ASCII"));
        }
        o.write(0);
        o.write((type >> 8) & 0xFF); o.write(type & 0xFF);
        o.write(0); o.write(1);                            // IN
        return o.toByteArray();
    }

    /** Вытаскивает IPv4-ответы из DNS-ответа. */
    public static List<byte[]> parseARecords(byte[] resp) {
        try {
            List<byte[]> out = new ArrayList<>();
            int qd = u16(resp, 4);
            int an = u16(resp, 6);
            int[] end = new int[1];
            int off = 12;
            for (int i = 0; i < qd; i++) {
                readName(resp, off, end);
                off = end[0] + 4;
            }
            for (int i = 0; i < an && off + 12 <= resp.length; i++) {
                readName(resp, off, end);
                off = end[0];
                int type = u16(resp, off);
                int rdlen = u16(resp, off + 8);
                off += 10;
                if (type == TYPE_A && rdlen == 4 && off + 4 <= resp.length) {
                    out.add(Arrays.copyOfRange(resp, off, off + 4));
                }
                off += rdlen;
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        in.close();
        return o.toByteArray();
    }

    // ─────────────────────────── упаковка UDP/IP ответа ───────────────────────────

    /** Собирает IPv4+UDP пакет с DNS-ответом клиенту. */
    public static byte[] buildUdp4Packet(byte[] srcIp, int srcPort, byte[] dstIp, int dstPort,
                                         byte[] payload) {
        int total = 20 + 8 + payload.length;
        byte[] p = new byte[total];
        // IPv4 header
        p[0] = 0x45;                                       // v4, IHL 20
        p[1] = 0;                                          // TOS
        p[2] = (byte) ((total >> 8) & 0xFF);
        p[3] = (byte) (total & 0xFF);
        p[4] = 0; p[5] = 0;                                // ID
        p[6] = 0x40; p[7] = 0;                             // DF
        p[8] = 64;                                         // TTL
        p[9] = 17;                                         // proto UDP
        p[10] = 0; p[11] = 0;                              // checksum placeholder
        System.arraycopy(srcIp, 0, p, 12, 4);
        System.arraycopy(dstIp, 0, p, 16, 4);
        int ck = checksum(p, 0, 20);
        p[10] = (byte) ((ck >> 8) & 0xFF);
        p[11] = (byte) (ck & 0xFF);
        // UDP header
        int udpLen = 8 + payload.length;
        p[20] = (byte) ((srcPort >> 8) & 0xFF);
        p[21] = (byte) (srcPort & 0xFF);
        p[22] = (byte) ((dstPort >> 8) & 0xFF);
        p[23] = (byte) (dstPort & 0xFF);
        p[24] = (byte) ((udpLen >> 8) & 0xFF);
        p[25] = (byte) (udpLen & 0xFF);
        p[26] = 0; p[27] = 0;                              // UDP checksum = 0 (допустимо в IPv4)
        System.arraycopy(payload, 0, p, 28, payload.length);
        return p;
    }

    /** Интернет-контрольная сумма (RFC 1071). */
    public static int checksum(byte[] b, int off, int len) {
        int sum = 0;
        for (int i = 0; i < len - 1; i += 2) {
            sum += ((b[off + i] & 0xFF) << 8) | (b[off + i + 1] & 0xFF);
        }
        if ((len & 1) != 0) sum += (b[off + len - 1] & 0xFF) << 8;
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        return (~sum) & 0xFFFF;
    }
}
