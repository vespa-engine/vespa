// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.gbdt;

import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class ResponseNodeTestCase {

    @Test
    public void requireThatAccessorsWork() {
        ResponseNode node = new ResponseNode(6.9, null);
        assertEquals(6.9, node.value(), 1E-6);
    }

    @Test
    public void requireThatRankingExpressionCanBeGenerated() {
        assertExpression("0.0", new ResponseNode(0, null));
        assertExpression("1.0", new ResponseNode(1, null));
    }

    @Test
    public void requireThatNodeCanBeGeneratedFromDomNode() throws ParserConfigurationException, IOException, SAXException {
        String xml = "<Response value='1' />\n";
        ResponseNode node = ResponseNode.fromDom(XmlHelper.parseXml(xml));
        assertEquals(1, node.value(), 1E-6);
    }

    private static void assertExpression(String expected, TreeNode node) {
        assertEquals(expected, node.toRankingExpression());
    }

}
