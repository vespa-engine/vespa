package ai.vespa.schemals.testutils;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.SchemaMessageHandler;
import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;

public class Utils {

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
        context.useDocumentIdentifiers();
        return context;
    }
}
