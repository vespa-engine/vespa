package ai.vespa.schemals.testutils;

import java.util.List;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.SchemaDiagnosticsHandler;

/*
 * Convenience class for testing without having to construct a language client
 * */
public class TestSchemaDiagnosticsHandler extends SchemaDiagnosticsHandler {

    private List<Diagnostic> diagnosticsStore;

	public TestSchemaDiagnosticsHandler(List<Diagnostic> diagnosticsStore) {
        this.diagnosticsStore = diagnosticsStore;
	}

    @Override
    public void publishDiagnostics(String fileURI, List<Diagnostic> diagnostics) {
        diagnosticsStore.addAll(diagnostics);
    }
}
