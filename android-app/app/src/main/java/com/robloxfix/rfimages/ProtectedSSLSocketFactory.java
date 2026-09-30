package com.robloxfix.rfimages;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import javax.net.ssl.SSLSocketFactory;

/**
 * SSL-фабрика сокетов, которые «защищены» от нашего же VPN
 * (VpnService.protect). Иначе на прошивках, где VPN становится сетью
 * по умолчанию, DoH-запросы самого приложения зацикливались бы в туннель.
 */
public final class ProtectedSSLSocketFactory extends SSLSocketFactory {

    private static volatile ProtectedSSLSocketFactory instance;

    public static ProtectedSSLSocketFactory get() {
        if (instance == null) {
            synchronized (ProtectedSSLSocketFactory.class) {
                if (instance == null) instance = new ProtectedSSLSocketFactory();
            }
        }
        return instance;
    }

    private final SSLSocketFactory delegate = (SSLSocketFactory) SSLSocketFactory.getDefault();

    private ProtectedSSLSocketFactory() {}

    private Socket p(Socket s) throws IOException {
        if (DnsKit.protector != null) DnsKit.protector.protectSocket(s);
        return s;
    }

    /** Основной путь HttpsURLConnection: создаём незасоединённый сокет и защищаем ДО коннекта. */
    @Override
    public Socket createSocket() throws IOException {
        return p(new Socket());
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
        return p(new Socket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
            throws IOException {
        return p(new Socket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return p(new Socket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
            throws IOException {
        return p(new Socket(address, port, localAddress, localPort));
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose)
            throws IOException {
        return delegate.createSocket(s, host, port, autoClose);
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }
}
