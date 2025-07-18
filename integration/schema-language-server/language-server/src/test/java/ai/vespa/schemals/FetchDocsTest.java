package ai.vespa.schemals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import com.yahoo.io.IOUtils;

import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.context.InvalidContextException;
import ai.vespa.schemals.documentation.FetchDocumentation;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.lsp.schema.hover.SchemaHover;
import ai.vespa.schemals.schemadocument.DocumentManager;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;
import ai.vespa.schemals.testutils.TestLogger;
import ai.vespa.schemals.testutils.TestSchemaDiagnosticsHandler;
import ai.vespa.schemals.testutils.TestSchemaMessageHandler;
import ai.vespa.schemals.testutils.TestSchemaProgressHandler;

/**
 * FetchDocsTest
 */
public class FetchDocsTest {
    @Test
    @Order(1)
    public void testFetchDocs() {
        try {
            FetchDocumentation.fetchSchemaDocs(Paths.get("").resolve("tmp").resolve("generated-resources").resolve("hover"));
            FetchDocumentation.fetchServicesDocs(Paths.get("").resolve("tmp").resolve("generated-resources").resolve("hover"));
        } catch(IOException ioe) {
            assertEquals(0, 1, ioe.getMessage());
        }

    }

    @Test
    @Order(2)
    public void hoverTest() throws IOException, InvalidContextException {
        String fileName = "src/test/sdfiles/single/hover.sd";
        File file = new File(fileName);
        String fileURI = file.toURI().toString();
        String fileContent = IOUtils.readFile(file);
        TestSchemaMessageHandler messageHandler = new TestSchemaMessageHandler();
        TestSchemaProgressHandler progressHandler = new TestSchemaProgressHandler();
        ClientLogger logger = new TestLogger(messageHandler);
        SchemaIndex schemaIndex = new SchemaIndex(logger);
        TestSchemaDiagnosticsHandler diagnosticsHandler = new TestSchemaDiagnosticsHandler(new ArrayList<>());
        SchemaDocumentScheduler scheduler = new SchemaDocumentScheduler(logger, diagnosticsHandler, schemaIndex, messageHandler, progressHandler);

        Path hoverPath = Paths.get("").resolve("tmp").resolve("generated-resources").resolve("hover");

        scheduler.openDocument(new TextDocumentItem(fileURI, "vespaSchema", 0, fileContent));
        assertNotEquals(null, scheduler.getDocument(fileURI), "Adding a document to the scheduler should create a DocumentManager for it.");
        DocumentManager document = scheduler.getDocument(fileURI);

        assertNotNull(document.getRootNode());

        // Non-exhaustive, but covers several keywords and symbol types
        Position[] positionsToTest = {
            new Position(0, 0),
            new Position(0, 10),
            new Position(1, 11),
            new Position(2, 11),
            new Position(2, 17),
            new Position(4, 16),
            new Position(5, 16),
            new Position(6, 16),
            new Position(7, 18),
            new Position(13, 18),
            new Position(19, 18),
            new Position(47, 18),
            new Position(50, 29),
            new Position(63, 22),
            new Position(72, 10),
            new Position(89, 13),
            new Position(134, 19),
            new Position(139, 9),
        };

        for (Position hoverPosition : positionsToTest) {
            EventPositionContext hoverContext = new EventPositionContext(
                scheduler,
                schemaIndex,
                messageHandler,
                document.getVersionedTextDocumentIdentifier(),
                hoverPosition
            );

            Hover hoverResult = SchemaHover.getHover(hoverContext, hoverPath);
            assertNotNull(hoverResult, 
                "Failed to get hover information for " + fileName + ":" + (hoverPosition.getLine()+1) + ":" + (hoverPosition.getCharacter()+1));
        }
    }

    private static boolean deleteDirectory(File directory) {
        File[] contents = directory.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDirectory(f);
            }
        }
        return directory.delete();
    }

    @AfterAll
    static void cleanup() {
        if (Paths.get("").resolve("tmp").toFile().exists()) {
            deleteDirectory(Paths.get("").resolve("tmp").toFile());
        }
    }
}
