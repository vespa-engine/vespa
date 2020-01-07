// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.log.LogLevel;
import com.yahoo.path.Path;
import com.yahoo.text.XML;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import java.io.*;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Utilities for XML.
 *
 * @author hmusum
 */
public class Xml {
    private static final Logger log = Logger.getLogger(Xml.class.getPackage().toString());

    // Access to this needs to be synchronized (as it is in getDocumentBuilder() below)
    private static final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

    static {
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
    }

    public static Document getDocument(Reader reader) {
        Document doc;
        try {
            doc = getDocumentBuilder().parse(new InputSource(reader));
        } catch (SAXException | IOException e) {
            throw new IllegalArgumentException(e);
        }
        return doc;
    }

    /**
     * Creates a new XML document builder.
     *
     * @return A new DocumentBuilder instance, or null if we fail to get one.
     */
    private static synchronized DocumentBuilder getDocumentBuilder() {
        try {
            return factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            log.log(LogLevel.WARNING, "No XML parser available - " + e);
            return null;
        }
    }

    static DocumentBuilder getPreprocessDocumentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false); // XXE prevention
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setValidating(false);
        return factory.newDocumentBuilder();
    }

    static File getServices(File app) {
        return new File(app, "services.xml"); // TODO Do not hard-code
    }

    static Document copyDocument(Document input) throws TransformerException {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        DOMSource source = new DOMSource(input);
        DOMResult result = new DOMResult();
        transformer.transform(source, result);
        return (Document) result.getNode();
    }

    static String documentAsString(Document document, boolean prettyPrint) throws TransformerException {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        if (prettyPrint) {
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        } else {
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
        }
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }

    static String documentAsString(Document document) throws TransformerException {
        return documentAsString(document, false);
    }

    /**
     * Utility method to get an XML element from a reader
     *
     * @param reader the {@link Reader} to get an xml element from
     */
    public static Element getElement(Reader reader) {
        return XML.getDocument(reader).getDocumentElement();
    }

    /**
     * @return The root element of each xml file under pathFromAppRoot/ in the app package
     */
    public static List<Element> allElemsFromPath(ApplicationPackage app, String pathFromAppRoot) {
        List<Element> ret = new ArrayList<>();
        List<NamedReader> files = null;
        try {
            files = app.getFiles(Path.fromString(pathFromAppRoot), ".xml", true);
            for (NamedReader reader : files)
                ret.add(getElement(reader));
        } finally {
            NamedReader.closeAll(files);
        }
        return ret;
    }

    /**
     * Will get all sub-elements under parent named "name", just like XML.getChildren(). Then look under
     * pathFromAppRoot/ in the app package for XML files, parse them and append elements of the same name.
     *
     * @param parent parent XML node
     * @param name name of elements to merge
     * @param app an {@link ApplicationPackage}
     * @param pathFromAppRoot path from application root
     * @return list of all sub-elements with given name
     */
    public static List<Element> mergeElems(Element parent, String name, ApplicationPackage app, String pathFromAppRoot) {
        List<Element> children = XML.getChildren(parent, name);
        List<Element> allFromFiles = allElemsFromPath(app, pathFromAppRoot);
        for (Element fromFile : allFromFiles) {
            for (Element inThatFile : XML.getChildren(fromFile, name)) {
                children.add(inThatFile);
            }
        }
        return children;
    }
}
