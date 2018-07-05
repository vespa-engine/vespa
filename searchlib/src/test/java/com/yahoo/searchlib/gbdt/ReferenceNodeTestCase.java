// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.gbdt;

import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class ReferenceNodeTestCase {

    @Test
    public void requireThatAccessorsWork() {
        TreeNode lhs = new ResponseNode(6.0, null);
        TreeNode rhs = new ResponseNode(9.0, null);
        NumericFeatureNode node = new NumericFeatureNode("foo", new DoubleValue(6.9), null, lhs, rhs);
        assertEquals("foo", node.feature());
        assertEquals(6.9, node.value().asDouble(), 1E-6);
        assertSame(lhs, node.left());
        assertSame(rhs, node.right());
    }

    @Test
    public void requireThatRankingExpressionCanBeGenerated() {
        assertExpression("if (a < 0.0, b, c)", new NumericFeatureNode("a", new DoubleValue(0), null, new MyNode("b"), new MyNode("c")));
        assertExpression("if (d < 1.0, e, f)", new NumericFeatureNode("d", new DoubleValue(1), null, new MyNode("e"), new MyNode("f")));
        assertExpression("if (d < 1.0, e, f, 0.5)", new NumericFeatureNode("d", new DoubleValue(1), null, new MyNode("e", 1), new MyNode("f", 1)));
        assertExpression("if (d < 1.0, e, f, 0.75)", new NumericFeatureNode("d", new DoubleValue(1), null, new MyNode("e", 3), new MyNode("f", 1)));
    }

    @Test
    public void requireThatNodeCanBeGeneratedFromDomNode() throws Exception {
        String xml = "<Node feature='a' value='1'>\n" +
                     "    <Response value='2' />\n" +
                     "    <Response value='4' />\n" +
                     "</Node>\n";
        NumericFeatureNode node = (NumericFeatureNode)FeatureNode.fromDom(XmlHelper.parseXml(xml));
        assertEquals("a", node.feature());
        assertEquals(1, node.value().asDouble(), 1E-6);
        assertTrue(node.left() instanceof ResponseNode);
        assertEquals(2, ((ResponseNode)node.left()).value(), 1E-6);
        assertTrue(node.right() instanceof ResponseNode);
        assertEquals(4, ((ResponseNode)node.right()).value(), 1E-6);
    }

    @Test
    public void requireThatUnknownNodeThrowsException() throws Exception {
        String xml = "<Node feature='a' value='1'>\n" +
                     "    <Response value='2' />\n" +
                     "</Node>\n";
        try {
            TreeNode.fromDom(XmlHelper.parseXml(xml));
            fail();
        } catch (IllegalArgumentException e) {

        }
        xml = "<Node feature='a' value='1'>\n" +
              "    <Response value='2' />\n" +
              "    <Response value='4' />\n" +
              "    <Response value='8' />\n" +
              "</Node>\n";
        try {
            TreeNode.fromDom(XmlHelper.parseXml(xml));
            fail();
        } catch (IllegalArgumentException e) {

        }
    }

    private static void assertExpression(String expected, TreeNode node) {
        assertEquals(expected, node.toRankingExpression());
    }

    private static class MyNode extends TreeNode {

        final String str;

        MyNode(String str) {
            this(str, Optional.empty());
        }

        MyNode(String str, int samples) {
            super(Optional.of(samples));
            this.str = str;
        }

        MyNode(String str, Optional<Integer> samples) {
            super(samples);
            this.str = str;
        }

        @Override
        public String toRankingExpression() {
            return str;
        }
    }
}
