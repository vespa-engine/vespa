package ai.vespa.schemals.documentation;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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

	SchemaDocumentationFetcher(String relativeFileUrl) {
		super(relativeFileUrl);
	}

	@Override
	Map<String, String> getMarkdownContent() throws IOException {
        Document schemaDoc = Jsoup.connect(ContentFetcher.URL_PREFIX + this.fileUrl).get();

        Element prevH2 = null;

        Node nodeIterator = schemaDoc.selectFirst("h2#schema");

        Map<String, StringBuilder> htmlContents = new HashMap<>();

        for (; nodeIterator != null; nodeIterator = nodeIterator.nextSibling()) {
            Element element = null;
            if (nodeIterator instanceof Element) {
                element = (Element)nodeIterator;

                if (element.tag().equals(Tag.valueOf("h2"))) {
                    if (!element.id().equals("syntax")
                        && !element.id().equals("elements")) {
                        prevH2 = element;
                    } else {
                        prevH2 = null;
                    }
                }
            }
            if (prevH2 == null) continue;

            String contentKey = prevH2.text();

            if (!htmlContents.containsKey(contentKey)) {
                htmlContents.put(contentKey, new StringBuilder().append(prevH2.outerHtml()));
                continue;
            }
            StringBuilder currentBuilder = htmlContents.get(contentKey);

            currentBuilder.append("\n");

            if (element == null) {
                if (!nodeIterator.toString().isBlank()) 
                    currentBuilder.append(nodeIterator.toString());
                continue;
            }

            if (element.tag().equals(Tag.valueOf("table"))) {
                Element tbody = element.selectFirst("tbody");
                // replace all <th> in tbody with <td>
                tbody.select("th").tagName("td");

                // some tables have very big texts in td. For our purposes, only keep the first sentence.
                if (prevH2.id().equals("field"))
                    manuallyFixFieldTable(tbody);
            }

            currentBuilder.append(element.outerHtml());
        }

        Map<String, String> result = new HashMap<>();

        FlexmarkHtmlConverter converter = this.getHtmlParser();

        for (var entry : htmlContents.entrySet()) {
            String md = converter.convert(entry.getValue().toString());

            // Edge case occuring at "bolding" html, don't know why.
            md = md.replaceAll("````\n", "");

            result.put(entry.getKey(), md);
        }
        return result;
	}

    private static void manuallyFixFieldTable(Element tbodyElement) {
        for (Element td : tbodyElement.select("tr td:nth-child(2)")) {
            String curr = td.html();
            int level = 0;
            int i;
            for (i = 0; i < curr.length(); ++i) {
                if ((
                    (curr.charAt(i) == '.' && !curr.substring(i-1, Math.min(curr.length(), i+3)).equals("i.e.") && !curr.substring(i-3,i+1).equals("i.e."))
                    || curr.substring(i).startsWith("<code>") 
                    || curr.substring(i).startsWith("<pre>") 
                    || curr.charAt(i) == ':') && level == 0) {
                    break;
                }
                if (curr.charAt(i) == '(')++level;
                if (curr.charAt(i) == ')')--level;
                if (curr.charAt(i) == '<')++level;
                if (curr.charAt(i) == '>')--level;
            }
            String firstSentence = curr.substring(0, i) + ".";
            td.html(firstSentence);
        }
    }
    
}
