// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.schemals;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yahoo.io.IOUtils;

import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.context.EventDocumentContext;
import ai.vespa.schemals.context.InvalidContextException;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.lsp.common.semantictokens.CommonSemanticTokens;
import ai.vespa.schemals.lsp.schema.semantictokens.SchemaSemanticTokens;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;
import ai.vespa.schemals.testutils.TestLogger;
import ai.vespa.schemals.testutils.TestSchemaDiagnosticsHandler;
import ai.vespa.schemals.testutils.TestSchemaMessageHandler;
import ai.vespa.schemals.testutils.TestSchemaProgressHandler;

/**
 * Dumps the Java LSP's semantic tokens to JSON for comparison with
 * the TextMate grammar in integration/tmgrammar/.
 *
 * This is a utility, not a test. It lives in test sources because it
 * depends on test scaffolding (TestLogger, etc.).
 *
 * Run from the language-server directory:
 *   mvn test-compile exec:java \
 *     -Dexec.mainClass=ai.vespa.schemals.SemanticTokenDumper \
 *     -Dexec.classpathScope=test
 */
public class SemanticTokenDumper {

    private static final Path SD_FILES_DIR = Paths.get("src/test/sdfiles");
    private static final Path OUTPUT_PATH = Paths.get("../../tmgrammar/tools/java_tokens.json");

    public static void main(String[] args) throws IOException, InvalidContextException {
        SemanticTokensWithRegistrationOptions options = CommonSemanticTokens.getSemanticTokensRegistrationOptions();
        SemanticTokensLegend legend = options.getLegend();
        List<String> tokenTypes = legend.getTokenTypes();
        List<String> tokenModifiers = legend.getTokenModifiers();

        // Find all .sd files
        List<Path> sdFiles;
        try (Stream<Path> walk = Files.walk(SD_FILES_DIR)) {
            sdFiles = walk.filter(p -> p.toString().endsWith(".sd"))
                         .sorted()
                         .toList();
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("legend", tokenTypes);
        Map<String, Object> filesMap = new LinkedHashMap<>();

        for (Path sdFile : sdFiles) {
            File file = sdFile.toFile();
            String fileURI = file.toURI().toString();
            String fileContent = IOUtils.readFile(file);

            // Set up test scaffolding - fresh for each file
            TestSchemaMessageHandler messageHandler = new TestSchemaMessageHandler();
            TestSchemaProgressHandler progressHandler = new TestSchemaProgressHandler();
            ClientLogger logger = new TestLogger(messageHandler);
            SchemaIndex schemaIndex = new SchemaIndex(logger);
            TestSchemaDiagnosticsHandler diagnosticsHandler = new TestSchemaDiagnosticsHandler(new ArrayList<>());
            SchemaDocumentScheduler scheduler = new SchemaDocumentScheduler(logger, diagnosticsHandler, schemaIndex, messageHandler, progressHandler);

            scheduler.openDocument(new TextDocumentItem(fileURI, "vespaSchema", 0, fileContent));

            EventDocumentContext context = new EventDocumentContext(
                scheduler, schemaIndex, messageHandler,
                new TextDocumentIdentifier(fileURI)
            );

            List<Integer> data = SchemaSemanticTokens.getSemanticTokens(context).getData();

            // Decode delta-encoded tokens
            String[] lines = fileContent.split("\n", -1);
            List<Map<String, Object>> tokens = new ArrayList<>();
            int prevLine = 0, prevCol = 0;

            for (int i = 0; i + 4 < data.size(); i += 5) {
                int deltaLine = data.get(i);
                int deltaCol = data.get(i + 1);
                int len = data.get(i + 2);
                int typeIndex = data.get(i + 3);
                int modBits = data.get(i + 4);

                int line = prevLine + deltaLine;
                int col = (deltaLine == 0) ? prevCol + deltaCol : deltaCol;
                prevLine = line;
                prevCol = col;

                String typeName = (typeIndex >= 0 && typeIndex < tokenTypes.size())
                    ? tokenTypes.get(typeIndex) : "unknown(" + typeIndex + ")";

                List<String> mods = new ArrayList<>();
                for (int bit = 0; bit < tokenModifiers.size(); bit++) {
                    if ((modBits & (1 << bit)) != 0) {
                        mods.add(tokenModifiers.get(bit));
                    }
                }

                String text = "";
                if (line >= 0 && line < lines.length) {
                    int end = Math.min(col + len, lines[line].length());
                    if (col >= 0 && col <= lines[line].length()) {
                        text = lines[line].substring(col, end);
                    }
                }

                Map<String, Object> token = new LinkedHashMap<>();
                token.put("line", line);
                token.put("col", col);
                token.put("len", len);
                token.put("text", text);
                token.put("type", typeName);
                token.put("modifiers", mods);

                // Deduplicate: if previous token has same position and length,
                // the LSP emitted overlapping tokens (e.g. dataType + keyword).
                // Keep the first one (more specific AST-level token).
                if (!tokens.isEmpty()) {
                    Map<String, Object> prev = tokens.get(tokens.size() - 1);
                    if (prev.get("line").equals(line)
                        && prev.get("col").equals(col)
                        && prev.get("len").equals(len)) {
                        continue; // skip duplicate
                    }
                }
                tokens.add(token);
            }

            String relPath = SD_FILES_DIR.relativize(sdFile).toString();
            Map<String, Object> fileEntry = new LinkedHashMap<>();
            fileEntry.put("tokens", tokens);
            filesMap.put(relPath, fileEntry);
        }

        output.put("files", filesMap);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(OUTPUT_PATH.toFile())) {
            gson.toJson(output, writer);
        }

        System.out.println("Wrote " + filesMap.size() + " files to " + OUTPUT_PATH.toAbsolutePath().normalize());
    }
}
