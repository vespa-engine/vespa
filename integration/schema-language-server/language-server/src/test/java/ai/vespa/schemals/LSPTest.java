package ai.vespa.schemals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.Test;

import com.yahoo.io.IOUtils;

import ai.vespa.schemals.context.EventDocumentContext;
import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.context.InvalidContextException;
import ai.vespa.schemals.lsp.common.semantictokens.SemanticTokenMarker;
import ai.vespa.schemals.lsp.schema.definition.SchemaDefinition;
import ai.vespa.schemals.lsp.schema.semantictokens.SchemaSemanticTokens;
import ai.vespa.schemals.lsp.yqlplus.semantictokens.YQLPlusSemanticTokens;
import ai.vespa.schemals.schemadocument.DocumentManager;
import ai.vespa.schemals.schemadocument.parser.schema.IdentifySymbolDefinition;
import ai.vespa.schemals.schemadocument.parser.schema.IdentifySymbolReferences;
import ai.vespa.schemals.schemadocument.resolvers.SymbolReferenceResolver;
import ai.vespa.schemals.testutils.Utils.TestContext;

/**
 * LSPTest
 */
public class LSPTest {
    /**
     * Describes a test where go-to-definition on startPosition should return resultRange
     */
    private record DefinitionTestPair(Position startPosition, Range resultRange) {}

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

        TestContext testContext = TestContext.create();

        testContext.scheduler().openDocument(new TextDocumentItem(fileURI, "vespaSchema", 0, fileContent));

        assertNotEquals(null, testContext.scheduler().getDocument(fileURI), "Adding a document to the scheduler should create a DocumentManager for it.");

        DocumentManager document = testContext.scheduler().getDocument(fileURI);

        // A list of tests specific to the file read above, positions are 0-indexed.
        List<DefinitionTestPair> definitionTests = List.of(
            new DefinitionTestPair(new Position(9, 32), new Range(new Position(5, 15), new Position(5, 23))),
            new DefinitionTestPair(new Position(10, 25), new Range(new Position(6, 18), new Position(6, 19))),
            new DefinitionTestPair(new Position(17, 17), new Range(new Position(2, 14), new Position(2, 21))),
            new DefinitionTestPair(new Position(17, 25), new Range(new Position(9, 14), new Position(9, 20))),
            new DefinitionTestPair(new Position(17, 32), new Range(new Position(10, 25), new Position(10, 26))),
            new DefinitionTestPair(new Position(28, 24), new Range(new Position(21, 17), new Position(21, 20))),
            new DefinitionTestPair(new Position(28, 32), new Range(new Position(21, 17), new Position(21, 20)))
        );

        for (var testPair : definitionTests) {
            EventPositionContext definitionContext = testContext.getPositionContext(
                document,
                testPair.startPosition() 
            );
            List<Location> result = SchemaDefinition.getDefinition(definitionContext);
            assertEquals(1, result.size(), "Definition request should return exactly 1 result for position " + testPair.startPosition().toString());

            assertEquals(testPair.resultRange(), result.get(0).getRange(), "Definition request returned wrong range for position " + testPair.startPosition().toString());
        }
    }

    /*
     * Test if partition of semantic tokens is as expected on a test file.
     */
    @Test
    void semanticTokenSchemaPartitionTest() throws IOException, InvalidContextException {
        // This list is generated from running on a working build on the file tested on below.
        List<Range> semanticTokenTestFileRanges = List.of(
            new Range(new Position(0, 0), new Position(0, 30)),
            new Range(new Position(1, 0), new Position(1, 6)),
            new Range(new Position(1, 7), new Position(1, 20)),
            new Range(new Position(2, 4), new Position(2, 33)),
            new Range(new Position(3, 4), new Position(3, 12)),
            new Range(new Position(3, 13), new Position(3, 26)),
            new Range(new Position(4, 8), new Position(4, 13)),
            new Range(new Position(4, 14), new Position(4, 22)),
            new Range(new Position(4, 23), new Position(4, 27)),
            new Range(new Position(4, 28), new Position(4, 34)),
            new Range(new Position(6, 0), new Position(6, 26)),
            new Range(new Position(7, 8), new Position(7, 13)),
            new Range(new Position(7, 14), new Position(7, 20)),
            new Range(new Position(7, 21), new Position(7, 25)),
            new Range(new Position(7, 26), new Position(7, 41)),
            new Range(new Position(8, 12), new Position(8, 17)),
            new Range(new Position(8, 19), new Position(8, 26)),
            new Range(new Position(11, 8), new Position(11, 13)),
            new Range(new Position(11, 14), new Position(11, 17)),
            new Range(new Position(11, 18), new Position(11, 22)),
            new Range(new Position(11, 23), new Position(11, 26)),
            new Range(new Position(12, 21), new Position(12, 50)),
            new Range(new Position(13, 12), new Position(13, 17)),
            new Range(new Position(14, 16), new Position(14, 44)),
            new Range(new Position(14, 46), new Position(14, 49)),
            new Range(new Position(14, 50), new Position(14, 74)),
            new Range(new Position(18, 4), new Position(18, 16)),
            new Range(new Position(18, 17), new Position(18, 32)),
            new Range(new Position(19, 8), new Position(19, 16)),
            new Range(new Position(19, 17), new Position(19, 20)),
            new Range(new Position(20, 12), new Position(20, 22)),
            new Range(new Position(20, 24), new Position(20, 34)),
            new Range(new Position(20, 35), new Position(20, 41)),
            new Range(new Position(20, 43), new Position(20, 44)),
            new Range(new Position(20, 45), new Position(20, 55)),
            new Range(new Position(20, 56), new Position(20, 59)),
            new Range(new Position(24, 0), new Position(24, 24))
        );

        String fileName = "src/test/sdfiles/single/semantictoken.sd";
        File file = new File(fileName);
        String fileURI = file.toURI().toString();
        String fileContent = IOUtils.readFile(file);

        TestContext testContext = TestContext.create();

        testContext.scheduler().openDocument(new TextDocumentItem(fileURI, "vespaSchema", 0, fileContent));

        DocumentManager document = testContext.scheduler().getDocument(fileURI);
        EventDocumentContext context = testContext.getDocumentContext(
            document
        );

        SchemaSemanticTokens.init();
        List<SemanticTokenMarker> computedMarkers = SchemaSemanticTokens.getSemanticTokenMarkers(context);

        assertEquals(semanticTokenTestFileRanges.size(), computedMarkers.size(), "Computed markers does not have the same size as expected.");

        for (int i = 0; i < computedMarkers.size(); ++i) {
            Range expectedRange = semanticTokenTestFileRanges.get(i);
            Range computedRange = computedMarkers.get(i).getRange();

            assertEquals(expectedRange, computedRange, "If this test fails you should open " + fileURI + " with the Language Server running and inspect semantic tokens (syntax highlighting).");
        }

        testContext.scheduler().closeDocument(fileURI);
    }

    @Test
    void semanticTokenYQLPartitionTest() throws IOException, InvalidContextException {
        List<Range> semanticTokenTestFileRanges = List.of(
            new Range(new Position(0, 0), new Position(0, 31)),
            new Range(new Position(1, 0), new Position(1, 39)),
            new Range(new Position(2, 0), new Position(2, 6)),
            new Range(new Position(2, 9), new Position(2, 13)),
            new Range(new Position(2, 14), new Position(2, 22)),
            new Range(new Position(2, 23), new Position(2, 28)),
            new Range(new Position(2, 29), new Position(2, 33)),
            new Range(new Position(2, 36), new Position(2, 39)),
            new Range(new Position(3, 3), new Position(3, 19)),
            new Range(new Position(4, 3), new Position(4, 8)),
            new Range(new Position(4, 9), new Position(4, 13)),
            new Range(new Position(5, 3), new Position(5, 31)),
            new Range(new Position(6, 3), new Position(6, 8)),
            new Range(new Position(6, 9), new Position(6, 10)),
            new Range(new Position(6, 10), new Position(6, 15)),
            new Range(new Position(7, 3), new Position(7, 48)),
            new Range(new Position(8, 3), new Position(8, 7)),
            new Range(new Position(9, 7), new Position(9, 13)),
            new Range(new Position(9, 14), new Position(9, 19)),
            new Range(new Position(12, 0), new Position(12, 30)),
            new Range(new Position(14, 0), new Position(14, 20)),
            new Range(new Position(15, 0), new Position(15, 6)),
            new Range(new Position(15, 9), new Position(15, 13)),
            new Range(new Position(15, 14), new Position(15, 17)),
            new Range(new Position(15, 18), new Position(15, 23)),
            new Range(new Position(15, 24), new Position(15, 28)),
            new Range(new Position(16, 0), new Position(16, 16))
        );

        String fileName = "src/test/yqlfiles/semantictoken.yql";
        File file = new File(fileName);
        String fileURI = file.toURI().toString();
        String fileContent = IOUtils.readFile(file);

        TestContext testContext = TestContext.create();

        testContext.scheduler().openDocument(new TextDocumentItem(fileURI, "vespaYQL", 0, fileContent));

        DocumentManager document = testContext.scheduler().getDocument(fileURI);
        EventDocumentContext context = testContext.getDocumentContext(document);

        YQLPlusSemanticTokens.init();
        List<SemanticTokenMarker> computedMarkers = YQLPlusSemanticTokens.getSemanticTokenMarkers(context);

        assertEquals(semanticTokenTestFileRanges.size(), computedMarkers.size(), "Computed markers does not have the same size as expected.");

        for (int i = 0; i < computedMarkers.size(); ++i) {
            Range expectedRange = semanticTokenTestFileRanges.get(i);
            Range computedRange = computedMarkers.get(i).getRange();

            assertEquals(expectedRange, computedRange, "If this test fails you should open " + fileURI + " with the Language Server running and inspect semantic tokens (syntax highlighting).");
        }

        testContext.scheduler().closeDocument(fileURI);
    }
}
