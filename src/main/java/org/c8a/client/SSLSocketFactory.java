package org.c8a.client;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

public class SSLSocketFactory {

    /**
     * Creates an SSL socket for secure communication.
     *
     * @param host The hostname to connect to
     * @param port The port to connect to
     * @param timeout The connection timeout in milliseconds
     * @return A connected SSL socket
     * @throws IOException If there's an error creating or connecting the socket
     */
    public static Socket createSSLSocket(String host, int port, int timeout) throws IOException {
        try {
            // Create SSL context
            SSLContext sslContext = SSLContext.getInstance("TLS");

            // Setup trust manager that trusts all certificates
            // Note: This is insecure and should be improved in a production environment
            TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                        public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                    }
            };

            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            // Create SSL socket
            javax.net.ssl.SSLSocketFactory factory = sslContext.getSocketFactory();
            SSLSocket sslSocket = (SSLSocket) factory.createSocket();

            // Set timeouts
            sslSocket.setSoTimeout(timeout);

            // Connect with timeout
            sslSocket.connect(new InetSocketAddress(host, port), timeout);

            // Start handshake
            sslSocket.startHandshake();

            return sslSocket;

        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IOException("SSL error: " + e.getMessage(), e);
        }
    }
}
