package ai.vespa.schemals.parser;

import org.apache.xerces.parsers.XMLDocumentParser;
import org.apache.xerces.xni.Augmentations;
import org.apache.xerces.xni.NamespaceContext;
import org.apache.xerces.xni.QName;
import org.apache.xerces.xni.XMLAttributes;
import org.apache.xerces.xni.XMLLocator;
import org.apache.xerces.xni.XMLString;
import org.apache.xerces.xni.XNIException;

import ai.vespa.schemals.SchemaDiagnosticsHandler;
import ai.vespa.schemals.common.ClientLogger;

public class ServicesXMLParser extends XMLDocumentParser {
    private ClientLogger logger;
    private XMLLocator locator;
    private SchemaDiagnosticsHandler diagnosticsHandler;

    public ServicesXMLParser(ClientLogger logger, SchemaDiagnosticsHandler diagnosticsHandler) {
        this.logger = logger;
        this.diagnosticsHandler = diagnosticsHandler;
    }

    @Override
    public void startDocument(XMLLocator locator, String encoding, NamespaceContext namespaceContext, Augmentations augs)
            throws XNIException {
        super.startDocument(locator, encoding, namespaceContext, augs);
        logger.info("Starting document");
        this.locator = locator;
    }


    @Override
    public void startElement(QName element, XMLAttributes attributes, Augmentations augs) throws XNIException {
        super.startElement(element, attributes, augs);
        logger.info("Start element: " + element.toString() + " [" + locator.getLineNumber() + ", " + locator.getColumnNumber() + "]");
    }

    @Override
    public void endElement(QName element, Augmentations augs) throws XNIException {
        logger.info("End element: " + element.toString() + " [" + locator.getLineNumber() + ", " + locator.getColumnNumber() + "]");
        super.endElement(element, augs);
    }

    @Override
    public void characters(XMLString text, Augmentations augs) throws XNIException {
        super.characters(text, augs);

        logger.info("Characters: " + text.toString() + " [" + locator.getLineNumber() + ", " + locator.getColumnNumber() + "]");
    }
}
