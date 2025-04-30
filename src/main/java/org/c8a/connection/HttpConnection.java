package org.c8a.connection;

import org.c8a.cache.CacheEntry;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpConnection {

    public static HttpURLConnection getHttpURLConnection(String urlString, CacheEntry cached) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        if (cached != null) {
            String etag = cached.headers().get("ETag");
            String lastModified = cached.headers().get("Last-Modified");
            if (etag != null) connection.setRequestProperty("If-None-Match", etag);
            if (lastModified != null) connection.setRequestProperty("If-Modified-Since", lastModified);
        }

        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
        connection.setRequestProperty("Accept", "application/json, text/html;q=0.9, application/xhtml+xml;q=0.8, application/xml;q=0.7");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
        return connection;
    }

    public static HttpURLConnection getHttpURLConnection(String encodedSearchTerm) throws IOException {
        String searchUrl = "https://html.duckduckgo.com/html/?q=" + encodedSearchTerm;

        URL url = new URL(searchUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/98.0.4758.102 Safari/537.36");
        connection.setRequestProperty("Accept", "application/json, text/html;q=0.9, application/xhtml+xml;q=0.8, application/xml;q=0.7");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
        connection.setRequestProperty("Connection", "keep-alive");
        return connection;
    }
}