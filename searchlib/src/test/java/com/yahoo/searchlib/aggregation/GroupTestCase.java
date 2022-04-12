// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.searchlib.expression.AggregationRefNode;
import com.yahoo.searchlib.expression.ConstantNode;
import com.yahoo.searchlib.expression.ExpressionNode;
import com.yahoo.searchlib.expression.IntegerResultNode;
import com.yahoo.searchlib.expression.NegateFunctionNode;
import com.yahoo.vespa.objects.BufferSerializer;
import com.yahoo.vespa.objects.Identifiable;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class GroupTestCase {

    @Test
    public void requireThatAggregationResultsCanBeAdded() {
        Group group = new Group();
        AggregationResult res = new AverageAggregationResult();
        group.addAggregationResult(res);
        assertEquals(1, group.getAggregationResults().size());
        assertSame(res, group.getAggregationResults().get(0));
    }

    @Test
    public void requireThatAggregationResultListIsImmutable() {
        Group group = new Group();
        try {
            group.getAggregationResults().add(new AverageAggregationResult());
            fail();
        } catch (UnsupportedOperationException e) {

        }
    }

    @Test
    public void requireThatOrderByExpressionsCanBeAdded() {
        Group group = new Group();
        ExpressionNode foo = new ConstantNode(new IntegerResultNode(6));
        group.addOrderBy(foo, true);
        assertEquals(1, group.getOrderByExpressions().size());
        assertSame(foo, group.getOrderByExpressions().get(0));
        assertEquals(List.of(1), group.getOrderByIndexes());

        ExpressionNode bar = new ConstantNode(new IntegerResultNode(9));
        group.addOrderBy(bar, false);
        assertEquals(2, group.getOrderByExpressions().size());
        assertSame(bar, group.getOrderByExpressions().get(1));
        assertEquals(Arrays.asList(1, -2), group.getOrderByIndexes());
    }

    @Test
    public void requireThatOrderByListsAreImmutable() {
        Group group = new Group();
        try {
            group.getOrderByExpressions().add(new ConstantNode(new IntegerResultNode(69)));
            fail();
        } catch (UnsupportedOperationException e) {

        }
        try {
            group.getOrderByIndexes().add(69);
            fail();
        } catch (UnsupportedOperationException e) {

        }
    }

    @Test
    public void requireThatAddOrderByAddsAggregationResult() {
        Group group = new Group();
        AggregationResult res = new MinAggregationResult();
        group.addOrderBy(res, true);
        assertEquals(1, group.getAggregationResults().size());
        assertSame(res, group.getAggregationResults().get(0));
    }

    @Test
    public void requireThatAddOrderByDoesNotAddDuplicateAggregationResult() {
        Group group = new Group();
        AggregationResult res = new MinAggregationResult();
        group.addAggregationResult(res);
        group.addOrderBy(res, true);
        assertEquals(1, group.getAggregationResults().size());
        assertSame(res, group.getAggregationResults().get(0));
    }

    @Test
    public void requireThatAddOrderByIgnoresAggregationResultTagWhenMatching() {
        Group group = new Group();
        AggregationResult foo = new MinAggregationResult();
        foo.setTag(6);
        group.addAggregationResult(foo);
        AggregationResult bar = new MinAggregationResult();
        bar.setTag(9);
        group.addOrderBy(bar, true);
        assertEquals(1, group.getAggregationResults().size());
        assertSame(foo, group.getAggregationResults().get(0));
        assertEquals(6, foo.getTag());
    }

    @Test
    public void requireThatAddOrderByDoesNotModifyTagOfNewAggregationResult() {
        Group group = new Group();
        AggregationResult foo = new MinAggregationResult();
        foo.setTag(6);
        group.addAggregationResult(foo);
        AggregationResult bar = new MaxAggregationResult();
        bar.setTag(9);
        group.addOrderBy(bar, true);
        assertEquals(2, group.getAggregationResults().size());
        assertSame(foo, group.getAggregationResults().get(0));
        assertEquals(6, foo.getTag());
        assertSame(bar, group.getAggregationResults().get(1));
        assertEquals(9, bar.getTag());
    }

    @Test
    public void requireThatAddOrderByAddsReferencedAggregationResult() {
        Group group = new Group();
        AggregationResult res = new MinAggregationResult();
        group.addOrderBy(new AggregationRefNode(res), true);
        assertEquals(1, group.getAggregationResults().size());
        assertSame(res, group.getAggregationResults().get(0));
    }

    @Test
    public void requireThatAddOrderByDoesNotAddDuplicateReferencedAggregationResult() {
        Group group = new Group();
        AggregationResult res = new MinAggregationResult();
        group.addAggregationResult(res);
        group.addOrderBy(new AggregationRefNode(res), true);
        assertEquals(1, group.getAggregationResults().size());
        assertSame(res, group.getAggregationResults().get(0));
    }

    @Test
    public void requireThatAddOrderByAddsDeepReferencedAggregationResult() {
        Group group = new Group();
        AggregationResult res = new MinAggregationResult();
        group.addOrderBy(new NegateFunctionNode(new AggregationRefNode(res)), true);
        assertEquals(1, group.getAggregationResults().size());
        assertSame(res, group.getAggregationResults().get(0));
    }

    @Test
    public void requireThatAddOrderByDoesNotAddDuplicateDeepReferencedAggregationResult() {
        Group group = new Group();
        AggregationResult res = new MinAggregationResult();
        group.addAggregationResult(res);
        group.addOrderBy(new NegateFunctionNode(new AggregationRefNode(res)), true);
        assertEquals(1, group.getAggregationResults().size());
        assertSame(res, group.getAggregationResults().get(0));
    }

    @Test
    public void requireThatAddOrderByResolvesReferenceIndex() {
        Group group = new Group();
        AggregationResult res = new MinAggregationResult();
        group.addAggregationResult(res);
        group.addOrderBy(new AggregationRefNode(res), true);
        assertEquals(1, group.getOrderByExpressions().size());
        AggregationRefNode ref = (AggregationRefNode)group.getOrderByExpressions().get(0);
        assertEquals(0, ref.getIndex());
        assertSame(res, ref.getExpression());
    }

    @Test
    public void requireThatAddOrderByResolvesDeepReferenceIndex() {
        Group group = new Group();
        AggregationResult res = new MinAggregationResult();
        group.addAggregationResult(res);
        group.addOrderBy(new NegateFunctionNode(new AggregationRefNode(res)), true);
        assertEquals(1, group.getOrderByExpressions().size());
        AggregationRefNode ref = (AggregationRefNode)((NegateFunctionNode)group.getOrderByExpressions().get(0)).getArg();
        assertEquals(0, ref.getIndex());
        assertSame(res, ref.getExpression());
    }

    @Test
    public void requireThatAddOrderByResolvesReferenceResult() {
        Group group = new Group();
        AggregationResult res = new MinAggregationResult();
        group.addOrderBy(new AggregationRefNode(res), true);
        assertEquals(1, group.getOrderByExpressions().size());
        AggregationRefNode ref = (AggregationRefNode)group.getOrderByExpressions().get(0);
        assertEquals(0, ref.getIndex());
        assertSame(res, ref.getExpression());
    }

    @Test
    public void requireThatAddOrderByResolvesDeepReferenceResult() {
        Group group = new Group();
        AggregationResult res = new MinAggregationResult();
        group.addOrderBy(new NegateFunctionNode(new AggregationRefNode(res)), true);
        assertEquals(1, group.getOrderByExpressions().size());
        AggregationRefNode ref = (AggregationRefNode)((NegateFunctionNode)group.getOrderByExpressions().get(0)).getArg();
        assertEquals(0, ref.getIndex());
        assertSame(res, ref.getExpression());
    }

    @Test
    public void requireThatCloneResolvesAggregationRef() {
        Group group = new Group();
        AggregationResult res = new MinAggregationResult();
        group.addOrderBy(new AggregationRefNode(res), true);
        group = group.clone();

        assertEquals(1, group.getOrderByExpressions().size());
        AggregationRefNode ref = (AggregationRefNode)group.getOrderByExpressions().get(0);
        assertEquals(0, ref.getIndex());
        assertEquals(res, ref.getExpression());
        assertNotSame(res, ref.getExpression());
    }

    @Test
    public void requireThatDeserializeResolvesAggregationRef() {
        Group group = new Group();
        AggregationResult res = new MinAggregationResult();
        group.addOrderBy(new AggregationRefNode(res), true);
        BufferSerializer buf = new BufferSerializer();
        group.serializeWithId(buf);
        buf.flip();
        group = (Group)Identifiable.create(buf);

        assertEquals(1, group.getOrderByExpressions().size());
        AggregationRefNode ref = (AggregationRefNode)group.getOrderByExpressions().get(0);
        assertEquals(0, ref.getIndex());
        assertEquals(res, ref.getExpression());
        assertNotSame(res, ref.getExpression());
    }
}
