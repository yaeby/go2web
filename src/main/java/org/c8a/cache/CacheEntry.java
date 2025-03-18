package org.c8a.cache;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

public record CacheEntry(String content, Map<String, String> headers, long expirationTime) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public boolean isExpired() {
        return System.currentTimeMillis() > expirationTime;
    }
}

