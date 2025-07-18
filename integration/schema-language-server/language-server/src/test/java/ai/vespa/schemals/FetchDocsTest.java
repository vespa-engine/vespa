package ai.vespa.schemals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentItem;
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

        //if (Paths.get("").resolve("tmp").toFile().exists()) {
        //    deleteDirectory(Paths.get("").resolve("tmp").toFile());
        //}
    }

    private boolean deleteDirectory(File directory) {
        File[] contents = directory.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDirectory(f);
            }
        }
        return directory.delete();
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

        List<String> lines = fileContent.lines().toList();

        assertNotNull(document.getRootNode());

        for (int lineNum = 0; lineNum < lines.size(); ++lineNum) {
            int nonWhiteSpace = 0;

            String line = lines.get(lineNum);

            for (nonWhiteSpace = 0; nonWhiteSpace < line.length(); ++nonWhiteSpace) {
                if (!Character.isWhitespace(line.charAt(nonWhiteSpace)))break;
            }

            if (nonWhiteSpace == line.length()) continue;

            if (line.charAt(nonWhiteSpace) == '}') continue;

            Position hoverPosition = new Position(lineNum, nonWhiteSpace);

            EventPositionContext hoverContext = new EventPositionContext(
                scheduler,
                schemaIndex,
                messageHandler,
                document.getVersionedTextDocumentIdentifier(),
                hoverPosition
            );

            Hover hoverResult = SchemaHover.getHover(hoverContext, hoverPath);

            assertNotEquals(null, hoverResult, 
                "Failed to get hover information for " + fileName + ":" + (lineNum+1) + ":" + (hoverPosition.getCharacter()+1) +": " + line.substring(nonWhiteSpace));
        }
    }
}
