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
    // Represents the previous list of diagnostics sent to the client for a given document
    private Map<String, List<Diagnostic>> documentDiagnostics = new HashMap<>();

    public void connectClient(LanguageClient client) {
        this.client = client;
    }

    public void clearDiagnostics(String fileURI) {
        publishDiagnostics(fileURI, new ArrayList<Diagnostic>());
        documentDiagnostics.remove(fileURI);
    }

    public void publishDiagnostics(String fileURI, List<Diagnostic> diagnostics) {
        insertDocumentIfNotExists(fileURI);

        client.publishDiagnostics(
            new PublishDiagnosticsParams(
                fileURI,
                diagnostics
            )
        );

        documentDiagnostics.get(fileURI).clear();
        documentDiagnostics.get(fileURI).addAll(diagnostics);
    }

    public void replaceUndefinedSymbolDiagnostics(String fileURI, List<Diagnostic> unresolvedSymbolDiagnostics) {
        Predicate<Diagnostic> undefinedSymbolPredicate = 
            (diagnostic) -> (
                diagnostic.getCode().isRight() 
                && diagnostic.getCode().getRight().equals(DiagnosticCode.UNDEFINED_SYMBOL.ordinal()));

        insertDocumentIfNotExists(fileURI);
        documentDiagnostics.get(fileURI).removeIf(undefinedSymbolPredicate);
        documentDiagnostics.get(fileURI).addAll(unresolvedSymbolDiagnostics.stream().filter(undefinedSymbolPredicate).toList());

        publishDiagnostics(fileURI, List.copyOf(documentDiagnostics.get(fileURI)));
    }

    private void insertDocumentIfNotExists(String fileURI) {
        if (documentDiagnostics.containsKey(fileURI)) return;
        documentDiagnostics.put(fileURI, new ArrayList<>());
    }
}
