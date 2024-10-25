package ai.vespa.lemminx;

import java.util.List;
import java.util.logging.Logger;

import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.extensions.contentmodel.settings.XMLValidationSettings;
import org.eclipse.lemminx.services.extensions.diagnostics.IDiagnosticsParticipant;
import org.eclipse.lemminx.utils.XMLPositionUtility;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

public class DiagnosticsParticipant implements IDiagnosticsParticipant {
    private static final Logger logger = Logger.getLogger(DiagnosticsParticipant.class.getName());

    @Override
    public void doDiagnostics(DOMDocument xmlDocument, List<Diagnostic> diagnostics,
            XMLValidationSettings validationSettings, CancelChecker cancelChecker) {
        traverse(xmlDocument, xmlDocument.getDocumentElement(), diagnostics);
    }

    private void traverse(DOMDocument xmlDocument, DOMNode node, List<Diagnostic> diagnostics) {
        if (node instanceof DOMElement) {
            DOMElement element = (DOMElement)node;
            // Diagnostics here
        }
        for (DOMNode child : node.getChildren()) {
            traverse(xmlDocument, child, diagnostics);
        }
    }
}
