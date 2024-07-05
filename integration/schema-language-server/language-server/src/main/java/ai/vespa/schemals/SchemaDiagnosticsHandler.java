package ai.vespa.schemals;

import java.io.PrintStream;
import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.services.LanguageClient;

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

    public void publishDiagnostics(String fileURI, Range range, String message) {
        publishDiagnostics(
            fileURI,
            new ArrayList<Diagnostic>() {{
                add(new Diagnostic(range, message));
            }}
        );
    }

    public void publishDiagnostics(String fileURI, ArrayList<Diagnostic> diagnostics) {
        client.publishDiagnostics(
            new PublishDiagnosticsParams(
                fileURI,
                diagnostics
            )
        );
    }
}
