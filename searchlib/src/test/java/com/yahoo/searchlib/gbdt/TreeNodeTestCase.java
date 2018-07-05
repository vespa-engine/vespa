// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.gbdt;

import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class TreeNodeTestCase {

    @Test
    public void requireThatFeatureNodeCanBeGeneratedFromDomNode()
            throws ParserConfigurationException, IOException, SAXException
    {
        String xml = "<Node feature='a' value='1'>\n" +
                     "    <Response value='2' />\n" +
                     "    <Response value='4' />\n" +
                     "</Node>\n";
        TreeNode obj = TreeNode.fromDom(XmlHelper.parseXml(xml));
        assertTrue(obj instanceof FeatureNode);
        NumericFeatureNode node = (NumericFeatureNode)obj;
        assertEquals("a", node.feature());
        assertEquals(1, node.value().asDouble(), 1E-6);
        assertTrue(node.left() instanceof ResponseNode);
        assertEquals(2, ((ResponseNode)node.left()).value(), 1E-6);
        assertTrue(node.right() instanceof ResponseNode);
        assertEquals(4, ((ResponseNode)node.right()).value(), 1E-6);
    }

    @Test
    public void requireThatResponseNodeCanBeGeneratedFromDomNode()
            throws ParserConfigurationException, IOException, SAXException
    {
        String xml = "<Response value='1' />\n";
        TreeNode obj = TreeNode.fromDom(XmlHelper.parseXml(xml));
        assertTrue(obj instanceof ResponseNode);
        assertEquals(1, ((ResponseNode)obj).value(), 1E-6);
    }

    @Test
    public void requireThatUnknownNodeThrowsException()
            throws ParserConfigurationException, IOException, SAXException
    {
        try {
            TreeNode.fromDom(XmlHelper.parseXml("<Unknown />"));
            fail();
        } catch (UnsupportedOperationException e) {
            assertEquals("Unknown", e.getMessage());
        }
    }
}
