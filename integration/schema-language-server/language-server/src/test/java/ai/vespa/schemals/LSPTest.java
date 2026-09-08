package ai.vespa.schemals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.Test;

import com.yahoo.collections.Pair;
import com.yahoo.io.IOUtils;

import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.context.EventCompletionContext;
import ai.vespa.schemals.context.EventDocumentContext;
import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.context.InvalidContextException;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.lsp.schema.completion.SchemaCompletion;
import ai.vespa.schemals.lsp.schema.completion.provider.BodyKeywordCompletion;
import ai.vespa.schemals.lsp.schema.definition.SchemaDefinition;
import ai.vespa.schemals.lsp.common.semantictokens.CommonSemanticTokens;
import ai.vespa.schemals.lsp.schema.hover.SchemaHover;
import ai.vespa.schemals.lsp.schema.semantictokens.SchemaSemanticTokens;
import ai.vespa.schemals.schemadocument.DocumentManager;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;
import ai.vespa.schemals.schemadocument.parser.schema.IdentifySymbolDefinition;
import ai.vespa.schemals.schemadocument.parser.schema.IdentifySymbolReferences;
import ai.vespa.schemals.schemadocument.resolvers.SymbolReferenceResolver;
import ai.vespa.schemals.testutils.TestLogger;
import ai.vespa.schemals.testutils.TestSchemaDiagnosticsHandler;
import ai.vespa.schemals.testutils.TestSchemaMessageHandler;
import ai.vespa.schemals.testutils.TestSchemaProgressHandler;
import ai.vespa.schemals.testutils.Utils;

/**
 * LSPTest
 */
public class LSPTest {
    /**
     * Uses a hand-crafted file to test some go-to-definition requests.
     * If this test fails, check
     * - That the file src/test/sdfiles/single/definition.sd has not been changed. 
     * - Go to definition code in {@link SchemaDefinition#getDefinition}
     * - Symbol definition logic in {@link IdentifySymbolDefinition#identify}
     * - Symbol reference logic in {@link IdentifySymbolReferences#identify}
     * - Symbol reference resolving in {@link SymbolReferenceResolver#resolveSymbolReference}
     */
    @Test
    void definitionTest() throws IOException, InvalidContextException {
        String fileName = "src/test/sdfiles/single/definition.sd";
        File file = new File(fileName);
        String fileURI = file.toURI().toString();
        String fileContent = IOUtils.readFile(file);
        TestSchemaMessageHandler messageHandler = new TestSchemaMessageHandler();
        TestSchemaProgressHandler progressHandler = new TestSchemaProgressHandler();
        ClientLogger logger = new TestLogger(messageHandler);
        SchemaIndex schemaIndex = new SchemaIndex(logger);
        TestSchemaDiagnosticsHandler diagnosticsHandler = new TestSchemaDiagnosticsHandler(new ArrayList<>());
        SchemaDocumentScheduler scheduler = new SchemaDocumentScheduler(logger, diagnosticsHandler, schemaIndex, messageHandler, progressHandler);

        scheduler.openDocument(new TextDocumentItem(fileURI, "vespaSchema", 0, fileContent));

        assertNotEquals(null, scheduler.getDocument(fileURI), "Adding a document to the scheduler should create a DocumentManager for it.");

        DocumentManager document = scheduler.getDocument(fileURI);

        // A list of tests specific to the file read above, positions are 0-indexed.
        List<Pair<Position, Range>> definitionTests = List.of(
            new Pair<>(new Position(9, 32), new Range(new Position(5, 15), new Position(5, 23))),
            new Pair<>(new Position(10, 25), new Range(new Position(6, 18), new Position(6, 19))),
            new Pair<>(new Position(17, 17), new Range(new Position(2, 14), new Position(2, 21))),
            new Pair<>(new Position(17, 25), new Range(new Position(9, 14), new Position(9, 20))),
            new Pair<>(new Position(17, 32), new Range(new Position(10, 25), new Position(10, 26))),
            new Pair<>(new Position(28, 24), new Range(new Position(21, 17), new Position(21, 20))),
            new Pair<>(new Position(28, 32), new Range(new Position(21, 17), new Position(21, 20)))
        );

        for (var testPair : definitionTests) {
            Position startPos = testPair.getFirst();
            EventPositionContext definitionContext = new EventPositionContext(
                scheduler,
                schemaIndex,
                messageHandler,
                document.getVersionedTextDocumentIdentifier(),
                startPos 
            );
            List<Location> result = SchemaDefinition.getDefinition(definitionContext);
            assertEquals(1, result.size(), "Definition request should return exactly 1 result for position " + startPos.toString());

            assertEquals(testPair.getSecond(), result.get(0).getRange(), 
                "Definition request returned wrong range for position " + startPos.toString());
        }
    }

    /**
     * The linguistics element nests bodies, e.g. linguistics { profile { ... } } or
     * linguistics { index { ... } }, which the grammar flattens into a single node, so
     * {@link BodyKeywordCompletion} has to tell them apart by which keyword opened the
     * enclosing brace, not just brace depth.
     */
    @Test
    void linguisticsCompletionTest() throws IOException, InvalidContextException {
        String fileName = "src/test/sdfiles/single/linguistics.sd";
        File file = new File(fileName);
        String fileURI = file.toURI().toString();
        String fileContent = IOUtils.readFile(file);

        // The completion providers look up hover documentation relative to the server path when initialized.
        SchemaLanguageServer.serverPath = Paths.get("target");

        TestSchemaMessageHandler messageHandler = new TestSchemaMessageHandler();
        TestSchemaProgressHandler progressHandler = new TestSchemaProgressHandler();
        ClientLogger logger = new TestLogger(messageHandler);
        SchemaIndex schemaIndex = new SchemaIndex(logger);
        TestSchemaDiagnosticsHandler diagnosticsHandler = new TestSchemaDiagnosticsHandler(new ArrayList<>());
        SchemaDocumentScheduler scheduler = new SchemaDocumentScheduler(logger, diagnosticsHandler, schemaIndex, messageHandler, progressHandler);

        scheduler.openDocument(new TextDocumentItem(fileURI, "vespaSchema", 0, fileContent));
        DocumentManager document = scheduler.getDocument(fileURI);

        // Positions are 0-indexed and point at the start of a line inside the given body.
        assertEquals(List.of("index", "profile", "profile", "search", "tokens"),
                     completionLabelsAt(scheduler, schemaIndex, messageHandler, document, new Position(8, 16)),
                     "Directly inside a linguistics body, profile, tokens, index and search should be suggested.");
        assertEquals(List.of("index", "search"), completionLabelsAt(scheduler, schemaIndex, messageHandler, document, new Position(10, 20)),
                     "Inside a linguistics profile body index and search should be suggested.");
        assertEquals(List.of("profile", "tokens"), completionLabelsAt(scheduler, schemaIndex, messageHandler, document, new Position(25, 20)),
                     "Inside a linguistics index body profile and tokens should be suggested.");
        assertEquals(List.of("profile", "tokens"), completionLabelsAt(scheduler, schemaIndex, messageHandler, document, new Position(29, 20)),
                     "Inside a linguistics search body profile and tokens should be suggested.");
        assertTrue(completionLabelsAt(scheduler, schemaIndex, messageHandler, document, new Position(16, 12)).contains("linguistics"),
                   "linguistics should be suggested in a field body.");
    }

    /**
     * 'map: fast-search' is only accepted by the config model on maps with string, int or long keys and values,
     * so {@link BodyKeywordCompletion} should only suggest the map block in such fields.
     */
    @Test
    void mapFastSearchCompletionTest() throws IOException, InvalidContextException {
        String fileName = "src/test/sdfiles/single/mapcompletion.sd";
        File file = new File(fileName);
        String fileURI = file.toURI().toString();
        String fileContent = IOUtils.readFile(file);

        SchemaLanguageServer.serverPath = Paths.get("target");

        TestSchemaMessageHandler messageHandler = new TestSchemaMessageHandler();
        TestSchemaProgressHandler progressHandler = new TestSchemaProgressHandler();
        ClientLogger logger = new TestLogger(messageHandler);
        SchemaIndex schemaIndex = new SchemaIndex(logger);
        TestSchemaDiagnosticsHandler diagnosticsHandler = new TestSchemaDiagnosticsHandler(new ArrayList<>());
        SchemaDocumentScheduler scheduler = new SchemaDocumentScheduler(logger, diagnosticsHandler, schemaIndex, messageHandler, progressHandler);

        scheduler.openDocument(new TextDocumentItem(fileURI, "vespaSchema", 0, fileContent));
        DocumentManager document = scheduler.getDocument(fileURI);

        // Positions are 0-indexed and point at the empty line inside the given field body.
        assertTrue(completionLabelsAt(scheduler, schemaIndex, messageHandler, document, new Position(4, 12)).contains("map"),
                   "map should be suggested in a map<string, string> field.");
        assertTrue(completionLabelsAt(scheduler, schemaIndex, messageHandler, document, new Position(8, 12)).contains("map"),
                   "map should be suggested in a map<string, int> field.");
        assertTrue(completionLabelsAt(scheduler, schemaIndex, messageHandler, document, new Position(12, 12)).contains("map"),
                   "map should be suggested in a map<string, long> field.");
        assertFalse(completionLabelsAt(scheduler, schemaIndex, messageHandler, document, new Position(16, 12)).contains("map"),
                    "map should not be suggested in a string field.");
        assertFalse(completionLabelsAt(scheduler, schemaIndex, messageHandler, document, new Position(20, 12)).contains("map"),
                    "map should not be suggested in an array<string> field.");
        assertEquals(List.of("fast-search"), completionLabelsAt(scheduler, schemaIndex, messageHandler, document, new Position(24, 17)),
                     "fast-search should be the only suggestion after 'map: '.");
        assertEquals(List.of("fast-search"), completionLabelsAt(scheduler, schemaIndex, messageHandler, document, new Position(29, 16)),
                     "fast-search should be the only suggestion inside a map block.");
        assertFalse(completionLabelsAt(scheduler, schemaIndex, messageHandler, document, new Position(34, 12)).contains("map"),
                    "map should not be suggested in a map<string, double> field.");
    }

    /**
     * 'map' is both a type constructor and the keyword opening a map settings block.
     * It should be colored as a type in the former case and as a keyword in the latter.
     */
    @Test
    void mapSemanticTokenTest() throws IOException, InvalidContextException {
        String fileName = "src/test/sdfiles/single/mapcompletion.sd";
        File file = new File(fileName);
        String fileURI = file.toURI().toString();
        String fileContent = IOUtils.readFile(file);

        TestSchemaMessageHandler messageHandler = new TestSchemaMessageHandler();
        TestSchemaProgressHandler progressHandler = new TestSchemaProgressHandler();
        ClientLogger logger = new TestLogger(messageHandler);
        SchemaIndex schemaIndex = new SchemaIndex(logger);
        TestSchemaDiagnosticsHandler diagnosticsHandler = new TestSchemaDiagnosticsHandler(new ArrayList<>());
        SchemaDocumentScheduler scheduler = new SchemaDocumentScheduler(logger, diagnosticsHandler, schemaIndex, messageHandler, progressHandler);

        scheduler.openDocument(new TextDocumentItem(fileURI, "vespaSchema", 0, fileContent));

        // Building the legend also initializes the token type tables used when producing tokens.
        SemanticTokensLegend legend = CommonSemanticTokens.getSemanticTokensRegistrationOptions().getLegend();
        EventDocumentContext context = new EventDocumentContext(scheduler, schemaIndex, messageHandler, new TextDocumentIdentifier(fileURI));
        List<Integer> data = SchemaSemanticTokens.getSemanticTokens(context).getData();

        // Positions are 0-indexed: the 'map' of 'type map<string, string>' on line 2 and of 'map: fast-search' on line 24.
        assertEquals(List.of("type"), semanticTokenTypesAt(data, legend, new Position(2, 33)),
                     "map in a field type should be colored as a type.");
        assertEquals(List.of("keyword"), semanticTokenTypesAt(data, legend, new Position(24, 12)),
                     "map opening a map settings block should be colored as a keyword.");
        assertEquals(List.of("keyword"), semanticTokenTypesAt(data, legend, new Position(28, 12)),
                     "map opening a map settings block should be colored as a keyword.");
        assertEquals(List.of("keyword"), semanticTokenTypesAt(data, legend, new Position(24, 17)),
                     "fast-search in a map settings block should be colored as a keyword.");
    }

    /** Decodes the delta-encoded semantic token data and returns the type names of the tokens starting at the given position. */
    private List<String> semanticTokenTypesAt(List<Integer> data, SemanticTokensLegend legend, Position position) {
        List<String> ret = new ArrayList<>();
        int line = 0;
        int column = 0;
        for (int i = 0; i + 4 < data.size(); i += 5) {
            int deltaLine = data.get(i);
            int deltaColumn = data.get(i + 1);
            line += deltaLine;
            column = (deltaLine == 0) ? column + deltaColumn : deltaColumn;
            if (line == position.getLine() && column == position.getCharacter()) {
                ret.add(legend.getTokenTypes().get(data.get(i + 3)));
            }
        }
        return ret;
    }

    private List<String> completionLabelsAt(SchemaDocumentScheduler scheduler,
                                            SchemaIndex schemaIndex,
                                            TestSchemaMessageHandler messageHandler,
                                            DocumentManager document,
                                            Position position) throws InvalidContextException {
        EventCompletionContext context = new EventCompletionContext(
            scheduler, schemaIndex, messageHandler, document.getVersionedTextDocumentIdentifier(), position, null);
        return SchemaCompletion.getCompletionItems(context, System.err)
                               .stream()
                               .map(CompletionItem::getLabel)
                               .sorted()
                               .toList();
    }
}
