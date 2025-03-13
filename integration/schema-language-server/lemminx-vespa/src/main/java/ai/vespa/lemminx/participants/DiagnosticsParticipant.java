package ai.vespa.lemminx.participants;

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.eclipse.lemminx.dom.DOMAttr;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.dom.parser.TokenType;
import org.eclipse.lemminx.extensions.contentmodel.settings.XMLValidationSettings;
import org.eclipse.lemminx.services.extensions.diagnostics.IDiagnosticsParticipant;
import org.eclipse.lemminx.utils.XMLPositionUtility;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.thaiopensource.util.PropertyMap;
import com.thaiopensource.util.PropertyMapBuilder;
import com.thaiopensource.validate.ValidateProperty;
import com.thaiopensource.validate.ValidationDriver;
import com.thaiopensource.validate.auto.AutoSchemaReader;
import com.yahoo.config.application.XmlPreProcessor;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Tags;

import ai.vespa.lemminx.ServicesURIResolverExtension;
import ai.vespa.lemminx.VespaExtension;
import ai.vespa.lemminx.command.SchemaLSCommands;

/**
 * DiagnosticsParticipant that performs validation after Vespa Preprocessing.
 * Due to Lemminx' implementation of the DOM and parser, it seems impossible to run 
 * preprocessing on a parsed DOM. This is sad because it means we cannot connect preprocessing 
 * errors to their locations in the document as it was prior to preprocessing.
 *
 * For now the workaround is to remove existing diagnostics (from Lemminx validation) that no longer
 * exist after the preprocessing step. To identify equal diagnostics, string hashing of the 
 * error messages is used, because that is the only information left from the {@link SAXParseException}
 * objects after Lemminx has validated.
 *
 * This strategy makes the validation "nicer" than it should. Some errors will be removed
 * even though they are legit due to either string mismatch or because the choice of deployment 
 * settings is arbitrary at the moment. This is at the benefit of not showing errors on valid services.xml.
 */
public class DiagnosticsParticipant implements IDiagnosticsParticipant {
    private static final Logger logger = Logger.getLogger(DiagnosticsParticipant.class.getName());

    private static final String DIAGNOSTIC_SOURCE = "LemMinX Vespa Extension";
    private record DiagnosticsContext(DOMDocument xmlDocument, List<Diagnostic> diagnostics, boolean hasSetupWorkspace) {}

    static enum DiagnosticCode {
        GENERIC,
        DOCUMENT_DOES_NOT_EXIST
    }

    private final static DiagnosticCode[] diagnosticCodeValues = DiagnosticCode.values();
    private Set<String> validationErrorSet = new HashSet<>();
    private ValidationDriver schemaValidationDriver;

    public DiagnosticsParticipant(ServicesURIResolverExtension uriResolverExtension) {
        setupValidationDriver(uriResolverExtension.getSchemaURI());
    }

    @Override
    public void doDiagnostics(DOMDocument xmlDocument, List<Diagnostic> diagnostics,
            XMLValidationSettings validationSettings, CancelChecker cancelChecker) {

        if (!VespaExtension.match(xmlDocument))
            return;

        this.validationErrorSet.clear();

        // Fills the validationErrorSet with "actual" errors, i.e. errors that still occur after 
        // preprocessing.
        preprocessAndValidateDocument(xmlDocument);

        diagnostics.removeIf(diagnostic -> !this.validationErrorSet.contains(diagnostic.getMessage().trim()));

        DiagnosticsContext context = new DiagnosticsContext(xmlDocument, diagnostics, SchemaLSCommands.instance().hasSetupWorkspace());
        traverse(xmlDocument.getDocumentElement(), context);
    }

    public static DiagnosticCode codeFromInt(int code) {
        if (code < 0 || diagnosticCodeValues.length <= code) return DiagnosticCode.GENERIC;
        return diagnosticCodeValues[code];
    }
    private void traverse(DOMNode node, DiagnosticsContext context) {
        if (node instanceof DOMElement) {
            DOMElement element = (DOMElement)node;
            if (context.hasSetupWorkspace() && "document".equals(element.getTagName())) {
                validateDocumentElement(element, context);
            }
        }
        for (DOMNode child : node.getChildren()) {
            traverse(child, context);
        }
    }

    private void validateDocumentElement(DOMElement element, DiagnosticsContext context) {
        DOMAttr typeAttribute = element.getAttributeNode("type");
        if (typeAttribute != null && typeAttribute.getValue() != null) {
            String docName = typeAttribute.getValue();
            Range range = XMLPositionUtility.createRange(typeAttribute.getStart(), typeAttribute.getEnd(), context.xmlDocument);

            // TODO: (possibly) slow blocking call. Could be grouped
            List<Location> locations = SchemaLSCommands.instance().findSchemaDefinition(docName);
            if (locations.isEmpty()) {
                Diagnostic diagnostic = new Diagnostic(
                    range, 
                    "Document " + docName + " does not exist in the current application.", 
                    DiagnosticSeverity.Warning, 
                    DIAGNOSTIC_SOURCE
                );
                diagnostic.setCode(DiagnosticCode.DOCUMENT_DOES_NOT_EXIST.ordinal());
                diagnostic.setData(docName);
                context.diagnostics.add(diagnostic);
            }
        }
    }

    private void preprocessAndValidateDocument(DOMDocument xmlDocument) {
        if (xmlDocument.getTextDocument() == null || xmlDocument.getTextDocument().getUri() == null) {
            logger.warning("Could not get document URI. Preprocessing will not be performed.");
            return;
        }
        URI documentURI = URI.create(xmlDocument.getTextDocument().getUri());
        File applicationDir = new File(documentURI).getParentFile();

        // The settings for the preprocessor are currently set arbitrarily.
        // They do not affect much of the diagnostics.
        // With more intelligent LSP from preprocessing a user could set these values somewhere 
        // and preview the modified XML.
        XmlPreProcessor preProcessor = new XmlPreProcessor(
            applicationDir,
            new StringReader(xmlDocument.getTextDocument().getText()),
            InstanceName.defaultName(),
            Environment.dev,
            RegionName.defaultName(),
            CloudName.DEFAULT,
            Tags.empty()
        );

        try {
            Document preprocessed = preProcessor.run();
            schemaValidationDriver.validate(new InputSource(new StringReader(documentAsString(preprocessed, false))));
        } catch (SAXException saxException) {
            // Should we check if it is instance of SAXParseException?
            validationErrorSet.add(saxException.getMessage().trim());
        } catch (Exception ex) {
            // Most exceptions are processed by CustomErrorHandler
            logger.warning("Exception occured during Vespa XML validation: " + ex.getMessage() + ", TYPE: " + ex.getClass().toString());
            return;
        }
    }

    /**
     * Helper for converting a {@link Document} to a String used to validate a document after preprocessing
     * @param document
     * @param prettyPrint
     * @return
     * @throws TransformerException
     */
    static String documentAsString(Document document, boolean prettyPrint) throws TransformerException {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        if (prettyPrint) {
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        } else {
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
        }
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        String[] lines = writer.toString().split("\n");
        var b = new StringBuilder();
        for (String line : lines)
            if ( ! line.isBlank())
                b.append(line).append("\n");
        return b.toString();
    }

    /**
     * @param validationSchemaURI Extracted from application JAR when LSP loads.
     */
    private void setupValidationDriver(String validationSchemaURI) {
        schemaValidationDriver = new ValidationDriver(PropertyMap.EMPTY, instanceProperties(), new AutoSchemaReader());
        try {
            InputSource inputSource = ValidationDriver.uriOrFileInputSource(validationSchemaURI);
            logger.info(inputSource.getSystemId());
            if (!schemaValidationDriver.loadSchema(inputSource)) {
                logger.info("Could not load schema");
                return;
            }
        } catch (Exception ex) {
            logger.warning("Exception when loading schema: " + ex.getMessage());
            return;
        }

    }

    private final CustomErrorHandler errorHandler = new CustomErrorHandler();
    private PropertyMap instanceProperties() {
        PropertyMapBuilder builder = new PropertyMapBuilder();
        builder.put(ValidateProperty.ERROR_HANDLER, errorHandler);
        return builder.toPropertyMap();
    }

    private class CustomErrorHandler implements ErrorHandler {
        public void warning(SAXParseException e) {
            validationErrorSet.add(e.getMessage().trim());
        }

        public void error(SAXParseException e) {
            validationErrorSet.add(e.getMessage().trim());
        }

        public void fatalError(SAXParseException e) {
            validationErrorSet.add(e.getMessage().trim());
        }
    }
}
