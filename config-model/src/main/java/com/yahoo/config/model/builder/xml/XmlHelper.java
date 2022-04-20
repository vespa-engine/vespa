// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.builder.xml;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.text.XML;
import com.yahoo.yolean.Exceptions;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Static methods for helping dom building
 *
 * @author bratseth
 */
public final class XmlHelper {
    private static final Logger log = Logger.getLogger(XmlHelper.class.getPackage().toString());


    private static final String idReference = "idref";
    // Access to this needs to be synchronized (as it is in getDocumentBuilder() below)
    public static final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

    static {
        XmlHelper.factory.setNamespaceAware(true);
        // if use jdom and jaxen this will fail badly:
        XmlHelper.factory.setXIncludeAware(true);
    }

    private XmlHelper() {}

    public static String nullIfEmpty(String attribute) {
        if (attribute.isEmpty())
            return null;
        else
            return attribute;
    }

    /**
     * For searchers inside search chains, the id may be both a reference and an id at once, or just a reference.
     * In other cases, it is clear which one it is from context, so I think the difference is not worth bothering users
     * with, unless they are XML purists in which case they will have the option of writing this correctly.
     * - Jon
     */
    public static String getIdString(Element element) {
        String idString = element.getAttribute("id");
        if (idString == null || idString.trim().equals(""))
            idString = element.getAttribute(idReference);
        if (idString == null || idString.trim().equals(""))
            idString = element.getAttribute("ident");
        return idString;
    }

    public static ComponentId getId(Element element) {
        return new ComponentId(getIdString(element));
    }

    public static ComponentSpecification getIdRef(Element element) {
        return new ComponentSpecification(getIdString(element));
    }

    public static Document getDocument(Reader reader) {
        return getDocument(reader, "unknown source");
    }

    public static Document getDocument(Reader reader, String source) {
        Document doc;
        try {
            InputSource inputSource = new InputSource(reader);
            inputSource.setPublicId(source);
            doc = getDocumentBuilder().parse(inputSource);
        } catch (SAXException | IOException e) {
            throw new IllegalArgumentException(e);
        }
        return doc;
    }

    public static List<String> splitAndDiscardEmpty(String field, String regex) {
        List<String> ret = new ArrayList<>();
        for (String t : field.split(regex)) {
            if (!t.isEmpty()) {
                ret.add(t);
            }
        }
        return ret;
    }

    public static List<String> spaceSeparatedSymbols(String field) {
        return splitAndDiscardEmpty(field, " ");
    }

    public static Collection<String> spaceSeparatedSymbolsFromAttribute(Element spec, String name) {
        return spaceSeparatedSymbols(spec.getAttribute(name));
    }

    public static Collection<String> valuesFromElements(Element parent, String elementName) {
        List<String> symbols = new ArrayList<>();
        for (Element symbol : XML.getChildren(parent, elementName)) {
            symbols.add(XML.getValue(symbol).trim());
        }
        return symbols;
    }

    public static boolean isReference(Element element) {
        return element.hasAttribute(idReference);
    }

    /**
     * Creates a new XML document builder.
     *
     * @return A new DocumentBuilder instance, or null if we fail to get one.
     */
    public static synchronized DocumentBuilder getDocumentBuilder() {
        try {
            DocumentBuilder docBuilder = factory.newDocumentBuilder();
            docBuilder.setErrorHandler(new CustomErrorHandler(log));
            log.log(Level.FINE, "XML parser now operational!");
            return docBuilder;
        } catch (ParserConfigurationException e) {
            log.log(Level.WARNING, "No XML parser available - " + e);
            return null;
        }
    }

    public static Optional<String> getOptionalAttribute(Element element, String name) {
        return Optional.ofNullable(element.getAttribute(name)).filter(s -> !s.isEmpty());
    }

    public static Optional<Element> getOptionalChild(Element parent, String childName) {
        return Optional.ofNullable(XML.getChild(parent, childName));

    }

    public static Optional<String> getOptionalChildValue(Element parent, String childName) {
        Element child = XML.getChild(parent, childName);
        if (child == null) return Optional.empty();
        if (child.getFirstChild() == null) return Optional.empty();
        return Optional.ofNullable(child.getFirstChild().getNodeValue());
    }

    /** Error handler which will output name of source for warnings and errors */
    private static class CustomErrorHandler implements ErrorHandler {

        private final Logger logger;

        CustomErrorHandler(Logger logger) {
            super();
            this.logger = logger;
        }

        public void warning(SAXParseException e) {
            logger.log(Level.WARNING, message(e));
        }

        public void error(SAXParseException e) {
            throw new IllegalArgumentException(message(e));
        }

        public void fatalError(SAXParseException e) { throw new IllegalArgumentException(message(e)); }

        private String message(SAXParseException e) {
            String sourceId = e.getPublicId() == null ? "" : e.getPublicId();
            return "Invalid XML" + (sourceId.isEmpty() ? " (unknown source)" : " in " + sourceId) +
                    ": " + Exceptions.toMessageString(e) +
                    " [" + e.getLineNumber() + ":" + e.getColumnNumber() + "]";
        }

    }

}
