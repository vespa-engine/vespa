package ai.vespa.schemals.documentation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import ai.vespa.schemals.common.FileUtils;

/**
 * DocumentationFetcher
 */
public class FetchDocumentation {
    private final static String SCHEMA_URL = "en/reference/schema-reference.html";
    private final static String RANK_FEATURE_URL = "en/reference/rank-features.html";

    private record ServicesLocation(String relativeUrl, String relativeSavePath) {}

    private final static ServicesLocation[] SERVICES_PATHS = {
        new ServicesLocation("en/reference/services.html", ""),
        new ServicesLocation("en/reference/services-admin.html", "admin"),
        new ServicesLocation("en/reference/services-container.html", "container"),
        new ServicesLocation("en/reference/services-content.html", "content"),
        new ServicesLocation("en/reference/services-docproc.html", "container/document-processing"),
        new ServicesLocation("en/reference/services-http.html", "container/http"),
        new ServicesLocation("en/reference/services-processing.html", "container/processing"),
        new ServicesLocation("en/reference/services-search.html", "container/search")
    };

    private final static Map<String, List<String>> REPLACE_FILENAME_MAP = new HashMap<>(){{
        put("EXPRESSION", List.of( "EXPRESSION_SL", "EXPRESSION_ML" ));
        put("RANK_FEATURES", List.of( "RANKFEATURES_SL", "RANKFEATURES_ML" ));
        put("FUNCTION (INLINE)? [NAME]", List.of( "FUNCTION" ));
        put("SUMMARY_FEATURES", List.of( "SUMMARYFEATURES_SL", "SUMMARYFEATURES_ML", "SUMMARYFEATURES_ML_INHERITS" ));
        put("MATCH_FEATURES", List.of( "MATCHFEATURES_SL", "MATCHFEATURES_ML", "MATCHFEATURES_SL_INHERITS" ));
        put("IMPORT FIELD", List.of( "IMPORT" ));
    }};

    public static void fetchSchemaDocs(Path targetPath) throws IOException {
        Files.createDirectories(targetPath);
        Files.createDirectories(targetPath.resolve("schema"));
        Files.createDirectories(targetPath.resolve("rankExpression"));

        Path writePath = targetPath.resolve("schema");

        Map<String, String> schemaMarkdownContent = new SchemaDocumentationFetcher(SCHEMA_URL).getMarkdownContent();

        for (var entry : schemaMarkdownContent.entrySet()) {
            String tokenName = convertToToken(entry.getKey());
            String content = entry.getValue();

            List<String> fileNamesToWrite = REPLACE_FILENAME_MAP.getOrDefault(tokenName, List.of(tokenName));

            for (String fileName : fileNamesToWrite) {
                fileName = FileUtils.sanitizeFileName(fileName);
                writeMarkdown(writePath.resolve(fileName + ".md"), content);
            }
        }

        Map<String, String> rankFeatureMarkdownContent = new RankFeatureDocumentationFetcher(RANK_FEATURE_URL).getMarkdownContent();

        writePath = targetPath.resolve("rankExpression");
        for (var entry : rankFeatureMarkdownContent.entrySet()) {
            String fileName = FileUtils.sanitizeFileName(entry.getKey());
            writeMarkdown(writePath.resolve(fileName + ".md"), entry.getValue());
        }
    }

    public static void fetchServicesDocs(Path targetPath) throws IOException {
        Files.createDirectories(targetPath);
        Files.createDirectories(targetPath.resolve("services"));
        targetPath = targetPath.resolve("services");

        for (ServicesLocation locationEntry : SERVICES_PATHS) {
            Map<String, String> markdownContent = new ServicesDocumentationFetcher(locationEntry.relativeUrl(), locationEntry.relativeSavePath()).getMarkdownContent();
            Path writePath = targetPath.resolve(locationEntry.relativeSavePath());
            Files.createDirectories(writePath); // mkdir -p

            for (var entry : markdownContent.entrySet()) {
                if (entry.getKey().contains("/")) continue;
                String fileName = entry.getKey().toLowerCase();
                fileName = FileUtils.sanitizeFileName(fileName);
                writeMarkdown(writePath.resolve(fileName + ".md"), entry.getValue());
            }
        }
    }


    private static void writeMarkdown(Path writePath, String markdown) throws IOException {
        Files.write(writePath, markdown.getBytes(), StandardOpenOption.CREATE);
    }

    private static String convertToToken(String h2Id) {
        return h2Id.toUpperCase().replaceAll("-", "_");
    }

    // Runs during build
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("FetchDocumentation requires one argument: <path-to-write-docs>");
            System.exit(1);
        }
        Path targetPath = Paths.get(args[0]);
        try {
            System.out.println("Fetching docs to " + args[0]);
            fetchSchemaDocs(targetPath);
            fetchServicesDocs(targetPath);
        } catch (IOException ex) {
            System.err.println("FetchDocumentation failed to download documentation: " + ex.getMessage());
            System.exit(1);
        }
    }
}
