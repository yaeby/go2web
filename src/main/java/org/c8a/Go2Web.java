package org.c8a;

import org.c8a.cache.CacheManager;
import org.c8a.handler.HttpHandler;
import org.c8a.search.SearchEngine;

public class Go2Web {

    public static void main(String[] args) {
        CacheManager cacheManager = new CacheManager();
        HttpHandler httpHandler = new HttpHandler(cacheManager);
        SearchEngine searchEngine = new SearchEngine(httpHandler);

        if (args.length < 1) {
            showHelp();
            return;
        }

        switch (args[0]) {
            case "-u":
                handleUrlRequest(httpHandler, args);
                break;
            case "-s":
                handleSearchRequest(searchEngine, args);
                break;
            case "-h":
                showHelp();
                break;
            default:
                System.out.println("Unknown option: " + args[0]);
                showHelp();
        }
    }

    private static void handleUrlRequest(HttpHandler handler, String[] args) {
        if (args.length < 2) {
            System.out.println("URL required with -u");
            return;
        }
        handler.fetchURL(args[1]);
    }

    private static void handleSearchRequest(SearchEngine engine, String[] args) {
        if (args.length < 2) {
            System.out.println("Search term required with -s");
            return;
        }
        engine.search(args);
    }

    private static void showHelp() {
        System.out.println("Usage:");
        System.out.println("go2web -u <URL>         # make an HTTP request to the specified URL and print the response");
        System.out.println("go2web -s <search-term> # make an HTTP request to search the term using your favorite search engine and print top 10 results");
        System.out.println("go2web -h               # show this help");
    }
}