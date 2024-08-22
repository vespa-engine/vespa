package ai.vespa.lemminxvespa.participant;

import java.io.PrintStream;
import java.util.List;

import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.services.extensions.diagnostics.IDiagnosticsParticipant;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

import ai.vespa.lemminxvespa.util.LoggerUtils;

/**
 * DiagnosticsParticipant
 */
public class DiagnosticsParticipant implements IDiagnosticsParticipant {

    private PrintStream logger;

    public DiagnosticsParticipant(PrintStream logger) {
        this.logger = logger;
        logger.println("Created participant");
    }

	@Override
	public void doDiagnostics(DOMDocument xmlDocument, List<Diagnostic> diagnostics, CancelChecker monitor) {
        logger.println("Do diagnostics");
        diagnostics.add(new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "Helloo", DiagnosticSeverity.Error, "VESPALEMMINX"));
	}
}
