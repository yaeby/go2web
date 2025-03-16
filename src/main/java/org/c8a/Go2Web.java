package org.c8a;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Go2Web {
    private static final Map<String, CacheEntry> cache = new HashMap<>();
    private static final String CACHE_FILE = "go2web_cache.dat";

    static {
        loadCacheFromFile();
    }

    public static void main(String[] args) {

        Runtime.getRuntime().addShutdownHook(new Thread(Go2Web::saveCacheToFile));

        if (args.length < 1) {
            showHelp();
            return;
        }

        String flag = args[0];

        switch (flag) {
            case "-u":
                if (args.length < 2) {
                    System.out.println("Error: URL is required with -u flag");
                    showHelp();
                    return;
                }
                String url = args[1];
                fetchURL(url);
                break;

            case "-s":
                if (args.length < 2) {
                    System.out.println("Error: Search term is required with -s flag");
                    showHelp();
                    return;
                }
                String searchTerm = String.join("+", Arrays.copyOfRange(args, 1, args.length));
                searchDuckDuckGo(searchTerm);
                break;

            case "-h":
                showHelp();
                break;

            default:
                System.out.println("Error: Unknown flag: " + flag);
                showHelp();
        }
    }

    private static void loadCacheFromFile() {
        File file = new File(CACHE_FILE);
        if (!file.exists()) return;

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(CACHE_FILE))) {
            @SuppressWarnings("unchecked")
            Map<String, CacheEntry> loadedCache = (Map<String, CacheEntry>) ois.readObject();

            loadedCache.entrySet().removeIf(entry -> entry.getValue().isExpired());

            cache.putAll(loadedCache);
            System.out.println("Loaded " + loadedCache.size() + " cache entries from disk");
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error loading cache: " + e.getMessage());
        }
    }

    private static void saveCacheToFile() {
        Map<String, CacheEntry> cacheCopy = new HashMap<>(cache);

        cacheCopy.entrySet().removeIf(entry -> entry.getValue().isExpired());

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(CACHE_FILE))) {
            oos.writeObject(cacheCopy);
            System.out.println("Saved " + cacheCopy.size() + " cache entries to disk");
        } catch (IOException e) {
            System.err.println("Error saving cache: " + e.getMessage());
        }
    }

    private static void showHelp() {
        System.out.println("Usage:");
        System.out.println("./go2web -u <URL>         # make an HTTP request to the specified URL and print the response");
        System.out.println("./go2web -s <search-term> # make an HTTP request to search the term using your favorite search engine and print top 10 results");
        System.out.println("./go2web -h               # show this help");
    }

    private static void fetchURL(String urlString) {
        final int MAX_REDIRECTS = 5;
        int redirectCount = 0;

        try {
            if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                urlString = "https://" + urlString;
            }

            while (true) {
                CacheEntry cached = cache.get(urlString);
                if (cached != null && !cached.isExpired()) {
                    System.out.println("Serving from cache:");
                    System.out.println(cached.getContent());
                    return;
                }

                HttpURLConnection connection = getHttpURLConnection(urlString, cached);

                int responseCode = connection.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    System.out.println("Resource not modified. Serving from cache:");
                    System.out.println(cached.getContent());
                    return;
                }

                if (responseCode >= 300 && responseCode < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isEmpty()) {
                        System.out.println("Error: Redirect requested but no Location header found");
                        return;
                    }

                    URL base = new URL(urlString);
                    URL resolvedUrl = new URL(base, location);
                    urlString = resolvedUrl.toString();

                    connection.disconnect();

                    if (redirectCount++ >= MAX_REDIRECTS) {
                        System.out.println("Error: Too many redirects (" + MAX_REDIRECTS + " max)");
                        return;
                    }

                    System.out.println("Redirecting to: " + urlString);
                    continue;
                }

                System.out.println("Final URL: " + urlString);
                System.out.println("Response Code: " + responseCode);

                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String inputLine;
                    StringBuilder response = new StringBuilder();

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine).append("\n");
                    }

                    String htmlContent = response.toString();
                    String readableContent = extractReadableContent(htmlContent);

                    Map<String, String> headers = new HashMap<>();
                    headers.put("ETag", connection.getHeaderField("ETag"));
                    headers.put("Last-Modified", connection.getHeaderField("Last-Modified"));
                    headers.put("Cache-Control", connection.getHeaderField("Cache-Control"));

                    long expirationTime = calculateExpirationTime(headers);
                    cache.put(urlString, new CacheEntry(readableContent, headers, expirationTime));

                    System.out.println(readableContent);
                }

                break;
            }
        } catch (SocketTimeoutException ste) {
            System.out.println("Error: Connection timed out. The server is too slow to respond.");
        } catch (IOException e) {
            System.out.println("Error fetching URL: " + e.getMessage());
        }
    }

    private static HttpURLConnection getHttpURLConnection(String urlString, CacheEntry cached) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        if (cached != null) {
            String etag = cached.getHeaders().get("ETag");
            String lastModified = cached.getHeaders().get("Last-Modified");
            if (etag != null) connection.setRequestProperty("If-None-Match", etag);
            if (lastModified != null) connection.setRequestProperty("If-Modified-Since", lastModified);
        }

        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
        return connection;
    }

    private static long calculateExpirationTime(Map<String, String> headers) {
        long defaultTTL = 60 * 60 * 1000;
        String cacheControl = headers.get("Cache-Control");
        String expires = headers.get("Expires");

        if (cacheControl != null) {
            if (cacheControl.contains("no-cache") || cacheControl.contains("no-store")) {
                System.out.println("Cache-Control: no-cache, no-store");
                return 0;
            }
            Pattern maxAgePattern = Pattern.compile("max-age\\s*=\\s*(\\d+)");
            Matcher matcher = maxAgePattern.matcher(cacheControl);
            if (matcher.find()) {
                try {
                    long maxAge = Long.parseLong(matcher.group(1)) * 1000;
                    if (maxAge <= 0) return 0; // Handle max-age=0
                    return System.currentTimeMillis() + maxAge;
                } catch (NumberFormatException e) {
                    System.err.println("Invalid max-age: " + matcher.group(1));
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
                System.err.println("Failed to parse Expires header: " + expires);
            }
        }

        return System.currentTimeMillis() + defaultTTL;
    }

    private static String extractReadableContent(String html) {
        StringBuilder result = new StringBuilder();

        String cleanHtml = html.replaceAll("<script[^>]*>[\\s\\S]*?</script>", "");
        cleanHtml = cleanHtml.replaceAll("<style[^>]*>[\\s\\S]*?</style>", "");
        cleanHtml = cleanHtml.replaceAll("<nav[^>]*>[\\s\\S]*?</nav>", "");
        cleanHtml = cleanHtml.replaceAll("<footer[^>]*>[\\s\\S]*?</footer>", "");
        cleanHtml = cleanHtml.replaceAll("<header[^>]*>[\\s\\S]*?</header>", "");
        cleanHtml = cleanHtml.replaceAll("<!--[\\s\\S]*?-->", "");

        Pattern titlePattern = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.DOTALL);
        Matcher titleMatcher = titlePattern.matcher(cleanHtml);
        if (titleMatcher.find()) {
            String title = cleanText(titleMatcher.group(1));
            if (!title.isBlank()) {
                result.append(title).append("\n");
                result.append(String.valueOf('=').repeat(Math.min(title.length(), 40))).append("\n\n");
            }
        }

        Pattern mainPattern = Pattern.compile("<main[^>]*>(.*?)</main>", Pattern.DOTALL);
        Matcher mainMatcher = mainPattern.matcher(cleanHtml);
        if (mainMatcher.find()) {
            cleanHtml = mainMatcher.group(1);
        } else {
            Pattern articlePattern = Pattern.compile("<article[^>]*>(.*?)</article>", Pattern.DOTALL);
            Matcher articleMatcher = articlePattern.matcher(cleanHtml);
            if (articleMatcher.find()) {
                cleanHtml = articleMatcher.group(1);
            }
        }

        for (int i = 1; i <= 6; i++) {
            Pattern hPattern = Pattern.compile("<h" + i + "[^>]*>(.*?)</h" + i + ">", Pattern.DOTALL);
            Matcher hMatcher = hPattern.matcher(cleanHtml);
            while (hMatcher.find()) {
                String heading = cleanText(hMatcher.group(1));
                if (!heading.isEmpty() && !heading.trim().isEmpty()) {
                    result.append(heading).append("\n");
                    if (i <= 2) {
                        result.append(String.valueOf(i == 1 ? '=' : '-').repeat(Math.min(heading.length(), 40))).append("\n");
                    }
                    result.append("\n");
                }
            }
        }

        Pattern pPattern = Pattern.compile("<p[^>]*>(.*?)</p>", Pattern.DOTALL);
        Matcher pMatcher = pPattern.matcher(cleanHtml);
        while (pMatcher.find()) {
            String paragraph = cleanText(pMatcher.group(1));
            if (!paragraph.isEmpty() && !paragraph.trim().isEmpty()) {
                result.append(paragraph).append("\n\n");
            }
        }

        extractLists(cleanHtml, result);
        extractTables(cleanHtml, result);

        if (result.length() < 200) {
            Set<String> processedContents = new HashSet<>();
            Pattern contentDivPattern = Pattern.compile("<div[^>]*class=[\"'][^\"']*(?:content|article|text|body)[^\"']*[\"'][^>]*>(.*?)</div>", Pattern.DOTALL);
            Matcher contentDivMatcher = contentDivPattern.matcher(cleanHtml);

            while (contentDivMatcher.find()) {
                String divContent = contentDivMatcher.group(1);
                if (!divContent.contains("<div")) {  // Skip nested divs
                    String cleaned = cleanText(divContent);
                    if (!cleaned.isEmpty() && !cleaned.trim().isEmpty() && !processedContents.contains(cleaned)) {
                        processedContents.add(cleaned);
                        result.append(cleaned).append("\n\n");
                    }
                }
            }

            if (result.length() < 200) {
                Pattern divPattern = Pattern.compile("<div[^>]*>(.*?)</div>", Pattern.DOTALL);
                Matcher divMatcher = divPattern.matcher(cleanHtml);

                while (divMatcher.find() && result.length() < 1000) {
                    String divContent = divMatcher.group(1);
                    if (!divContent.contains("<div")) {  // Skip nested divs
                        String cleaned = cleanText(divContent);
                        if (!cleaned.isEmpty() && cleaned.trim().length() > 40 && !processedContents.contains(cleaned)) {
                            processedContents.add(cleaned);
                            result.append(cleaned).append("\n\n");
                        }
                    }
                }
            }
        }

        if (result.length() < 100) {
            Pattern bodyPattern = Pattern.compile("<body[^>]*>(.*?)</body>", Pattern.DOTALL);
            Matcher bodyMatcher = bodyPattern.matcher(html);
            if (bodyMatcher.find()) {
                String bodyContent = cleanText(bodyMatcher.group(1));
                if (!bodyContent.isEmpty() && !bodyContent.trim().isEmpty()) {
                    result.append(bodyContent);
                }
            }
        }

        String finalResult = result.toString().trim();

        // Replacing multiple consecutive newlines
        finalResult = finalResult.replaceAll("\n{3,}", "\n\n");

        return finalResult;
    }

    private static void extractLists(String html, StringBuilder result) {
        Pattern ulPattern = Pattern.compile("<ul[^>]*>(.*?)</ul>", Pattern.DOTALL);
        Matcher ulMatcher = ulPattern.matcher(html);

        while (ulMatcher.find()) {
            String ulContent = ulMatcher.group(1);
            result.append("\n");

            Pattern liPattern = Pattern.compile("<li[^>]*>(.*?)</li>", Pattern.DOTALL);
            Matcher liMatcher = liPattern.matcher(ulContent);

            while (liMatcher.find()) {
                String item = cleanText(liMatcher.group(1));
                if (!item.isEmpty() && !item.trim().isEmpty()) {
                    result.append("• ").append(item).append("\n");
                }
            }
            result.append("\n");
        }

        Pattern olPattern = Pattern.compile("<ol[^>]*>(.*?)</ol>", Pattern.DOTALL);
        Matcher olMatcher = olPattern.matcher(html);

        while (olMatcher.find()) {
            String olContent = olMatcher.group(1);
            result.append("\n");

            Pattern liPattern = Pattern.compile("<li[^>]*>(.*?)</li>", Pattern.DOTALL);
            Matcher liMatcher = liPattern.matcher(olContent);

            int itemNumber = 1;
            while (liMatcher.find()) {
                String item = cleanText(liMatcher.group(1));
                if (!item.isEmpty() && !item.trim().isEmpty()) {
                    result.append(itemNumber++).append(". ").append(item).append("\n");
                }
            }
            result.append("\n");
        }
    }

    private static void extractTables(String html, StringBuilder result) {
        Pattern tablePattern = Pattern.compile("<table[^>]*>(.*?)</table>", Pattern.DOTALL);
        Matcher tableMatcher = tablePattern.matcher(html);

        while (tableMatcher.find()) {
            String tableContent = tableMatcher.group(1);
            result.append("\n");

            Pattern trPattern = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.DOTALL);
            Matcher trMatcher = trPattern.matcher(tableContent);

            while (trMatcher.find()) {
                String rowContent = trMatcher.group(1);
                StringBuilder rowText = new StringBuilder();

                Pattern thPattern = Pattern.compile("<th[^>]*>(.*?)</th>", Pattern.DOTALL);
                Matcher thMatcher = thPattern.matcher(rowContent);
                boolean isHeader = thMatcher.find();

                if (isHeader) {
                    do {
                        String cell = cleanText(thMatcher.group(1));
                        rowText.append(cell).append("\t");
                    } while (thMatcher.find());

                    result.append(rowText.toString().trim()).append("\n");
                    result.append(String.valueOf('-').repeat(Math.min(rowText.length(), 40))).append("\n");
                } else {
                    Pattern tdPattern = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL);
                    Matcher tdMatcher = tdPattern.matcher(rowContent);

                    while (tdMatcher.find()) {
                        String cell = cleanText(tdMatcher.group(1));
                        rowText.append(cell).append("\t");
                    }

                    result.append(rowText.toString().trim()).append("\n");
                }
            }

            result.append("\n");
        }
    }

    private static String cleanText(String text) {
        String noTags = text.replaceAll("<[^>]+>", " ");

        String decoded = noTags.replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&")
                .replaceAll("&quot;", "\"")
                .replaceAll("&apos;", "'")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&#39;", "'")
                .replaceAll("&#34;", "\"")
                .replaceAll("&#160;", " ")
                .replaceAll("&ldquo;", "\"")
                .replaceAll("&rdquo;", "\"")
                .replaceAll("&lsquo;", "'")
                .replaceAll("&rsquo;", "'")
                .replaceAll("&mdash;", "—")
                .replaceAll("&ndash;", "-")
                .replaceAll("&hellip;", "...");

        return decoded.replaceAll("\\s+", " ").trim();
    }

    private static void searchDuckDuckGo(String searchTerm) {
        try {
            String encodedSearchTerm = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
            String searchUrl = "https://html.duckduckgo.com/html/?q=" + encodedSearchTerm;

            URL url = new URL(searchUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/98.0.4758.102 Safari/537.36");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
            connection.setRequestProperty("Connection", "keep-alive");

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                System.out.println("Error: Could not complete search. Response code: " + responseCode);
                return;
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            List<String> searchResults = extractDuckDuckGoResults(response.toString());
            System.out.println("Top " + Math.min(10, searchResults.size()) + " search results for: " + searchTerm);

            for (int i = 0; i < Math.min(10, searchResults.size()); i++) {
                System.out.println((i + 1) + ". " + searchResults.get(i));
            }

            if (searchResults.isEmpty()) {
                System.out.println("No results found or could not parse results properly.");
                return;
            }

            System.out.println("\nEnter a number (1-" + Math.min(10, searchResults.size()) + ") to fetch that URL, or 0 to go exit: ");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String input = reader.readLine();

            try {
                int selection = Integer.parseInt(input.trim());
                if (selection > 0 && selection <= searchResults.size()) {
                    String selectedUrl = searchResults.get(selection - 1);
                    System.out.println("Fetching URL: " + selectedUrl);
                    fetchURL(selectedUrl);
                } else if (selection != 0) {
                    System.out.println("Invalid selection: " + selection);
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number.");
            }

        } catch (IOException e) {
            System.out.println("Error during search: " + e.getMessage());
        }
    }

    private static List<String> extractDuckDuckGoResults(String html) {
        List<String> results = new ArrayList<>();

        Pattern pattern = Pattern.compile("<a class=\"result__url\" href=\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(html);

        while (matcher.find() && results.size() < 10) {
            String url = matcher.group(1);
            if (url.startsWith("//")) {
                url = "https:" + url;
            }
            results.add(url);
        }

        return results;
    }
}