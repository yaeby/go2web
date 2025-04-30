package org.c8a.search;

import org.c8a.connection.HttpConnection;
import org.c8a.handler.HttpHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
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
            HttpURLConnection connection = HttpConnection.getHttpURLConnection(encodedSearchTerm);

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                System.out.println("\nError: Could not complete search. Response code: " + responseCode);
                return;
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            List<String> searchResults = extractResults(response.toString());
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