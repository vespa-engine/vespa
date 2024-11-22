package ai.vespa.schemals.documentation;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Tag;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;

public class ServicesDocumentationFetcher extends ContentFetcher {
    // Store html content as well as id of h2 element. The id is used to append a read more link at the end.
    private record HTMLContentEntry(StringBuilder htmlContent, String h2ID) { }

    private String subPageTitle;

    ServicesDocumentationFetcher(String relativeFileUrl, String subPageTitle) {
        super(relativeFileUrl);
        if (subPageTitle.contains("/")) {
            subPageTitle = subPageTitle.substring(subPageTitle.indexOf('/')+1);
        }
        this.subPageTitle = subPageTitle;
    }

    @Override
    Map<String, String> getMarkdownContent() throws IOException {
        /*
         * Documentation for services.xml consists of multiple pages.
         * Each page has the structure:
         * "This page describes section X of services.xml
         * <pre>
         *  ...tree-like structure of this section
         * </pre>
         *
         * OPTIONAL description of current sub-parent in services.xml, for example <document-processing>. Here referred to as subPageTitle
         *
         * <h2>sub-tag in services.xml</h2>
         * CONTENT...
         *
         * <h2> next tag</h2>
         * ...
         */

        Document schemaDoc = Jsoup.connect(ContentFetcher.URL_PREFIX + this.fileUrl).get();

        // Map title -> (content, id)
        // content is HTML content
        // id is used to create read more links with hash
        Map<String, HTMLContentEntry> htmlContents = new HashMap<>();

        Element firstH2 = schemaDoc.selectFirst("h2");

        // Add page description as its own markdown file if it exists
        if (!subPageTitle.isEmpty()) {
            Node firstPre = schemaDoc.selectFirst("pre").nextSibling();

            while (!(firstPre instanceof Element))firstPre = firstPre.nextSibling();

            if (!firstPre.equals(firstH2)) {
                HTMLContentEntry subPageDocumentation = processPageDescription(firstPre, firstH2);
                htmlContents.put(subPageTitle, subPageDocumentation);
            }
        }

        // Process the rest of the tag descriptions
        Element prevH2 = null;
        for (Node nodeIterator = firstH2; nodeIterator != null; nodeIterator = nodeIterator.nextSibling()) {
            Element element = null;
            if (nodeIterator instanceof Element) {
                element = (Element)nodeIterator;

                if (element.tag().equals(Tag.valueOf("h2"))) {
                    prevH2 = element;
                }
            }
            if (prevH2 == null) continue;

            String contentKey = prevH2.text();

            if (!htmlContents.containsKey(contentKey)) {
                htmlContents.put(contentKey,
                    new HTMLContentEntry(new StringBuilder().append(prevH2.outerHtml()), prevH2.id())
                );
                continue;
            }
            StringBuilder currentBuilder = htmlContents.get(contentKey).htmlContent();

            currentBuilder.append("\n");

            if (element == null) {
                if (!nodeIterator.toString().isBlank()) 
                    currentBuilder.append(nodeIterator.toString());
                continue;
            }
            if (element.tag().equals(Tag.valueOf("table"))) {
                // tables are inherently problematic so we just replace everything after the first table with "read more"
                prevH2 = null;
                continue;
            }

            currentBuilder.append(getElementHTML(element));
        }

        Map<String, String> result = new HashMap<>();

        // Convert all HTML to Markdown, also add a Read more link to everything
        FlexmarkHtmlConverter converter = this.getHtmlParser();
        for (var entry : htmlContents.entrySet()) {
            StringBuilder htmlContent = entry.getValue().htmlContent();
            String h2id = entry.getValue().h2ID();

            URI readMoreLink = URI.create(ContentFetcher.URL_PREFIX).resolve(fileUrl);
            readMoreLink = readMoreLink.resolve("#" + h2id);

            htmlContent.append("<a href=\"" + readMoreLink + "\">Read more</a>");

            String md = converter.convert(htmlContent.toString());

            // Edge case occuring at "bolding" html, don't know why.
            md = md.replaceAll("````\n", "");

            result.put(entry.getKey(), md);
        }
        return result;
    }

    private HTMLContentEntry processPageDescription(Node descriptionStart, Node firstH2) {
        StringBuilder firstContent = new StringBuilder();

        if (!(descriptionStart instanceof Element) || !((Element)descriptionStart).tagName().equals("h2")) {
            firstContent.append("<h2>")
                        .append(subPageTitle)
                        .append("</h2>\n");
        }

        while (!descriptionStart.equals(firstH2)) {
            if (descriptionStart instanceof Element) {
                Element element = (Element)descriptionStart;
                firstContent.append(getElementHTML(element));
            }
            descriptionStart = descriptionStart.nextSibling();
        }

        return new HTMLContentEntry(firstContent, "");
    }

    private String getElementHTML(Element element) {
        if (element.tag().equals(Tag.valueOf("table"))) {
            Element tbody = element.selectFirst("tbody");
            // replace all <th> in tbody with <td>
            tbody.select("th").tagName("td");
        }
        return element.outerHtml();
    }
}
