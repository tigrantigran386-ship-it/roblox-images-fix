import com.robloxfix.rfimages.DnsKit;
import com.robloxfix.rfimages.MirrorConfig;

import java.util.Arrays;
import java.util.List;

/**
 * Локальный тест DNS-движка приложения (запускается обычной java на ПК).
 * Проверяет: разбор запроса, сборку ответа, упаковку UDP/IP-пакета.
 */
public class TestDnsKit {
    static int pass = 0, fail = 0;

    static void check(String name, boolean ok, String extra) {
        if (ok) { pass++; System.out.println("  ✅ " + name); }
        else { fail++; System.out.println("  ❌ " + name + (extra.isEmpty() ? "" : " | " + extra)); }
    }

    static void check(String name, boolean ok) { check(name, ok, ""); }

    public static void main(String[] args) throws Exception {
        // ── 1. Реальный DNS-запрос Roblox-клиента (A, tr.rbxcdn.com) ──
        byte[] query = DnsKit.buildQuery("tr.rbxcdn.com", DnsKit.TYPE_A);
        System.out.println("— Сборка/разбор запроса");
        check("query > 17 байт", query.length > 17, String.valueOf(query.length));

        // упаковываем в UDP/IP как это делает ядро Android
        byte[] clientIp = {(byte)10, 111, (byte)222, 77};
        byte[] dnsIp = {10, 111, (byte)222, 3};
        byte[] wire = DnsKit.buildUdp4Packet(clientIp, 51234, dnsIp, 53, query);

        // ...и разбираем как это делает приложение
        DnsKit.Query q = DnsKit.parseUdpDns(wire, wire.length);
        check("запрос разобран", q != null, "parseUdpDns вернул null!");
        check("qname = tr.rbxcdn.com", q != null && q.qname.equals("tr.rbxcdn.com"), q == null ? "" : q.qname);
        check("qtype = A(1)", q != null && q.qtype == 1);
        check("clientPort = 51234", q != null && q.clientPort == 51234);
        check("clientAddr сохранён", q != null && Arrays.equals(q.clientAddr, clientIp));

        // ── 2. Ответ с зеркальными IP ──
        System.out.println("— Сборка ответа");
        List<byte[]> ips = List.of(new byte[]{(byte)52, (byte)85, (byte)129, (byte)61}, new byte[]{18, (byte)172, (byte)170, 91});
        byte[] resp = DnsKit.buildAAnswers(q, ips);
        check("ответ собран", resp != null);
        // QR=1?
        check("QR=1 (это ответ)", resp != null && (resp[2] & 0x80) != 0);
        check("ANCOUNT=2", resp != null && ((resp[6] & 0xFF) << 8 | (resp[7] & 0xFF)) == 2);
        check("ID сохранён", resp != null && resp[0] == query[0] && resp[1] == query[1]);

        // RCODE=0
        check("RCODE=0", resp != null && (resp[3] & 0x0F) == 0);

        // ── 3. Ответ через buildUdp4Packet: структура + чексуммы ──
        System.out.println("— Упаковка UDP/IP ответа клиенту");
        byte[] out = DnsKit.buildUdp4Packet(dnsIp, 53, clientIp, 51234, resp);
        check("IP version=4, IHL=5", out[0] == 0x45);
        check("proto=UDP(17)", out[9] == 17);
        int total = ((out[2] & 0xFF) << 8) | (out[3] & 0xFF);
        check("total length верна", total == out.length, total + " vs " + out.length);
        int ipCk = ((out[10] & 0xFF) << 8) | (out[11] & 0xFF);
        check("IP checksum не 0", ipCk != 0, String.valueOf(ipCk));
        int udpLen = ((out[24] & 0xFF) << 8) | (out[25] & 0xFF);
        check("udp length верна", udpLen == out.length - 20);
        check("src=10.111.222.3", (out[12]&0xFF)==10 && (out[13]&0xFF)==111 && (out[14]&0xFF)==222 && (out[15]&0xFF)==3);
        check("dst=client", (out[16]&0xFF)==10 && (out[17]&0xFF)==111 && (out[18]&0xFF)==222 && (out[19]&0xFF)==77);
        check("dstPort=51234", ((out[22]&0xFF)<<8 | (out[23]&0xFF)) == 51234);
        check("srcPort=53", ((out[20]&0xFF)<<8 | (out[21]&0xFF)) == 53);

        // ── 4. SERVFAIL и пустой ответ ──
        System.out.println("— Спец-ответы");
        byte[] sf = DnsKit.buildServFail(q);
        check("SERVFAIL: RCODE=2", sf != null && (sf[3] & 0x0F) == 2);
        DnsKit.Query qAaaa = new DnsKit.Query();
        qAaaa.id = 0x4242; qAaaa.qtype = DnsKit.TYPE_AAAA; qAaaa.qname = "tr.rbxcdn.com";
        qAaaa.qEnd = 12 + 13 + 4;
        qAaaa.raw = DnsKit.buildQuery("tr.rbxcdn.com", DnsKit.TYPE_AAAA);
        byte[] empty = DnsKit.buildEmptyAnswer(qAaaa);
        check("AAAA: пустой NOERROR", empty != null && (empty[3] & 0x0F) == 0
                && ((empty[6]&0xFF)<<8 | (empty[7]&0xFF)) == 0);

        // ── 5. parseARecords: парсим свой же ответ ──
        System.out.println("— Парсинг A-записей");
        List<byte[]> got = DnsKit.parseARecords(resp);
        check("найдены 2 A-записи", got != null && got.size() == 2, got == null ? "null" : String.valueOf(got.size()));
        check("IP совпадают", got != null && got.size() == 2
                && Arrays.equals(got.get(0), ips.get(0)) && Arrays.equals(got.get(1), ips.get(1)));

        System.out.println("\nИТОГ: " + pass + " ✅ / " + fail + " ❌");
        if (fail > 0) System.exit(1);
    }
}
