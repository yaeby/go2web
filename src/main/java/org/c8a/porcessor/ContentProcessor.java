package org.c8a.porcessor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ContentProcessor {

    public static String extractReadableContent(String html) {
        if (html == null || html.trim().isEmpty()) {
            return "No content found";
        }

        StringBuilder result = new StringBuilder();

        try {
            // Parse the HTML using JSoup
            Document doc = Jsoup.parse(html);

            // Remove unwanted elements
            doc.select("script, style, iframe, noscript, svg, [style*=display:none], [hidden]").remove();

            // Extract title
            String title = doc.title();
            if (title != null && !title.trim().isEmpty()) {
                result.append(title).append("\n");
                result.append(String.valueOf('=').repeat(Math.min(title.length(), 40))).append("\n\n");
            }

            // Try to find main content
            Element mainContent = findMainContent(doc);

            if (mainContent != null) {
                processContent(mainContent, result);
            } else {
                // If no main content identified, process the body
                processContent(doc.body(), result);
            }

            // Final cleanup
            String finalResult = result.toString().trim();
            finalResult = finalResult.replaceAll("\n{3,}", "\n\n");

            return finalResult.isEmpty() ? "Could not extract readable content" : finalResult;

        } catch (Exception e) {
            return "Error processing HTML: " + e.getMessage();
        }
    }

    private static Element findMainContent(Document doc) {
        // Common selectors for main content
        String[] contentSelectors = {
                "main", "article", "div.content", "div.article", "div.post",
                "div.main-content", "div#content", "div#main", "div#article",
                "div.entry-content", ".post-content", ".entry", ".article-body"
        };

        for (String selector : contentSelectors) {
            Elements elements = doc.select(selector);
            if (!elements.isEmpty()) {
                // Find the element with the most text content
                Element bestElement = null;
                int maxTextLength = 0;

                for (Element element : elements) {
                    String text = element.text();
                    if (text.length() > maxTextLength) {
                        maxTextLength = text.length();
                        bestElement = element;
                    }
                }

                if (bestElement != null && maxTextLength > 100) {
                    return bestElement;
                }
            }
        }

        // If no suitable element found, try to find the div with most content
        Elements divs = doc.select("div");
        Element bestDiv = null;
        int maxTextLength = 0;

        for (Element div : divs) {
            if (div.select("div").size() < 3) { // Skip containers with many divs
                String text = div.text();
                if (text.length() > maxTextLength) {
                    maxTextLength = text.length();
                    bestDiv = div;
                }
            }
        }

        return (bestDiv != null && maxTextLength > 200) ? bestDiv : null;
    }

    private static void processContent(Element element, StringBuilder result) {
        if (element == null) return;

        // Extract headings
        processHeadings(element, result);

        // Extract paragraphs
        boolean foundParagraphs = processParagraphs(element, result);

        // Extract lists
        processLists(element, result);

        // Extract tables
        processTables(element, result);

        // If no substantial content found, extract text from divs
        if (!foundParagraphs && result.length() < 200) {
            processTextDivs(element, result);
        }

        // If still not enough content, just get all the text
        if (result.length() < 100) {
            String text = element.text();
            if (!text.isEmpty()) {
                result.append(text).append("\n");
            }
        }
    }

    private static void processHeadings(Element element, StringBuilder result) {
        for (int i = 1; i <= 6; i++) {
            Elements headings = element.select("h" + i);
            for (Element heading : headings) {
                String headingText = heading.text().trim();
                if (!headingText.isEmpty()) {
                    result.append(headingText).append("\n");
                    if (i <= 2) {
                        result.append(String.valueOf(i == 1 ? '=' : '-').repeat(Math.min(headingText.length(), 40))).append("\n");
                    }
                    result.append("\n");
                }
            }
        }
    }

    private static boolean processParagraphs(Element element, StringBuilder result) {
        Elements paragraphs = element.select("p");
        boolean foundContent = false;

        for (Element paragraph : paragraphs) {
            String paragraphText = paragraph.text().trim();
            if (!paragraphText.isEmpty() && paragraphText.length() > 10) {
                result.append(paragraphText).append("\n\n");
                foundContent = true;
            }
        }

        return foundContent;
    }

    private static void processLists(Element element, StringBuilder result) {
        // Process unordered lists
        Elements unorderedLists = element.select("ul");
        for (Element ul : unorderedLists) {
            result.append("\n");
            Elements items = ul.select("li");
            for (Element item : items) {
                String itemText = item.text().trim();
                if (!itemText.isEmpty()) {
                    result.append("• ").append(itemText).append("\n");
                }
            }
            result.append("\n");
        }

        // Process ordered lists
        Elements orderedLists = element.select("ol");
        for (Element ol : orderedLists) {
            result.append("\n");
            Elements items = ol.select("li");
            int i = 1;
            for (Element item : items) {
                String itemText = item.text().trim();
                if (!itemText.isEmpty()) {
                    result.append(i++).append(". ").append(itemText).append("\n");
                }
            }
            result.append("\n");
        }
    }

    private static void processTables(Element element, StringBuilder result) {
        Elements tables = element.select("table");
        for (Element table : tables) {
            result.append("\n");
            Elements rows = table.select("tr");

            for (Element row : rows) {
                Elements headerCells = row.select("th");
                Elements dataCells = row.select("td");

                // Handle header rows
                if (!headerCells.isEmpty()) {
                    StringBuilder headerText = new StringBuilder();
                    for (Element cell : headerCells) {
                        headerText.append(cell.text().trim()).append("\t");
                    }
                    String headerLine = headerText.toString().trim();
                    result.append(headerLine).append("\n");
                    result.append(String.valueOf('-').repeat(Math.min(headerLine.length(), 40))).append("\n");
                }
                // Handle data rows
                else if (!dataCells.isEmpty()) {
                    StringBuilder rowText = new StringBuilder();
                    for (Element cell : dataCells) {
                        rowText.append(cell.text().trim()).append("\t");
                    }
                    result.append(rowText.toString().trim()).append("\n");
                }
            }
            result.append("\n");
        }
    }

    private static void processTextDivs(Element element, StringBuilder result) {
        Set<String> processedContents = new HashSet<>();
        Elements divs = element.select("div");

        for (Element div : divs) {
            // Skip divs with certain elements to avoid duplicating content
            if (div.select("p, h1, h2, h3, h4, h5, h6, ul, ol, table").isEmpty()) {
                String divText = div.text().trim();
                if (divText.length() > 40 && !processedContents.contains(divText)) {
                    processedContents.add(divText);
                    result.append(divText).append("\n\n");
                }
            }
        }
    }

    public static String formatJson(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            Object jsonObject = mapper.readValue(json, Object.class);
            return mapper.writeValueAsString(jsonObject);
        } catch (Exception e) {
            return json;
        }
    }
}