package ai.vespa.schemals;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;

/*
 * Convenience class for testing without having to construct a language client
 * */
public class TestSchemaDiagnosticsHandler extends SchemaDiagnosticsHandler {

    private List<Diagnostic> diagnosticsStore;

	public TestSchemaDiagnosticsHandler(PrintStream logger, List<Diagnostic> diagnosticsStore) {
		super(logger);
        this.diagnosticsStore = diagnosticsStore;
	}

    @Override
    public void publishDiagnostics(String fileURI, Range range, String message) {
        publishDiagnostics(
            fileURI,
            new ArrayList<Diagnostic>() {{
                add(new Diagnostic(range, message));
            }}
        );
    }

    @Override
    public void publishDiagnostics(String fileURI, ArrayList<Diagnostic> diagnostics) {
        diagnosticsStore.addAll(diagnostics);
        /*
        client.publishDiagnostics(
            new PublishDiagnosticsParams(
                fileURI,
                diagnostics
            )
        );
        */
    }
}
