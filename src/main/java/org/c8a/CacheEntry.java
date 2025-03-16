package org.c8a;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class CacheEntry {
    private final String content;
    private final Map<String, String> headers;
    private final long expirationTime; // Time when the entry expires (milliseconds)

    public CacheEntry(String content, Map<String, String> headers, long expirationTime) {
        this.content = content;
        this.headers = headers;
        this.expirationTime = expirationTime;
    }

    public String getContent() { return content; }
    public Map<String, String> getHeaders() { return headers; }
    public boolean isExpired() { return System.currentTimeMillis() > expirationTime; }
}

