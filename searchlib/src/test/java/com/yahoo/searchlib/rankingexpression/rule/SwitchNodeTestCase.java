// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.MapTypeContext;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests for SwitchNode data structure.
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
    public void requireThatTypeValidationAcceptsCompatibleResultTypes() {
        MapTypeContext context = new MapTypeContext();
        context.setType(ref("x1"), TensorType.fromSpec("tensor(x[])"));
        context.setType(ref("x2"), TensorType.fromSpec("tensor(x[10])"));

        // All case results have compatible types
        assertSwitchType("tensor(x[])", "switch(1) { case 1: query(x1), case 2: query(x2), default: query(x1) }", context);
    }

    @Test
    public void requireThatTypeValidationRejectsIncompatibleResultTypes() {
        MapTypeContext context = new MapTypeContext();
        context.setType(ref("x1"), TensorType.fromSpec("tensor(x[])"));
        context.setType(ref("y1"), TensorType.fromSpec("tensor(y[])"));

        // Case results have incompatible types (different dimensions)
        assertTypeError("switch(1) { case 1: query(x1), case 2: query(y1), default: query(x1) }", context);
    }

    @Test
    public void requireThatTypeValidationRejectsIncompatibleDefaultType() {
        MapTypeContext context = new MapTypeContext();
        context.setType(ref("x1"), TensorType.fromSpec("tensor(x[])"));
        context.setType(ref("y1"), TensorType.fromSpec("tensor(y[])"));

        // Default has incompatible type with case results
        assertTypeError("switch(1) { case 1: query(x1), default: query(y1) }", context);
    }

    @Test
    public void requireThatTypeValidationAcceptsCompatibleDiscriminantAndCases() {
        MapTypeContext context = new MapTypeContext();
        context.setType(ref("tensor1"), TensorType.fromSpec("tensor(x[10])"));

        // Discriminant and case values have compatible types
        assertSwitchType("tensor(x[10])", "switch(query(tensor1)) { case query(tensor1): query(tensor1), default: query(tensor1) }", context);
    }

    @Test
    public void requireThatTypeValidationRejectsIncompatibleDiscriminantAndCase() {
        MapTypeContext context = new MapTypeContext();
        context.setType(ref("tensor1"), TensorType.fromSpec("tensor(x[10])"));
        context.setType(ref("tensor2"), TensorType.fromSpec("tensor(y[5])"));

        // Discriminant and case value have incompatible types (different dimensions)
        assertTypeError("switch(query(tensor1)) { case query(tensor2): 1, default: 0 }", context);
    }

    private Reference ref(String name) {
        return Reference.simple("query", name);
    }

    private void assertSwitchType(String expectedType, String expression, TypeContext<Reference> context) {
        try {
            TensorType actualType = new RankingExpression(expression).type(context);
            assertEquals(TensorType.fromSpec(expectedType), actualType);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private void assertTypeError(String expression, TypeContext<Reference> context) {
        try {
            new RankingExpression(expression).type(context);
            fail("Expected type validation to fail for: " + expression);
        } catch (IllegalArgumentException expected) {
            // Expected - type validation correctly rejected the expression
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

}
