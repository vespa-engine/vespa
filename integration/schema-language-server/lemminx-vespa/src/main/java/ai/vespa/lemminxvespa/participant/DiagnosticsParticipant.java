package ai.vespa.lemminxvespa.participant;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.xerces.parsers.SAXParser;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.extensions.contentmodel.participants.diagnostics.LSPErrorReporterForXML;
import org.eclipse.lemminx.extensions.contentmodel.participants.diagnostics.LSPSAXParser;
import org.eclipse.lemminx.extensions.contentmodel.participants.diagnostics.LSPXMLGrammarPool;
import org.eclipse.lemminx.extensions.contentmodel.settings.XMLValidationSettings;
import org.eclipse.lemminx.extensions.xerces.ReferencedGrammarDiagnosticsInfo;
import org.eclipse.lemminx.extensions.xerces.xmlmodel.XMLModelAwareParserConfiguration;
import org.eclipse.lemminx.services.extensions.diagnostics.IDiagnosticsParticipant;
import org.eclipse.lemminx.services.extensions.diagnostics.LSPContentHandler;
import org.eclipse.lemminx.utils.DOMUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.xml.sax.InputSource;

import ai.vespa.lemminxvespa.VespaExtension;
import ai.vespa.lemminxvespa.util.LoggerUtils;

/**
 * DiagnosticsParticipant
 */
public class DiagnosticsParticipant implements IDiagnosticsParticipant {

    private PrintStream logger;
    private final VespaExtension vespaExtension;

    public DiagnosticsParticipant(VespaExtension vespaExtension) {
        this.vespaExtension = vespaExtension;
    }

	@Override
	public void doDiagnostics(DOMDocument xmlDocument, List<Diagnostic> diagnostics, XMLValidationSettings settings, CancelChecker monitor) {
        vespaExtension.logger().println("Do diagnostics");
        diagnostics.add(new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "Helloo", DiagnosticSeverity.Error, "vespalemminx"));

        Map<String, ReferencedGrammarDiagnosticsInfo> referencedGrammarDiagnosticsInfoCache = new HashMap<>();
        LSPErrorReporterForXML reporterForXML = new LSPErrorReporterForXML(xmlDocument, diagnostics, vespaExtension.getContentModelManager(), settings.isRelatedInformation(), referencedGrammarDiagnosticsInfoCache);
        try {
            XMLModelAwareParserConfiguration config = new XMLModelAwareParserConfiguration();
            SAXParser parser = new LSPSAXParser(
                reporterForXML, 
                config,
                vespaExtension.getContentModelManager().getGrammarPool(), 
                xmlDocument);

            parser.setContentHandler(new LSPContentHandler(monitor));

            parser.setFeature("http://xml.org/sax/features/validation", true);
            InputSource input = DOMUtils.createInputSource(xmlDocument);
            parser.parse(input);

        } catch (Exception e) {
            e.printStackTrace(vespaExtension.logger());
        } finally {
            reporterForXML.endReport();
        }
	}
}
