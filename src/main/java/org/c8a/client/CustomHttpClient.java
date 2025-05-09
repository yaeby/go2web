package org.c8a.client;

import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CustomHttpClient {

    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;
    private static final int DEFAULT_TIMEOUT = 10000; // 10 seconds

    private final Map<String, String> requestHeaders;
    private final int connectTimeout;
    private final int readTimeout;

    public CustomHttpClient() {
        this(DEFAULT_TIMEOUT, DEFAULT_TIMEOUT);
    }

    public CustomHttpClient(int connectTimeout, int readTimeout) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.requestHeaders = new HashMap<>();

        setRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
        setRequestHeader("Accept", "application/json, text/html;q=0.9, application/xhtml+xml;q=0.8, application/xml;q=0.7");
        setRequestHeader("Accept-Language", "en-US,en;q=0.5");
        setRequestHeader("Connection", "close");
    }

    public void setRequestHeader(String name, String value) {
        if (value != null) {
            requestHeaders.put(name, value);
        } else {
            requestHeaders.remove(name);
        }
    }

    public HttpResponse get(String url) throws IOException {
        return request("GET", url, null);
    }

    public HttpResponse request(String method, String url, byte[] body) throws IOException {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();

            if (scheme == null) {
                throw new IOException("URL scheme is missing");
            }

            boolean isHttps = "https".equalsIgnoreCase(scheme);
            if (port == -1) {
                port = isHttps ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT;
            }

            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            String query = uri.getRawQuery();
            if (query != null && !query.isEmpty()) {
                path += "?" + query;
            }

            System.out.println("Connecting to " + host + ":" + port + "...");

            Socket socket;
            if (isHttps) {
                socket = SSLSocketFactory.createSSLSocket(host, port, connectTimeout);
            } else {
                socket = new Socket(host, port);
                socket.setSoTimeout(readTimeout);
            }

            StringBuilder requestBuilder = new StringBuilder();
            requestBuilder.append(method).append(" ").append(path).append(" HTTP/1.1\r\n");
            requestBuilder.append("Host: ").append(host).append("\r\n");

            for (Map.Entry<String, String> header : requestHeaders.entrySet()) {
                requestBuilder.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
            }

            if (body != null && body.length > 0) {
                requestBuilder.append("Content-Length: ").append(body.length).append("\r\n");
            }

            requestBuilder.append("\r\n");

            OutputStream out = socket.getOutputStream();
            out.write(requestBuilder.toString().getBytes("UTF-8"));

            if (body != null && body.length > 0) {
                out.write(body);
            }
            out.flush();

            InputStream in = socket.getInputStream();
            return parseResponse(in);

        } catch (URISyntaxException e) {
            throw new IOException("Invalid URL: " + e.getMessage(), e);
        }
    }

    private HttpResponse parseResponse(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));

        String statusLine = reader.readLine();
        if (statusLine == null) {
            throw new IOException("Empty response");
        }

        Pattern statusPattern = Pattern.compile("HTTP/\\d\\.\\d (\\d+) (.*)");
        Matcher matcher = statusPattern.matcher(statusLine);
        if (!matcher.matches()) {
            throw new IOException("Invalid status line: " + statusLine);
        }

        int statusCode = Integer.parseInt(matcher.group(1));
        String statusMessage = matcher.group(2);

        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int colonPos = line.indexOf(':');
            if (colonPos > 0) {
                String headerName = line.substring(0, colonPos).trim();
                String headerValue = line.substring(colonPos + 1).trim();
                headers.put(headerName, headerValue);

                headers.put(headerName.toLowerCase(), headerValue);
            }
        }

        String contentLengthStr = headers.get("content-length");

        boolean isChunked = "chunked".equalsIgnoreCase(headers.get("transfer-encoding"));

        ByteArrayOutputStream responseBody = new ByteArrayOutputStream();

        if (isChunked) {
            readChunkedBody(reader, responseBody);
        } else if (contentLengthStr != null) {
            int contentLength = Integer.parseInt(contentLengthStr);
            readFixedLengthBody(in, responseBody, contentLength);
        } else {
            readUntilEOF(in, responseBody);
        }

        return new HttpResponse(statusCode, statusMessage, headers, responseBody.toByteArray());
    }

    private void readChunkedBody(BufferedReader reader, ByteArrayOutputStream output) throws IOException {
        try {
            while (true) {
                String chunkSizeLine = reader.readLine();
                if (chunkSizeLine == null) break;

                if (chunkSizeLine.trim().isEmpty()) {
                    continue;
                }

                String hexPart = extractHexPart(chunkSizeLine);
                if (hexPart.isEmpty()) {
//                    System.err.println("Warning: Skipping invalid chunk header: " + chunkSizeLine);
                    continue;
                }

                try {
                    int chunkSize = Integer.parseInt(hexPart, 16);

                    if (chunkSize == 0) {
                        reader.readLine();
                        break;
                    }

                    char[] buffer = new char[chunkSize];
                    int totalRead = 0;

                    while (totalRead < chunkSize) {
                        int read = reader.read(buffer, totalRead, chunkSize - totalRead);
                        if (read == -1) break;
                        totalRead += read;
                    }

                    output.write(new String(buffer, 0, totalRead).getBytes(StandardCharsets.UTF_8));

                    reader.readLine();
                } catch (NumberFormatException e) {
                    System.err.println("Warning: Failed to parse chunk size from: " + chunkSizeLine);
                    continue;
                }
            }

            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                //  Consuming the trailing headers
            }
        } catch (IOException e) {
            System.err.println("Error reading chunked body: " + e.getMessage());
            throw e;
        }
    }


    private String extractHexPart(String line) {
        line = line.trim();

        int endPos = 0;
        while (endPos < line.length() && isHexDigit(line.charAt(endPos))) {
            endPos++;
        }

        return endPos > 0 ? line.substring(0, endPos) : "";
    }

    private boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') ||
                (c >= 'a' && c <= 'f') ||
                (c >= 'A' && c <= 'F');
    }

    private void readFixedLengthBody(InputStream in, ByteArrayOutputStream output, int contentLength) throws IOException {
        byte[] buffer = new byte[1024];
        int bytesRemaining = contentLength;

        while (bytesRemaining > 0) {
            int bytesToRead = Math.min(buffer.length, bytesRemaining);
            int bytesRead = in.read(buffer, 0, bytesToRead);

            if (bytesRead == -1) {
                break;
            }

            output.write(buffer, 0, bytesRead);
            bytesRemaining -= bytesRead;
        }
    }

    private void readUntilEOF(InputStream in, ByteArrayOutputStream output) throws IOException {
        byte[] buffer = new byte[1024];
        int bytesRead;

        while ((bytesRead = in.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
    }

    public static class HttpResponse {
        private final int statusCode;
        private final String statusMessage;
        private final Map<String, String> headers;
        private final byte[] body;

        public HttpResponse(int statusCode, String statusMessage, Map<String, String> headers, byte[] body) {
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
            this.headers = headers;
            this.body = body;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getStatusMessage() {
            return statusMessage;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public String getHeader(String name) {
            return headers.get(name.toLowerCase());
        }

        public byte[] getBody() {
            return body;
        }

        public String getBodyAsString() {
            try {
                return new String(body, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                return new String(body);
            }
        }

        public boolean isRedirect() {
            return statusCode >= 300 && statusCode < 400;
        }
    }
}
