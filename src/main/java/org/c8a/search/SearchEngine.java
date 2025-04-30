package org.c8a.search;

import org.c8a.client.CustomHttpClient;
import org.c8a.handler.HttpHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SearchEngine {

    private HttpHandler handler;

    public SearchEngine(HttpHandler handler) {
        this.handler = handler;
    }

    public void search(String[] args) {
        try {
            String searchTerm = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            String encodedSearchTerm = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
            String searchUrl = "https://html.duckduckgo.com/html/";

            // Add required form parameters
            String postData = "q=" + encodedSearchTerm + "&kl=us-en&dt=t";

            CustomHttpClient client = new CustomHttpClient();
            client.setRequestHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/115.0");
            client.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
            client.setRequestHeader("Accept", "text/html");
            client.setRequestHeader("Accept-Language", "en-US,en;q=0.5");
            client.setRequestHeader("Referer", "https://html.duckduckgo.com/");

            CustomHttpClient.HttpResponse response = client.request("POST", searchUrl, postData.getBytes(StandardCharsets.UTF_8));

            // Handle 302 redirect
            if (response.isRedirect()) {
                String newLocation = response.getHeader("Location");
                if (newLocation != null) {
                    response = client.request("GET", newLocation, null);
                }
            }

            int responseCode = response.getStatusCode();
            if (responseCode != 200) {
                System.out.println("\nError: Could not complete search. Response code: " + responseCode);
                return;
            }

            String html = response.getBodyAsString();

            List<String> searchResults = extractResults(html);
            System.out.println("\nTop " + Math.min(10, searchResults.size()) + " search results for: " + searchTerm);

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
                    System.out.println("\nFetching URL: " + selectedUrl);
                    handler.fetchURL(selectedUrl);
                } else if (selection != 0) {
                    System.out.println("\nInvalid selection: " + selection);
                }
            } catch (NumberFormatException e) {
                System.out.println("\nInvalid input. Please enter a number.");
            }

        } catch (IOException e) {
            System.out.println("\nError during search: " + e.getMessage());
        }
    }

    private static List<String> extractResults(String html) {
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
