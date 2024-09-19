package ai.vespa.schemals.documentation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DocumentationFetcher
 */
public class FetchDocumentation {
    private final static String SCHEMA_URL = "en/reference/schema-reference.html";
    private final static String RANK_FEATURE_URL = "en/reference/rank-features.html";

    private final static Map<String, List<String>> REPLACE_FILENAME_MAP = new HashMap<>(){{
        put("EXPRESSION", List.of( "EXPRESSION_SL", "EXPRESSION_ML" ));
        put("RANK_FEATURES", List.of( "RANKFEATURES_SL", "RANKFEATURES_ML" ));
        put("FUNCTION (INLINE)? [NAME]", List.of( "FUNCTION" ));
        put("SUMMARY_FEATURES", List.of( "SUMMARYFEATURES_SL", "SUMMARYFEATURES_ML", "SUMMARYFEATURES_ML_INHERITS" ));
        put("MATCH_FEATURES", List.of( "MATCHFEATURES_SL", "MATCHFEATURES_ML", "MATCHFEATURES_SL_INHERITS" ));
        put("IMPORT FIELD", List.of( "IMPORT" ));
    }};

    public static void fetchDocs(Path targetPath) throws IOException {
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

        Map<String, String> rankFeatureMarkdownContent = new RankFeatureDocumentationFetcher(RANK_FEATURE_URL).getMarkdownContent();

        writePath = targetPath.resolve("rankExpression");
        for (var entry : rankFeatureMarkdownContent.entrySet()) {
            Files.write(writePath.resolve(entry.getKey() + ".md"), entry.getValue().getBytes(), StandardOpenOption.CREATE);
        }
    }

    private static String convertToToken(String h2Id) {
        return h2Id.toUpperCase().replaceAll("-", "_");
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("FetchDocumentation requires one argument: <path-to-write-docs>");
            System.exit(1);
        }
        Path targetPath = Paths.get(args[0]);
        try {
            System.out.println("Fetching docs to " + args[0]);
            fetchDocs(targetPath);
        } catch (IOException ex) {
            System.err.println("FetchDocumentation failed to download documentation: " + ex.getMessage());
            System.exit(1);
        }
    }
}
