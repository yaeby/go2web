package org.c8a.cache;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.FileOutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class CacheManager {
    private final Map<String, CacheEntry> cache = new HashMap<>();
    private static final String CACHE_FILE = "go2web_cache.dat";

    public CacheManager() {
        loadCacheFromFile();
    }

    public CacheEntry getEntry(String url) {
        CacheEntry entry = cache.get(url);
        return (entry != null && !entry.isExpired()) ? entry : null;
    }

    public void addEntry(String url, CacheEntry entry) {
        cache.put(url, entry);
    }

    private void loadCacheFromFile() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(CACHE_FILE))) {
            @SuppressWarnings("unchecked")
            Map<String, CacheEntry> loadedCache = (Map<String, CacheEntry>) ois.readObject();

            loadedCache.entrySet().removeIf(entry -> entry.getValue().isExpired());

            cache.putAll(loadedCache);
            System.out.println("\nLoaded " + loadedCache.size() + " cache entries from disk");
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("\nError loading cache: " + e.getMessage());
        }
    }


    public void saveCacheToFile() {

        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(CACHE_FILE))) {
            oos.writeObject(cache);
            System.out.println("\nSaved " + cache.size() + " cache entries to disk");
            System.out.println("Cache entries being saved:");
            cache.forEach((url, entry) -> {
                long remainingMillis = entry.expirationTime() - System.currentTimeMillis();
                String remainingTime = formatDuration(remainingMillis);
                System.out.println(url + " | Expires: " + new Date(entry.expirationTime())
                        + " (in " + remainingTime + ")");
            });
        } catch (IOException e) {
            System.err.println("\nError saving cache: " + e.getMessage());
        }
    }

    private static String formatDuration(long millis) {
        if (millis <= 0) return "EXPIRED";
        long seconds = millis / 1000;
        long days = seconds / 86400; seconds %= 86400;
        long hours = seconds / 3600; seconds %= 3600;
        long minutes = seconds / 60; seconds %= 60;
        return String.format("%dd %dh %dm %ds", days, hours, minutes, seconds);
    }
}