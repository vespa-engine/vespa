// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.gbdt;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * @author Simon Thoresen Hult
 */
abstract class XmlHelper {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    public static Element parseXml(String xml)
            throws ParserConfigurationException, IOException, SAXException
    {
        return parseXmlStream(new ByteArrayInputStream(xml.getBytes(UTF8)));
    }

    public static Element parseXmlFile(String fileName)
            throws ParserConfigurationException, IOException, SAXException
    {
        return parseXmlStream(new FileInputStream(fileName));
    }

    public static Element parseXmlStream(InputStream in)
            throws ParserConfigurationException, IOException, SAXException
    {
        DocumentBuilderFactory factory = createDocumentBuilderFactory();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(in);
        return doc.getDocumentElement();
    }

    private static DocumentBuilderFactory createDocumentBuilderFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);

        // XXE prevention
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return factory;
    }

    public static String getAttributeText(Node node, String name) {
        Node valueNode = node.getAttributes().getNamedItem(name);
        if (valueNode == null) {
            throw new IllegalArgumentException("Missing '" + name + "' attribute in element '" +
                                               node.getNodeName() + "'.");
        }
        String valueText = valueNode.getTextContent();
        if (valueText == null || valueText.isEmpty()) {
            throw new IllegalArgumentException("Attribute '" + name + "' in element '" +
                                               node.getNodeName() + "' is empty.");
        }
        return valueText;
    }

    public static String getAttributeTextOrNull(Node node, String name) {
        Node valueNode = node.getAttributes().getNamedItem(name);
        if (valueNode == null) return null;
        return valueNode.getTextContent();
    }

    public static Optional<String> getOptionalAttributeText(Node node, String name) {
        Node valueNode = node.getAttributes().getNamedItem(name);
        if (valueNode == null) return Optional.empty();
        return Optional.of(valueNode.getTextContent());
    }

    public static Element getSingleElement(Node node, String name) {
        List<Element> children = getChildElements(node, name);
        if (children.isEmpty()) {
            if (name != null) {
                throw new IllegalArgumentException("Node '" + node.getNodeName() + "' has no '" + name + "' children.");
            } else {
                throw new IllegalArgumentException("Node '" + node.getNodeName() + "' has no children.");
            }
        }
        if (children.size() != 1) {
            if (name != null) {
                throw new IllegalArgumentException("Expected 1 '" + name + "' child, got " + children.size() + ".");
            } else {
                throw new IllegalArgumentException("Expected 1 child, got " + children.size() + ".");
            }
        }
        return children.get(0);
    }

    public static List<Element> getChildElements(Node node, String name) {
        NodeList children = node.getChildNodes();
        List<Element> lst = new LinkedList<>();
        for (int i = 0, len = children.getLength(); i < len; ++i) {
            Node child = children.item(i);
            if (!(child instanceof Element)) {
                continue;
            }
            if (name != null && !child.getNodeName().equalsIgnoreCase(name)) {
                continue;
            }
            lst.add((Element)child);
        }
        return lst;
    }
}
