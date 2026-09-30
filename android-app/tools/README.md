# Юнит-тест DNS-движка

Запускается обычной Java на любом ПК (Android не нужен):

```bash
cd app/src/main/java/com/robloxfix/rfimages
javac -encoding UTF-8 -d /tmp/rbxtest DnsKit.java MirrorConfig.java ProtectedSSLSocketFactory.java
javac -encoding UTF-8 -cp /tmp/rbxtest -d /tmp/rbxtest ../../../../../../../../tools/TestDnsKit.java
java -cp /tmp/rbxtest TestDnsKit
```
Проверяет: разбор запроса → сборка ответа → упаковка UDP/IP → чексуммы → обратный разбор. 24 проверки.
