package ai.vespa.schemals.documentation;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;

/**
 * RankfeatureDocumentationFetcher
 */
public class RankFeatureDocumentationFetcher extends ContentFetcher {

	RankFeatureDocumentationFetcher(String relativeFileUrl) {
		super(relativeFileUrl);
	}

	@Override
	Map<String, String> getMarkdownContent() throws IOException {
        Document document = Jsoup.connect(ContentFetcher.URL_PREFIX + this.fileUrl).get();
        Element tableElement = document.selectFirst("table.table");

        Elements trs = tableElement.select("tr:has(> td:nth-child(3)):not(:has(> td:nth-child(4)))");

        Map<String, String> result = new HashMap<>();

        FlexmarkHtmlConverter converter = this.getHtmlParser();

        for (Element tr : trs) {
            String name = tr.child(0).text();
            name = name.replaceAll(", ", ",").replaceAll("input_1,input_2,...", "input,...");

            String content = converter.convert(tr.child(2).html());
            content += "\nDefault: " + tr.child(1).text();
            result.put(name, content);
        }

        return result;
	}
}
