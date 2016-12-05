// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application;

import com.yahoo.io.IOUtils;
import com.yahoo.text.XML;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

/**
 * Handles preprocess:include statements and returns a Document which has all the include statements resolved
 *
 * @author hmusum
 * @since 5.22
 */
class IncludeProcessor implements PreProcessor {

    private final File application;
    public IncludeProcessor(File application) {
        this.application = application;

    }

    public Document process(Document input) throws IOException, TransformerException {
        Document doc = Xml.copyDocument(input);
        includeFile(application, doc.getDocumentElement());
        return doc;
    }

    private static void includeFile(File currentFolder, Element currentElement) throws IOException {
        NodeList list = currentElement.getElementsByTagNameNS(XmlPreProcessor.preprocessNamespaceUri, "include");
        while (list.getLength() > 0) {
            Element elem = (Element) list.item(0);
            Element parent = (Element) elem.getParentNode();
            String filename = elem.getAttribute("file");
            boolean required = ! elem.hasAttribute("required") || Boolean.parseBoolean(elem.getAttribute("required"));
            File file = new File(currentFolder, filename);

            Document subFile = IncludeProcessor.parseIncludeFile(file, parent.getTagName(), required);
            includeFile(file.getParentFile(), subFile.getDocumentElement());

            //System.out.println("document before merging: " + documentAsString(doc));
            IncludeProcessor.mergeInto(parent, XML.getChildren(subFile.getDocumentElement()));
            //System.out.println("document after merging: " + documentAsString(doc));
            parent.removeChild(elem);
            //System.out.println("document after removing child: " + documentAsString(doc));
            list = currentElement.getElementsByTagNameNS(XmlPreProcessor.preprocessNamespaceUri, "include");
        }
    }


    private static void mergeInto(Element destination, List<Element> subElements) {
        // System.out.println("merging " + subElements.size() + " elements into " + destination.getTagName());
        for (Element subElement : subElements) {
            Node copiedNode = destination.getOwnerDocument().importNode(subElement, true);
            destination.appendChild(copiedNode);
        }
    }

    private static Document parseIncludeFile(File file, String parentTagName, boolean required) throws IOException {
        StringWriter w = new StringWriter();
        final String startTag = "<" + parentTagName + " " + XmlPreProcessor.deployNamespace + "='" + XmlPreProcessor.deployNamespaceUri + "' " + XmlPreProcessor.preprocessNamespace + "='" + XmlPreProcessor.preprocessNamespaceUri + "'>";
        w.append(startTag);
        if (file.exists() || required) {
            w.append(IOUtils.readFile(file));
        }
        final String endTag = "</" + parentTagName + ">";
        w.append(endTag);
        return XML.getDocument(new StringReader(w.toString()));
    }

}
