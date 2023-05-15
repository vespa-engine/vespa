// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application;

import java.util.logging.Level;
import com.yahoo.text.XML;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.transform.TransformerException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

/**
 * Handles getting properties from services.xml and replacing references to properties with their real values
 *
 * @author hmusum
 */
class PropertiesProcessor implements PreProcessor {

    private final static Logger log = Logger.getLogger(PropertiesProcessor.class.getName());
    private final LinkedHashMap<String, String> properties;

    PropertiesProcessor() {
        properties = new LinkedHashMap<>();
    }

    public Document process(Document input) throws TransformerException {
        Document doc = Xml.copyDocument(input);
        Document document = buildProperties(doc);
        applyProperties(document.getDocumentElement());
        return document;
    }

    private Document buildProperties(Document input) {
        NodeList list = input.getElementsByTagNameNS(XmlPreProcessor.preprocessNamespaceUri, "properties");
        while (list.getLength() > 0) {
            Element propertiesElement = (Element) list.item(0);
            Element parent = (Element) propertiesElement.getParentNode();
            for (Node node : XML.getChildren(propertiesElement)) {
                String propertyName = node.getNodeName();
                if (properties.containsKey(propertyName)) {
                    log.log(Level.WARNING, "Duplicate definition for property '" + propertyName + "' detected");
                }
                properties.put(propertyName, node.getTextContent());
            }
            parent.removeChild(propertiesElement);
            list = input.getElementsByTagNameNS(XmlPreProcessor.preprocessNamespaceUri, "properties");
        }
        return input;
    }

    private void applyProperties(Element parent) {
        NamedNodeMap attributes = parent.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node a = attributes.item(i);
            if (hasProperty(a)) {
                replaceAttributePropertyWithValue(a);
            }
        }

        if (XML.getChildren(parent).isEmpty() && parent.getTextContent() != null) {
            if (hasPropertyInElement(parent)) {
                replaceElementPropertyWithValue(parent);
            }
        }

        // Repeat for remaining children;
        for (Element child : XML.getChildren(parent)) {
            applyProperties(child);
        }
    }

    private void replaceAttributePropertyWithValue(Node a) {
        String propertyValue = a.getNodeValue();
        String replacedPropertyValue = replaceValue(propertyValue);
        a.setNodeValue(replacedPropertyValue);
    }

    private String replaceValue(String propertyValue) {
        // Use a list with keys sorted by length (longest key first)
        // Needed for replacing values where you have overlapping keys
        ArrayList<String> keys = new ArrayList<>(properties.keySet());
        keys.sort(Collections.reverseOrder(Comparator.comparing(String::length)));

        for (String key : keys) {
            String value = properties.get(key);
            // Try to find exact match first and since this is done with longest key
            // first, the else branch will only happen when there cannot be an exact
            // match, i.e. where you want to replace only parts of the attribute or node value
            if (propertyValue.equals("${" + key + "}")) {
                String regex = "\\$\\{" + key + "}";
                return propertyValue.replaceAll(regex, value);
            } else if (propertyValue.contains(key)) {
                return propertyValue.replaceAll("\\$\\{" + key + "}", value);
            }
        }
        throw new IllegalArgumentException("Unable to find property replace in " + propertyValue);
    }

    private void replaceElementPropertyWithValue(Node a) {
        String propertyValue = a.getTextContent();
        String replacedPropertyValue = replaceValue(propertyValue);
        a.setTextContent(replacedPropertyValue);
    }

    private static boolean hasProperty(Node node) {
        return hasProperty(node.getNodeValue());
    }

    private static boolean hasPropertyInElement(Node node) {
        return hasProperty(node.getTextContent());
    }

    private static boolean hasProperty(String s) {
        return s.matches("^.*\\$\\{.+}.*$");
    }

    public LinkedHashMap<String, String> getProperties() {
        return properties;
    }

}
