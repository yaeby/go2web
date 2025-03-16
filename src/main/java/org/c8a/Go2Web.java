package org.c8a;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

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
                System.out.println("TODO");
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
            connection.setRequestProperty("User-Agent", "Go2Web/1.0");

            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine).append("\n");
            }
            in.close();

            System.out.println("Response:");
            System.out.println(response.toString());

        } catch (IOException e) {
            System.out.println("Error fetching URL: " + e.getMessage());
        }
    }


}