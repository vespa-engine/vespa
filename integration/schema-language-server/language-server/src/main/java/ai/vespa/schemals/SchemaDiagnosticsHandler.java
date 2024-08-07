package ai.vespa.schemals;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.services.LanguageClient;

/**
 * SchemaDiagnosticHandler is a wrapper for publishing diagnostics to the client
 */
public class SchemaDiagnosticsHandler {

    private PrintStream logger;
    private LanguageClient client;

    public SchemaDiagnosticsHandler(PrintStream logger) {
        this.logger = logger;
    }

    public void connectClient(LanguageClient client) {
        this.client = client;
    }

    public void clearDiagnostics(String fileURI) {
        publishDiagnostics(fileURI, new ArrayList<Diagnostic>());
    }

    public void publishDiagnostics(String fileURI, List<Diagnostic> diagnostics) {
        client.publishDiagnostics(
            new PublishDiagnosticsParams(
                fileURI,
                diagnostics
            )
        );
    }
}
