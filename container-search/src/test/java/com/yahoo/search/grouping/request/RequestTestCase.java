// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class RequestTestCase {

    @Test
    void requireThatApiWorks() {
        GroupingOperation op = new AllOperation()
                .setGroupBy(new AttributeValue("foo"))
                .addOrderBy(new CountAggregator())
                .addChildren(Arrays.asList(new AllOperation(), new EachOperation()))
                .addChild(new EachOperation()
                        .addOutput(new CountAggregator())
                        .addOutput(new MinAggregator(new AttributeValue("bar")))
                        .addChild(new EachOperation()
                                .addOutput(new AddFunction(
                                        new LongValue(69),
                                        new AttributeValue("baz")))
                                .addOutput(new SummaryValue("cox"))));
        assertEquals("all(group(foo) order(count()) all() each() " +
                "each(output(count(), min(bar)) each(output(add(69, baz), summary(cox)))))",
                op.toString());
        op.resolveLevel(1);

        GroupingExpression exp = op.getGroupBy();
        assertNotNull(exp);
        assertTrue(exp instanceof AttributeValue);
        assertEquals("foo", ((AttributeValue) exp).getAttributeName());
        assertEquals(1, op.getNumOrderBy());
        assertNotNull(exp = op.getOrderBy(0));
        assertTrue(exp instanceof CountAggregator);

        assertEquals(3, op.getNumChildren());
        assertTrue(op.getChild(0) instanceof AllOperation);
        assertTrue(op.getChild(1) instanceof EachOperation);
        assertNotNull(op = op.getChild(2));
        assertTrue(op instanceof EachOperation);
        assertEquals(2, op.getNumOutputs());
        assertNotNull(exp = op.getOutput(0));
        assertTrue(exp instanceof CountAggregator);
        assertNotNull(exp = op.getOutput(1));
        assertTrue(exp instanceof MinAggregator);
        assertNotNull(exp = ((MinAggregator) exp).getExpression());
        assertTrue(exp instanceof AttributeValue);
        assertEquals("bar", ((AttributeValue) exp).getAttributeName());

        assertEquals(1, op.getNumChildren());
        assertNotNull(op = op.getChild(0));
        assertTrue(op instanceof EachOperation);
        assertEquals(2, op.getNumOutputs());
        assertNotNull(exp = op.getOutput(0));
        assertTrue(exp instanceof AddFunction);
        assertEquals(2, ((AddFunction) exp).getNumArgs());
        GroupingExpression arg = ((AddFunction) exp).getArg(0);
        assertNotNull(arg);
        assertTrue(arg instanceof LongValue);
        assertEquals(69L, ((LongValue) arg).getValue().longValue());
        assertNotNull(arg = ((AddFunction) exp).getArg(1));
        assertTrue(arg instanceof AttributeValue);
        assertEquals("baz", ((AttributeValue) arg).getAttributeName());
        assertNotNull(exp = op.getOutput(1));
        assertTrue(exp instanceof SummaryValue);
        assertEquals("cox", ((SummaryValue) exp).getSummaryName());
    }

    @Test
    void requireThatPredefinedApiWorks() {
        PredefinedFunction fnc = new LongPredefined(new AttributeValue("foo"),
                new LongBucket(1, 2),
                new LongBucket(3, 4));
        assertEquals(2, fnc.getNumBuckets());
        BucketValue bucket = fnc.getBucket(0);
        assertNotNull(bucket);
        assertTrue(bucket instanceof LongBucket);
        assertEquals(1L, bucket.getFrom().getValue());
        assertEquals(2L, bucket.getTo().getValue());

        assertNotNull(bucket = fnc.getBucket(1));
        assertTrue(bucket instanceof LongBucket);
        assertEquals(3L, bucket.getFrom().getValue());
        assertEquals(4L, bucket.getTo().getValue());
    }

    @Test
    void requireThatBucketIntegrityIsChecked() {
        try {
            new LongBucket(2, 1);
        } catch (IllegalArgumentException e) {
            assertEquals("Bucket to-value can not be less than from-value.", e.getMessage());
        }
        try {
            new LongPredefined(new AttributeValue("foo"),
                    new LongBucket(3, 4),
                    new LongBucket(1, 2));
        } catch (IllegalArgumentException e) {
            assertEquals("Buckets must be monotonically increasing, got bucket[3, 4> before bucket[1, 2>.",
                    e.getMessage());
        }
    }

    @Test
    void requireThatAliasWorks() {
        GroupingOperation all = new AllOperation();
        all.putAlias("myalias", new AttributeValue("foo"));
        GroupingExpression exp = all.getAlias("myalias");
        assertNotNull(exp);
        assertTrue(exp instanceof AttributeValue);
        assertEquals("foo", ((AttributeValue) exp).getAttributeName());

        GroupingOperation each = new EachOperation();
        all.addChild(each);
        assertNotNull(exp = each.getAlias("myalias"));
        assertTrue(exp instanceof AttributeValue);
        assertEquals("foo", ((AttributeValue) exp).getAttributeName());

        each.putAlias("myalias", new AttributeValue("bar"));
        assertNotNull(exp = each.getAlias("myalias"));
        assertTrue(exp instanceof AttributeValue);
        assertEquals("bar", ((AttributeValue) exp).getAttributeName());
    }

    @Test
    void testOrderBy() {
        GroupingOperation all = new AllOperation();
        all.addOrderBy(new AttributeValue("foo"));
        try {
            all.resolveLevel(0);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Operation 'all(order(foo))' can not order single hit.", e.getMessage());
        }
        all.resolveLevel(1);
        assertEquals(0, all.getOrderBy(0).getLevel());
    }

    @Test
    void testMax() {
        GroupingOperation all = new AllOperation();
        all.setMax(69);
        try {
            all.resolveLevel(0);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Operation 'all(max(69))' can not apply max to single hit.", e.getMessage());
        }
        all.resolveLevel(1);
    }

    @Test
    void testAccuracy() {
        GroupingOperation all = new AllOperation();
        all.setAccuracy(0.53);
        assertEquals((long) (100.0 * all.getAccuracy()), 53);
        try {
            all.setAccuracy(1.2);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Illegal accuracy '1.2'. Must be between 0 and 1.", e.getMessage());
        }
        try {
            all.setAccuracy(-0.5);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Illegal accuracy '-0.5'. Must be between 0 and 1.", e.getMessage());
        }
    }

    @Test
    void testLevelChange() {
        GroupingOperation all = new AllOperation();
        all.resolveLevel(0);
        assertEquals(0, all.getLevel());
        all.setGroupBy(new AttributeValue("foo"));
        all.resolveLevel(1);
        assertEquals(2, all.getLevel());

        GroupingOperation each = new EachOperation();
        try {
            each.resolveLevel(0);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Operation '" + each + "' can not operate on single hit.", e.getMessage());
        }
        each.resolveLevel(1);
        assertEquals(0, each.getLevel());
        each.setGroupBy(new AttributeValue("foo"));
        each.resolveLevel(2);
        assertEquals(2, each.getLevel());
    }

    @Test
    void testLevelInheritance() {
        GroupingOperation grandParent, parent, child, grandChild;
        grandParent = new AllOperation()
                .addChild(parent = new EachOperation()
                        .addChild(child = new AllOperation()
                                .addChild(grandChild = new EachOperation())));

        grandParent.resolveLevel(69);
        assertEquals(69, grandParent.getLevel());
        assertEquals(68, parent.getLevel());
        assertEquals(68, child.getLevel());
        assertEquals(67, grandChild.getLevel());
    }

    @Test
    void testLevelPropagation() {
        GroupingOperation all = new AllOperation()
                .setGroupBy(new AttributeValue("foo"))
                .addOrderBy(new MaxAggregator(new AttributeValue("bar")))
                .addChild(new EachOperation()
                        .addOutput(new MaxAggregator(new AttributeValue("baz"))));

        all.resolveLevel(1);
        assertEquals(0, all.getGroupBy().getLevel());
        assertEquals(1, all.getOrderBy(0).getLevel());
        assertEquals(1, all.getChild(0).getOutput(0).getLevel());
        assertEquals(0, ((AggregatorNode) all.getChild(0).getOutput(0)).getExpression().getLevel());
    }
}
