package ai.vespa.schemals.parser;

import org.apache.xerces.parsers.XMLDocumentParser;
import org.apache.xerces.xni.Augmentations;
import org.apache.xerces.xni.NamespaceContext;
import org.apache.xerces.xni.QName;
import org.apache.xerces.xni.XMLAttributes;
import org.apache.xerces.xni.XMLLocator;
import org.apache.xerces.xni.XMLResourceIdentifier;
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
    public void any(Augmentations augs) throws XNIException {
        // TODO Auto-generated method stub
        super.any(augs);
        logger.info("Any: " + " [" + locator.getLineNumber() + ", " + locator.getColumnNumber() + "]");
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

    @Override
    public void comment(XMLString text, Augmentations augs) throws XNIException {
        super.comment(text, augs);

        //logger.info("Comment: " + text.toString() + " [" + locator.getLineNumber() + ", " + locator.getColumnNumber() + "]");
    }

    @Override
    public void startGeneralEntity(String text, XMLResourceIdentifier arg1, String arg2, Augmentations augs)
            throws XNIException {
        super.startGeneralEntity(text, arg1, arg2, augs);
        logger.info("Entity start: " + text.toString() + " [" + locator.getLineNumber() + ", " + locator.getColumnNumber() + "]");
    }

    @Override
    public void endGeneralEntity(String text, Augmentations augs) throws XNIException {
        // TODO Auto-generated method stub
        super.endGeneralEntity(text, augs);
        logger.info("Entity end: " + text.toString() + " [" + locator.getLineNumber() + ", " + locator.getColumnNumber() + "]");
    }
}
