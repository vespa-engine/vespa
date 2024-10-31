package ai.vespa.schemals.testutils;

import java.util.List;
import java.util.function.Predicate;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.SchemaDiagnosticsHandler;
import ai.vespa.schemals.common.SchemaDiagnostic.DiagnosticCode;

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

    @Override
    public void resetUndefinedSymbolDiagnostics(String fileURI, List<Diagnostic> newUndefinedSymbolDiagnostics) {
        Predicate<Diagnostic> undefinedSymbolPredicate = (diagnostic -> diagnostic.getCode().getRight().equals(DiagnosticCode.UNDEFINED_SYMBOL.ordinal()));

        diagnosticsStore.removeIf(undefinedSymbolPredicate);
        diagnosticsStore.addAll(newUndefinedSymbolDiagnostics.stream().filter(undefinedSymbolPredicate).toList());
    }
}
