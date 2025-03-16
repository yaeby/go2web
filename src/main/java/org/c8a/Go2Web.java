package org.c8a;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Go2Web {

    public static void main(String[] args) {
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
                String searchTerm = args[1];
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

    private static void showHelp() {
        System.out.println("Usage:");
        System.out.println("./go2web -u <URL>         # make an HTTP request to the specified URL and print the response");
        System.out.println("./go2web -s <search-term> # make an HTTP request to search the term using your favorite search engine and print top 10 results");
        System.out.println("./go2web -h               # show this help");
    }

    private static void fetchURL(String urlString) {
        try {
            if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                urlString = "https://" + urlString;
            }

            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/98.0.4758.102 Safari/537.36");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");

            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine).append("\n");
            }
            in.close();

            String htmlContent = response.toString();
            String readableContent = extractReadableContent(htmlContent);

            System.out.println("Human-Readable Content:");
            System.out.println(readableContent);

        } catch (IOException e) {
            System.out.println("Error fetching URL: " + e.getMessage());
        }
    }

    private static String extractReadableContent(String html) {
        String noScript = html.replaceAll("<script[^>]*>[\\s\\S]*?</script>", "");
        String noStyle = noScript.replaceAll("<style[^>]*>[\\s\\S]*?</style>", "");

        String noComments = noStyle.replaceAll("<!--[\\s\\S]*?-->", "");
        String noTags = noComments.replaceAll("<[^>]+>", " ");
        String singleSpaced = noTags.replaceAll("\\s+", " ");
        String cleanedText = singleSpaced.replaceAll("\\n+", "\n");
        cleanedText = cleanedText.trim();

        return cleanedText;
    }

    private static void searchDuckDuckGo(String searchTerm) {
        try {
            String encodedSearchTerm = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8.toString());
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