package org.c8a.porcessor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ContentProcessor {

    public static String extractReadableContent(String html) {
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
                if (!divContent.contains("<div")) {
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
                    if (!divContent.contains("<div")) {
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