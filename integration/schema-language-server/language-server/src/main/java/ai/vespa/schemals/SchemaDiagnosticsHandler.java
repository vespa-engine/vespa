package ai.vespa.schemals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.services.LanguageClient;

import ai.vespa.schemals.common.SchemaDiagnostic.DiagnosticCode;

/**
 * SchemaDiagnosticHandler is a wrapper for publishing diagnostics to the client
 */
public class SchemaDiagnosticsHandler {
    private LanguageClient client;
    private Map<String, List<Diagnostic>> currentDocumentDiagnostics;

    public SchemaDiagnosticsHandler() {
        currentDocumentDiagnostics = new HashMap<>();
    }

    public void connectClient(LanguageClient client) {
        this.client = client;
    }

    public void clearDiagnostics(String fileURI) {
        publishDiagnostics(fileURI, new ArrayList<Diagnostic>());
        currentDocumentDiagnostics.remove(fileURI);
    }

    public void publishDiagnostics(String fileURI, List<Diagnostic> diagnostics) {
        insertDocumentIfNotExists(fileURI);
        currentDocumentDiagnostics.get(fileURI).clear();
        currentDocumentDiagnostics.get(fileURI).addAll(diagnostics);
        client.publishDiagnostics(
            new PublishDiagnosticsParams(
                fileURI,
                diagnostics
            )
        );
    }

    public void resetUndefinedSymbolDiagnostics(String fileURI, List<Diagnostic> newUndefinedSymbolDiagnostics) {
        Predicate<Diagnostic> undefinedSymbolPredicate = (diagnostic -> diagnostic.getCode().getRight().equals(DiagnosticCode.UNDEFINED_SYMBOL.ordinal()));

        insertDocumentIfNotExists(fileURI);
        List<Diagnostic> currentDiagnostics = currentDocumentDiagnostics.get(fileURI);
        currentDiagnostics.removeIf(undefinedSymbolPredicate);
        currentDiagnostics.addAll(newUndefinedSymbolDiagnostics.stream().filter(undefinedSymbolPredicate).toList());

        // Resend to update on client side
        if (client != null)
            client.publishDiagnostics(
                new PublishDiagnosticsParams(
                    fileURI,
                    currentDiagnostics
                )
            );
    }

    private void insertDocumentIfNotExists(String fileURI) {
        if (!currentDocumentDiagnostics.containsKey(fileURI)) {
            currentDocumentDiagnostics.put(fileURI, new ArrayList<>());
        }
    }
}
