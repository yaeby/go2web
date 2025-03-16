package org.c8a;


public class Go2Web {

    public static void main(String[] args) {
        System.out.println("Hello World");
        if (args.length < 1) {
            showHelp();
            return;
        }

        String flag = args[0];

    }

    private static void showHelp() {
        System.out.println("Usage:");
        System.out.println("go2web -u <URL>         # make an HTTP request to the specified URL and print the response");
        System.out.println("go2web -s <search-term> # make an HTTP request to search the term using your favorite search engine and print top 10 results");
        System.out.println("go2web -h               # show this help");
    }


}