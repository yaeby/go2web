package org.c8a.handler;

import org.c8a.cache.CacheEntry;
import org.c8a.cache.CacheManager;
import org.c8a.connection.HttpConnection;
import org.c8a.porcessor.ContentProcessor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpHandler {

    private final CacheManager cacheManager;
    private static final int MAX_REDIRECTS = 5;

    public HttpHandler(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void fetchURL(String urlString) {
        int redirectCount = 0;

        try {
            if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                urlString = "https://" + urlString;
            }

            while (true) {
                CacheEntry cached = cacheManager.getEntry(urlString);
                if (cached != null && !cached.isExpired()) {
                    System.out.println("\nServing from cache:");
                    System.out.println(cached.content());
                    return;
                }

                HttpURLConnection connection = HttpConnection.getHttpURLConnection(urlString, cached);

                int responseCode = connection.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    System.out.println("\nResource not modified. Serving from cache:");
                    assert cached != null;
                    System.out.println(cached.content());
                    return;
                }

                if (responseCode >= 300 && responseCode < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isEmpty()) {
                        System.out.println("\nError: Redirect requested but no Location header found");
                        return;
                    }

                    URL base = new URL(urlString);
                    URL resolvedUrl = new URL(base, location);
                    urlString = resolvedUrl.toString();

                    connection.disconnect();

                    if (redirectCount++ >= MAX_REDIRECTS) {
                        System.out.println("\nError: Too many redirects (" + MAX_REDIRECTS + " max)");
                        return;
                    }

                    System.out.println("\nRedirecting to: " + urlString);
                    continue;
                }

                System.out.println("\nFinal URL: " + urlString);
                System.out.println("Response Code: " + responseCode);

                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String inputLine;
                    StringBuilder response = new StringBuilder();

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine).append("\n");
                    }

                    String contentType = connection.getContentType().split(";")[0].trim();
                    String readableContent;

                    if ("application/json".equals(contentType)) {
                        readableContent = ContentProcessor.formatJson(response.toString());
                    } else {
                        readableContent = ContentProcessor.extractReadableContent(response.toString());
                    }

                    Map<String, String> headers = new HashMap<>();
                    headers.put("Content-Type", contentType);
                    headers.put("ETag", connection.getHeaderField("ETag"));
                    headers.put("Last-Modified", connection.getHeaderField("Last-Modified"));
                    headers.put("Cache-Control", connection.getHeaderField("Cache-Control"));

                    long expirationTime = calculateExpirationTime(headers);
                    cacheManager.addEntry(urlString, new CacheEntry(readableContent, headers, expirationTime));

                    System.out.println("\n"+readableContent);
                    System.out.println("\nCaching URL: " + urlString);
                    System.out.println("Cache-Control: " + headers.get("Cache-Control"));
                    System.out.println("Expires: " + headers.get("Expires"));
                    System.out.println("Calculated Expiration: " + new Date(expirationTime));
                    cacheManager.saveCacheToFile();
                }
                break;
            }
        } catch (SocketTimeoutException ste) {
            System.out.println("\nError: Connection timed out. The server is too slow to respond.");
        } catch (IOException e) {
            System.out.println("\nError fetching URL: " + e.getMessage());
        }
    }

    private static long calculateExpirationTime(Map<String, String> headers) {
        long defaultTTL = 60 * 60 * 1000;
        String cacheControl = headers.get("Cache-Control");
        String expires = headers.get("Expires");

        if (cacheControl != null) {
            if (cacheControl.contains("no-cache") || cacheControl.contains("no-store")) {
                System.out.println("\nCache-Control: no-cache, no-store");
                return 0;
            }
            Pattern maxAgePattern = Pattern.compile("max-age\\s*=\\s*(\\d+)(?:\\s*,|\\s*$)");
            Matcher matcher = maxAgePattern.matcher(cacheControl);
            if (matcher.find()) {
                try {
                    String maxAgeStr = matcher.group(1).trim();
                    long maxAge = Long.parseLong(maxAgeStr) * 1000;
                    if (maxAge <= 0) return 0;
                    return System.currentTimeMillis() + maxAge;
                } catch (NumberFormatException e) {
                    System.err.println("\nInvalid max-age: " + matcher.group(1));
                }
            }
        }

        if (expires != null) {
            try {
                SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
                format.setTimeZone(TimeZone.getTimeZone("GMT"));
                Date expiresDate = format.parse(expires);
                return expiresDate.getTime();
            } catch (Exception e) {
                System.err.println("\nFailed to parse Expires header: " + expires);
            }
        }

        return System.currentTimeMillis() + defaultTTL;
    }

}

