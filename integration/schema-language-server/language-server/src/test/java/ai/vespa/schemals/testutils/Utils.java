package ai.vespa.schemals.testutils;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.context.EventDocumentContext;
import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.context.InvalidContextException;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.schemadocument.DocumentManager;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;

public class Utils {

    /*
     * Sets up objects and datastructures normally set up by the language server on launch.
     * Replaces the client-facing classes with test stubs. 
     */
    public record TestContext(
            TestSchemaMessageHandler messageHandler, 
            ClientLogger logger, 
            SchemaIndex schemaIndex, 
            TestSchemaDiagnosticsHandler diagnosticsHandler,
            SchemaDocumentScheduler scheduler) {

        public static TestContext create() {
            return withManagedDiagnostics(new ArrayList<>());
        }

        // Useful if produced diagnostics need to be cleared or otherwise managed by a test.
        public static TestContext withManagedDiagnostics(List<Diagnostic> managedDiagnostics) {
            TestSchemaMessageHandler messageHandler = new TestSchemaMessageHandler();
            TestLogger logger = new TestLogger(messageHandler);
            SchemaIndex schemaIndex = new SchemaIndex(logger);
            TestSchemaDiagnosticsHandler diagnosticsHandler = new TestSchemaDiagnosticsHandler(managedDiagnostics);
            SchemaDocumentScheduler scheduler = new SchemaDocumentScheduler(logger, diagnosticsHandler, schemaIndex, messageHandler);
            return new TestContext(messageHandler, logger, schemaIndex, diagnosticsHandler, scheduler);
        }

        public EventDocumentContext getDocumentContext(DocumentManager document) throws InvalidContextException {
            return new EventDocumentContext(
                scheduler(),
                schemaIndex(),
                messageHandler(),
                document.getVersionedTextDocumentIdentifier()
            );
        }

        public EventPositionContext getPositionContext(DocumentManager document, Position position) throws InvalidContextException {
            return new EventPositionContext(
                scheduler(),
                schemaIndex(),
                messageHandler(),
                document.getVersionedTextDocumentIdentifier(),
                position
            );
        }
    }

    public static long countErrors(List<Diagnostic> diagnostics) {
        return diagnostics.stream()
                          .filter(diag -> diag.getSeverity() == DiagnosticSeverity.Error || diag.getSeverity() == null)
                          .count();
    }

    public static String constructDiagnosticMessage(List<Diagnostic> diagnostics, int indent) {
        String message = "";
        for (var diagnostic : diagnostics) {
            String severityString = "";
            if (diagnostic.getSeverity() == null) severityString = "No Severity";
            else severityString = diagnostic.getSeverity().toString();

            Position start = diagnostic.getRange().getStart();
            message += "\n" + new String(new char[indent]).replace('\0', '\t') +
                "Diagnostic" + "[" + severityString + "]: \"" + diagnostic.getMessage() + "\", at position: (" + start.getLine() + ", " + start.getCharacter() + ")";

        }
        return message;
    }

    public static ParseContext createTestContext(String input, String fileName) {
        TestSchemaMessageHandler messageHandler = new TestSchemaMessageHandler();
        ClientLogger logger = new TestLogger(messageHandler);
        SchemaIndex schemaIndex = new SchemaIndex(logger);
        TestSchemaDiagnosticsHandler diagnosticsHandler = new TestSchemaDiagnosticsHandler(new ArrayList<>());
        SchemaDocumentScheduler scheduler = new SchemaDocumentScheduler(logger, diagnosticsHandler, schemaIndex, messageHandler);
        schemaIndex.clearDocument(fileName);
        ParseContext context = new ParseContext(input, logger, fileName, schemaIndex, scheduler);
        return context;
    }
}
