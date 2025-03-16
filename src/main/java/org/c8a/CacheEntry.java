package org.c8a;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

public class CacheEntry implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final String content;
    private final Map<String, String> headers;
    private final long expirationTime;

    public CacheEntry(String content, Map<String, String> headers, long expirationTime) {
        this.content = content;
        this.headers = headers;
        this.expirationTime = expirationTime;
    }

    public String getContent() { return content; }
    public Map<String, String> getHeaders() { return headers; }
    public long getExpirationTime() { return expirationTime; }
    public boolean isExpired() { return System.currentTimeMillis() > expirationTime; }
}

