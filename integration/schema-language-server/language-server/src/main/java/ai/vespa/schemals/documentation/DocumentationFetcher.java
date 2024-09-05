package ai.vespa.schemals.documentation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;

import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.renderer.ResolvedLink;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.html2md.converter.HtmlLinkResolver;
import com.vladsch.flexmark.html2md.converter.HtmlLinkResolverFactory;
import com.vladsch.flexmark.html2md.converter.HtmlNodeConverterContext;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataHolder;
import com.vladsch.flexmark.util.data.MutableDataSet;

/**
 * DocumentationFetcher
 */
public class DocumentationFetcher {
    private final static String SCHEMA_URL = "/en/reference/schema-reference.html";
    private final static String RANK_EXPRESSION_URL = "/en/reference/rank-features.html";

    private final static Map<String, List<String>> REPLACE_FILENAME_MAP = new HashMap<>(){{
        put("EXPRESSION", List.of( "EXPRESSION_SL", "EXPRESSION_ML" ));
        put("RANK_FEATURES", List.of( "RANKFEATURES_SL", "RANKFEATURES_ML" ));
        put("FUNCTION (INLINE)? [NAME]", List.of( "FUNCTION" ));
        put("SUMMARY_FEATURES", List.of( "SUMMARYFEATURES_SL", "SUMMARYFEATURES_ML", "SUMMARYFEATURES_ML_INHERITS" ));
        put("MATCH_FEATURES", List.of( "MATCHFEATURES_SL", "MATCHFEATURES_ML", "MATCHFEATURES_SL_INHERITS" ));
        put("IMPORT FIELD", List.of( "IMPORT" ));
    }};

    public static String fetchDocs() throws IOException {
        Path targetPath = Paths.get("").resolve("target").resolve("generated-resources").resolve("hover");
        Files.createDirectories(targetPath);
        Files.createDirectories(targetPath.resolve("schema"));
        Files.createDirectories(targetPath.resolve("rankExpression"));

        Path writePath = targetPath.resolve("schema");

        Map<String, String> schemaMarkdownContent = new SchemaDocumentationFetcher(SCHEMA_URL).getMarkdownContent();

        for (var entry : schemaMarkdownContent.entrySet()) {
            String fileName = convertToToken(entry.getKey());
            String content = entry.getValue();

            if (REPLACE_FILENAME_MAP.containsKey(fileName)) {
                for (String replacedFileName : REPLACE_FILENAME_MAP.get(fileName)) {
                    Files.write(writePath.resolve(replacedFileName + ".md"), content.getBytes(), StandardOpenOption.CREATE);
                }
            } else {
                Files.write(writePath.resolve(fileName + ".md"), content.getBytes(), StandardOpenOption.CREATE);
            }
        }

        return "LGTM";
    }

    private static String convertToToken(String h2Id) {
        return h2Id.toUpperCase().replaceAll("-", "_");
    }
}
