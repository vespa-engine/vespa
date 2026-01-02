// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for SwitchNode functionality.
 *
 * @author johsol
 */
public class SwitchNodeTestCase {

    @Test
    public void requireThatAccessorsWork() {
        var discriminant = new ReferenceNode("x");
        List<ExpressionNode> caseValues = List.of(new ConstantNode(new DoubleValue(1.0)), new ConstantNode(new DoubleValue(2.0)));
        List<ExpressionNode> caseResults = List.of(new ConstantNode(new DoubleValue(10.0)), new ConstantNode(new DoubleValue(20.0)));
        var defaultResult = new ConstantNode(new DoubleValue(0.0));

        SwitchNode node = new SwitchNode(discriminant, caseValues, caseResults, defaultResult);

        assertEquals(discriminant, node.getDiscriminant());
        assertEquals(caseValues, node.getCaseValues());
        assertEquals(caseResults, node.getCaseResults());
        assertEquals(defaultResult, node.getDefaultResult());
        assertEquals(2, node.getCaseValues().size());
    }

    @Test
    public void requireThatToIfNodeTransformsCorrectly() {
        // switch(x) { case 1: 10, case 2: 20, default: 0 }
        var switchNode = new SwitchNode(
            new ReferenceNode("x"),
            List.of(new ConstantNode(new DoubleValue(1.0)), new ConstantNode(new DoubleValue(2.0))),
            List.of(new ConstantNode(new DoubleValue(10.0)), new ConstantNode(new DoubleValue(20.0))),
            new ConstantNode(new DoubleValue(0.0))
        );

        ExpressionNode ifNode = switchNode.toIfNode();

        // Should produce: if(x == 1, 10, if(x == 2, 20, 0))
        assertTrue("Root should be IfNode", ifNode instanceof IfNode);
        IfNode rootIf = (IfNode) ifNode;

        // Check condition: x == 1
        assertTrue("Condition should be OperationNode", rootIf.getCondition() instanceof OperationNode);
        OperationNode condition1 = (OperationNode) rootIf.getCondition();
        assertEquals(Operator.equal, condition1.operators().get(0));

        // Check true branch: 10
        assertTrue("True branch should be ConstantNode", rootIf.getTrueExpression() instanceof ConstantNode);
        assertEquals(10.0, ((ConstantNode) rootIf.getTrueExpression()).getValue().asDouble(), 0.0001);

        // Check false branch: another if
        assertTrue("False branch should be IfNode", rootIf.getFalseExpression() instanceof IfNode);
        IfNode nestedIf = (IfNode) rootIf.getFalseExpression();

        // Check nested condition: x == 2
        assertTrue("Nested condition should be OperationNode", nestedIf.getCondition() instanceof OperationNode);
        OperationNode condition2 = (OperationNode) nestedIf.getCondition();
        assertEquals(Operator.equal, condition2.operators().get(0));

        // Check nested true branch: 20
        assertTrue("Nested true branch should be ConstantNode", nestedIf.getTrueExpression() instanceof ConstantNode);
        assertEquals(20.0, ((ConstantNode) nestedIf.getTrueExpression()).getValue().asDouble(), 0.0001);

        // Check nested false branch (default): 0
        assertTrue("Nested false branch should be ConstantNode", nestedIf.getFalseExpression() instanceof ConstantNode);
        assertEquals(0.0, ((ConstantNode) nestedIf.getFalseExpression()).getValue().asDouble(), 0.0001);
    }

    @Test
    public void requireThatToIfNodeEvaluatesCorrectly() {
        // switch(x) { case 100: 10000, case 50: 2500, case 1: 1, default: 0 }
        var switchNode = new SwitchNode(
            new ReferenceNode("x"),
            List.of(new ConstantNode(new DoubleValue(100.0)), new ConstantNode(new DoubleValue(50.0)), new ConstantNode(new DoubleValue(1.0))),
            List.of(new ConstantNode(new DoubleValue(10000.0)), new ConstantNode(new DoubleValue(2500.0)), new ConstantNode(new DoubleValue(1.0))),
            new ConstantNode(new DoubleValue(0.0))
        );

        ExpressionNode ifNode = switchNode.toIfNode();

        // Test evaluation for different values
        var context = new MapContext();

        // Test case 1: x = 100
        context.put("x", 100.0);
        assertEquals(10000.0, switchNode.evaluate(context).asDouble(), 0.0001);
        assertEquals(10000.0, ifNode.evaluate(context).asDouble(), 0.0001);

        // Test case 2: x = 50
        context.put("x", 50.0);
        assertEquals(2500.0, switchNode.evaluate(context).asDouble(), 0.0001);
        assertEquals(2500.0, ifNode.evaluate(context).asDouble(), 0.0001);

        // Test case 3: x = 1
        context.put("x", 1.0);
        assertEquals(1.0, switchNode.evaluate(context).asDouble(), 0.0001);
        assertEquals(1.0, ifNode.evaluate(context).asDouble(), 0.0001);

        // Test default: x = 99
        context.put("x", 99.0);
        assertEquals(0.0, switchNode.evaluate(context).asDouble(), 0.0001);
        assertEquals(0.0, ifNode.evaluate(context).asDouble(), 0.0001);
    }

    @Test
    public void requireThatToIfNodeHandlesSingleCase() {
        // switch(x) { case 5: 100, default: 0 }
        var switchNode = new SwitchNode(
            new ReferenceNode("x"),
            List.of(new ConstantNode(new DoubleValue(5.0))),
            List.of(new ConstantNode(new DoubleValue(100.0))),
            new ConstantNode(new DoubleValue(0.0))
        );

        ExpressionNode ifNode = switchNode.toIfNode();

        // Should produce: if(x == 5, 100, 0)
        assertTrue("Should be IfNode", ifNode instanceof IfNode);
        IfNode rootIf = (IfNode) ifNode;

        assertTrue("True branch should be ConstantNode", rootIf.getTrueExpression() instanceof ConstantNode);
        assertEquals(100.0, ((ConstantNode) rootIf.getTrueExpression()).getValue().asDouble(), 0.0001);

        assertTrue("False branch should be ConstantNode", rootIf.getFalseExpression() instanceof ConstantNode);
        assertEquals(0.0, ((ConstantNode) rootIf.getFalseExpression()).getValue().asDouble(), 0.0001);
    }

    @Test
    public void requireThatToIfNodeHandlesComplexDiscriminant() {
        // switch(a + b) { case 10: 1, default: 0 }
        var discriminant = new OperationNode(new ReferenceNode("a"), Operator.plus, new ReferenceNode("b"));
        var switchNode = new SwitchNode(
            discriminant,
            List.of(new ConstantNode(new DoubleValue(10.0))),
            List.of(new ConstantNode(new DoubleValue(1.0))),
            new ConstantNode(new DoubleValue(0.0))
        );

        ExpressionNode ifNode = switchNode.toIfNode();

        var context = new MapContext();
        context.put("a", 7.0);
        context.put("b", 3.0);

        // Both should evaluate to 1 when a+b = 10
        assertEquals(1.0, switchNode.evaluate(context).asDouble(), 0.0001);
        assertEquals(1.0, ifNode.evaluate(context).asDouble(), 0.0001);

        // Both should evaluate to 0 when a+b != 10
        context.put("b", 2.0);
        assertEquals(0.0, switchNode.evaluate(context).asDouble(), 0.0001);
        assertEquals(0.0, ifNode.evaluate(context).asDouble(), 0.0001);
    }

    @Test
    public void requireThatToStringWorks() {
        var switchNode = new SwitchNode(
            new ReferenceNode("x"),
            List.of(new ConstantNode(new DoubleValue(1.0)), new ConstantNode(new DoubleValue(2.0))),
            List.of(new ConstantNode(new DoubleValue(10.0)), new ConstantNode(new DoubleValue(20.0))),
            new ConstantNode(new DoubleValue(0.0))
        );

        String str = switchNode.toString();
        assertTrue("Should contain 'switch'", str.contains("switch"));
        assertTrue("Should contain 'case'", str.contains("case"));
        assertTrue("Should contain 'default'", str.contains("default"));
    }
}
