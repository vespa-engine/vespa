package ai.vespa.lemminx;

import java.util.List;
import java.util.logging.Logger;

import org.eclipse.lemminx.dom.DOMAttr;
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
import org.w3c.dom.Node;

public class DiagnosticsParticipant implements IDiagnosticsParticipant {
    private static final Logger logger = Logger.getLogger(DiagnosticsParticipant.class.getName());

    private static final String DIAGNOSTIC_SOURCE = "LemMinX Vespa Extension";
    private record DiagnosticsContext(DOMDocument xmlDocument, List<Diagnostic> diagnostics, boolean hasSetupWorkspace) {}

    @Override
    public void doDiagnostics(DOMDocument xmlDocument, List<Diagnostic> diagnostics,
            XMLValidationSettings validationSettings, CancelChecker cancelChecker) {
        DiagnosticsContext context = new DiagnosticsContext(xmlDocument, diagnostics, SchemaLSCommands.instance().hasSetupWorkspace());
        traverse(xmlDocument.getDocumentElement(), context);
    }

    private void traverse(DOMNode node, DiagnosticsContext context) {
        if (node instanceof DOMElement) {
            DOMElement element = (DOMElement)node;
            if (context.hasSetupWorkspace() && element.getTagName().equals("document")) {
                validateDocumentElement(element, context);
            }
        }
        for (DOMNode child : node.getChildren()) {
            traverse(child, context);
        }
    }

    private void validateDocumentElement(DOMElement element, DiagnosticsContext context) {
        DOMAttr typeAttribute = element.getAttributeNode("type");
        if (typeAttribute != null) {
            String docName = typeAttribute.getValue();
            Range range = XMLPositionUtility.createRange(typeAttribute.getStart(), typeAttribute.getEnd(), context.xmlDocument);

            // TODO: (possibly) slow blocking call. Could be grouped
            List<Location> locations = SchemaLSCommands.instance().findSchemaDefinition(docName);
            if (locations.isEmpty()) {
                context.diagnostics.add(new Diagnostic(
                            range, 
                            "Document " + docName + " does not exist in the current application.", 
                            DiagnosticSeverity.Warning, 
                            DIAGNOSTIC_SOURCE
                            ));
            }
        }
    }
}
