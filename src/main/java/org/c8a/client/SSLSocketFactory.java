package org.c8a.client;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

public class SSLSocketFactory {

    public static Socket createSSLSocket(String host, int port, int timeout) throws IOException {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");

            TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                        public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                    }
            };

            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            javax.net.ssl.SSLSocketFactory factory = sslContext.getSocketFactory();
            SSLSocket sslSocket = (SSLSocket) factory.createSocket();

            sslSocket.setSoTimeout(timeout);

            sslSocket.connect(new InetSocketAddress(host, port), timeout);

            sslSocket.startHandshake();

            return sslSocket;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IOException("SSL error: " + e.getMessage(), e);
        }
    }
}
