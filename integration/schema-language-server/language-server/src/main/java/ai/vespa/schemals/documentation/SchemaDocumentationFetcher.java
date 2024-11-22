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

/**
 * SchemaDocumentationFetcher
 */
public class SchemaDocumentationFetcher extends ContentFetcher {
    // Store html content as well as id of h2 element. The id is used to append a read more link at the end.
    private record HTMLContentEntry(StringBuilder htmlContent, String h2ID) { }

    private final static Set<String> IGNORE_H2_IDS = Set.of(
        "syntax",
        "elements",
        "document-and-search-field-types",
        "modifying-schemas"
    );

	SchemaDocumentationFetcher(String relativeFileUrl) {
		super(relativeFileUrl);
	}


	@Override
	Map<String, String> getMarkdownContent() throws IOException {
        Document schemaDoc = Jsoup.connect(ContentFetcher.URL_PREFIX + this.fileUrl).get();

        Element prevH2 = null;

        Map<String, HTMLContentEntry> htmlContents = new HashMap<>();

        for (Node nodeIterator = schemaDoc.selectFirst("h2#schema"); nodeIterator != null; nodeIterator = nodeIterator.nextSibling()) {
            Element element = null;
            if (nodeIterator instanceof Element) {
                element = (Element)nodeIterator;

                if (element.tag().equals(Tag.valueOf("h2"))) {
                    if (!IGNORE_H2_IDS.contains(element.id())) {
                        prevH2 = element;
                    } else {
                        prevH2 = null;
                    }
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
                // The tables in the docs are inherently problematic 
                // so we just replace the first table and everything after with "read more"
                prevH2 = null;
                continue;
            }

            currentBuilder.append(element.outerHtml());
        }

        Map<String, String> result = new HashMap<>();

        FlexmarkHtmlConverter converter = this.getHtmlParser();

        for (var entry : htmlContents.entrySet()) {
            StringBuilder htmlContent = entry.getValue().htmlContent();
            String h2id = entry.getValue().h2ID();

            URI readMoreLink = URI.create(ContentFetcher.URL_PREFIX).resolve(fileUrl).resolve("#" + h2id);
            htmlContent.append("<a href=\"" + readMoreLink + "\">Read more</a>");

            String md = converter.convert(htmlContent.toString());

            // Edge case occuring at "bolding" html, don't know why.
            md = md.replaceAll("````\n", "");

            result.put(entry.getKey(), md);
        }
        return result;
	}
}
