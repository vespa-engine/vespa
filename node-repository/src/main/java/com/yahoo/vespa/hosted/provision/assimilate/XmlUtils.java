// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.assimilate;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author vegard
*/
class XmlUtils {
    static String attributeOrDefault(Element element, String attributeName, String defaultValue) {
        String value = element.getAttribute(attributeName);
        return !value.isEmpty() ? value : defaultValue;
    }

    static Document parseXml(String filename) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new File(filename));
        }
        catch (ParserConfigurationException | SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    static Object evalXPath(Document doc, String xPathExpr, QName returnType) {
        try {
            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();
            XPathExpression expr = xpath.compile(xPathExpr);
            return expr.evaluate(doc, returnType);
        }
        catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reads hosts.xml into a map (alias to hostname)
     */
    static Map<String, String> getHostMapping(Document hostsXml) {
        Map<String, String> hostMapping = new HashMap<>();

        NodeList hostList = hostsXml.getElementsByTagName("host");
        for (int i = 0; i < hostList.getLength(); i++) {
            Element host = (Element) hostList.item(i);
            hostMapping.put(host.getElementsByTagName("alias").item(0).getTextContent(), host.getAttribute("name"));
        }

        return hostMapping;
    }
}
