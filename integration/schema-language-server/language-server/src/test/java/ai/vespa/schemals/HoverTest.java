package ai.vespa.schemals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.Test;

import com.yahoo.io.IOUtils;

import ai.vespa.schemals.testutils.*;

import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.common.FileUtils;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.lsp.schema.hover.SchemaHover;
import ai.vespa.schemals.schemadocument.SchemaDocument;
import ai.vespa.schemals.schemadocument.SchemaDocument.ParseResult;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;

import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;

import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.schemadocument.DocumentManager;


/**
 * HoverTest
 */
public class HoverTest {
    @Test
    public void hoverTest() throws IOException {
        // TODO: uncomment this test and fix hover when new HTML parsing is done
        /*
        String fileName = "src/test/sdfiles/single/hover.sd";
        File file = new File(fileName);
        String fileURI = file.toURI().toString();
        String fileContent = IOUtils.readFile(file);
        SchemaMessageHandler messageHandler = new TestSchemaMessageHandler();
        ClientLogger logger = new TestLogger(messageHandler);
        SchemaIndex schemaIndex = new SchemaIndex(logger);
        TestSchemaDiagnosticsHandler diagnosticsHandler = new TestSchemaDiagnosticsHandler(new ArrayList<>());
        SchemaDocumentScheduler scheduler = new SchemaDocumentScheduler(logger, diagnosticsHandler, schemaIndex, messageHandler);

        scheduler.openDocument(new TextDocumentItem(fileURI, "vespaSchema", 0, fileContent));

        assertNotEquals(null, scheduler.getDocument(fileURI), "Adding a document to the scheduler should create a DocumentManager for it.");

        DocumentManager document = scheduler.getDocument(fileURI);

        assertNotEquals(null, document.getRootNode(), "Parsing the hover file should be successful!");

        List<String> lines = fileContent.lines().toList();

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

            Hover hoverResult = SchemaHover.getHover(hoverContext);

            assertNotEquals(null, hoverResult, 
                "Failed to get hover information for " + fileName + ", line " + (lineNum+1) + ": " + line.substring(nonWhiteSpace));
        }
        */
    }
}
