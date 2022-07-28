// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import org.junit.jupiter.api.Test;

import java.text.ChoiceFormat;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
@SuppressWarnings({ "rawtypes" })
public class BucketResolverTestCase {

    // --------------------------------------------------------------------------------
    //
    // Tests
    //
    // --------------------------------------------------------------------------------

    @Test
    void testResolve() {
        BucketResolver resolver = new BucketResolver();
        resolver.push(new StringValue("a"), true);
        try {
            resolver.resolve(new AttributeValue("foo"));
            fail();
        } catch (IllegalStateException e) {
            assertEquals("Missing to-limit of last bucket.", e.getMessage());
        }

        resolver.push(new StringValue("b"), false);
        PredefinedFunction fnc = resolver.resolve(new AttributeValue("foo"));
        assertNotNull(fnc);
        assertEquals(1, fnc.getNumBuckets());
        BucketValue exp = fnc.getBucket(0);
        assertNotNull(exp);
        assertTrue(exp.getFrom() instanceof StringValue);
        assertTrue(exp.getTo() instanceof StringValue);
        BucketValue val = exp;
        assertEquals("a", val.getFrom().getValue());
        assertEquals("b", val.getTo().getValue());

        resolver.push(new StringValue("c"), true);
        try {
            resolver.resolve(new AttributeValue("foo"));
            fail();
        } catch (IllegalStateException e) {
            assertEquals("Missing to-limit of last bucket.", e.getMessage());
        }

        resolver.push(new StringValue("d"), false);
        fnc = resolver.resolve(new AttributeValue("foo"));
        assertNotNull(fnc);
        assertEquals(2, fnc.getNumBuckets());
        assertNotNull(exp = fnc.getBucket(0));
        assertTrue(exp.getFrom() instanceof StringValue);
        assertTrue(exp.getTo() instanceof StringValue);
        val = exp;
        assertEquals("a", val.getFrom().getValue());
        assertEquals("b", val.getTo().getValue());
        assertNotNull(exp = fnc.getBucket(1));
        assertTrue(exp.getFrom() instanceof StringValue);
        assertTrue(exp.getTo() instanceof StringValue);
        val = exp;
        assertEquals("c", val.getFrom().getValue());
        assertEquals("d", val.getTo().getValue());
    }

    @Test
    void testBucketType() {
        checkPushFail(Arrays.asList((ConstantValue) new StringValue("a"), new LongValue(1L)),
                "Bucket type mismatch, expected 'StringValue' got 'LongValue'.");
        checkPushFail(Arrays.asList((ConstantValue) new StringValue("a"), new DoubleValue(1.0)),
                "Bucket type mismatch, expected 'StringValue' got 'DoubleValue'.");
        checkPushFail(Arrays.asList((ConstantValue) new LongValue(1L), new StringValue("a")),
                "Bucket type mismatch, expected 'LongValue' got 'StringValue'.");
        checkPushFail(Arrays.asList((ConstantValue) new LongValue(1L), new DoubleValue(1.0)),
                "Bucket type mismatch, expected 'LongValue' got 'DoubleValue'.");
        checkPushFail(Arrays.asList((ConstantValue) new DoubleValue(1.0), new StringValue("a")),
                "Bucket type mismatch, expected 'DoubleValue' got 'StringValue'.");
        checkPushFail(Arrays.asList((ConstantValue) new DoubleValue(1.0), new LongValue(1L)),
                "Bucket type mismatch, expected 'DoubleValue' got 'LongValue'.");
        checkPushFail(Arrays.asList((ConstantValue) new InfiniteValue(new Infinite(true)), new InfiniteValue(new Infinite(false))),
                "Bucket type mismatch, cannot both be infinity.");

    }

    @Test
    void testBucketOrder() {
        checkPushFail(Arrays.asList((ConstantValue) new LongValue(2L), new LongValue(1L)),
                "Bucket to-value can not be less than from-value.");
        checkPushFail(Arrays.asList((ConstantValue) new DoubleValue(2.0), new DoubleValue(1.0)),
                "Bucket to-value can not be less than from-value.");
        checkPushFail(Arrays.asList((ConstantValue) new StringValue("b"), new StringValue("a")),
                "Bucket to-value can not be less than from-value.");
    }

    public void assertBucketRange(BucketValue expected, ConstantValue from, boolean inclusiveFrom, ConstantValue to, boolean inclusiveTo) {
        BucketResolver resolver = new BucketResolver();
        resolver.push(from, inclusiveFrom);
        resolver.push(to, inclusiveTo);
        PredefinedFunction fnc = resolver.resolve(new AttributeValue("foo"));
        assertNotNull(fnc);
        BucketValue result = fnc.getBucket(0);
        assertEquals(result.getFrom().getValue(), expected.getFrom().getValue());
        assertEquals(result.getTo().getValue(), expected.getTo().getValue());
    }

    public void assertBucketOrder(BucketResolver resolver) {
        PredefinedFunction fnc = resolver.resolve(new AttributeValue("foo"));
        BucketValue prev = null;
        for (int i = 0; i < fnc.getNumBuckets(); i++) {
            BucketValue b = fnc.getBucket(i);
            if (prev != null) {
                assertTrue(prev.compareTo(b) < 0);
            }
            prev = b;
        }
    }

    @Test
    void requireThatBucketRangesWork() {
        BucketValue expected = new LongBucket(2, 5);
        assertBucketRange(expected, new LongValue(1), false, new LongValue(4), true);
        assertBucketRange(expected, new LongValue(1), false, new LongValue(5), false);
        assertBucketRange(expected, new LongValue(2), true, new LongValue(4), true);
        assertBucketRange(expected, new LongValue(2), true, new LongValue(5), false);


        BucketResolver resolver = new BucketResolver();
        resolver.push(new LongValue(1), true).push(new LongValue(2), false);
        resolver.push(new LongValue(2), true).push(new LongValue(4), true);
        resolver.push(new LongValue(4), false).push(new LongValue(5), false);
        resolver.push(new LongValue(5), false).push(new LongValue(8), true);
        assertBucketOrder(resolver);


        expected = new StringBucket("aba ", "bab ");
        assertBucketRange(expected, new StringValue("aba"), false, new StringValue("bab"), true);
        assertBucketRange(expected, new StringValue("aba"), false, new StringValue("bab "), false);
        assertBucketRange(expected, new StringValue("aba "), true, new StringValue("bab"), true);
        assertBucketRange(expected, new StringValue("aba "), true, new StringValue("bab "), false);

        resolver = new BucketResolver();
        resolver.push(new StringValue("aaa"), true).push(new StringValue("aab"), false);
        resolver.push(new StringValue("aab"), true).push(new StringValue("aac"), true);
        resolver.push(new StringValue("aac"), false).push(new StringValue("aad"), false);
        resolver.push(new StringValue("aad"), false).push(new StringValue("aae"), true);
        assertBucketOrder(resolver);

        RawBuffer r1 = new RawBuffer(new byte[]{0, 1, 3});
        RawBuffer r1next = new RawBuffer(new byte[]{0, 1, 3, 0});
        RawBuffer r2 = new RawBuffer(new byte[]{0, 2, 2});
        RawBuffer r2next = new RawBuffer(new byte[]{0, 2, 2, 0});
        RawBuffer r2nextnext = new RawBuffer(new byte[]{0, 2, 2, 0, 4});

        expected = new RawBucket(r1next, r2next);
        assertBucketRange(expected, new RawValue(r1), false, new RawValue(r2), true);
        assertBucketRange(expected, new RawValue(r1), false, new RawValue(r2next), false);
        assertBucketRange(expected, new RawValue(r1next), true, new RawValue(r2), true);
        assertBucketRange(expected, new RawValue(r1next), true, new RawValue(r2next), false);

        resolver = new BucketResolver();
        resolver.push(new RawValue(r1), true).push(new RawValue(r1next), false);
        resolver.push(new RawValue(r1next), true).push(new RawValue(r2), true);
        resolver.push(new RawValue(r2), false).push(new RawValue(r2next), false);
        resolver.push(new RawValue(r2next), false).push(new RawValue(r2nextnext), true);
        assertBucketOrder(resolver);

        double d1next = ChoiceFormat.nextDouble(1.414);
        double d2next = ChoiceFormat.nextDouble(3.14159);
        double d1 = ChoiceFormat.nextDouble(d1next);
        double d2 = ChoiceFormat.nextDouble(d2next);
        expected = new DoubleBucket(d1, d2);
        assertBucketRange(expected, new DoubleValue(d1next), false, new DoubleValue(d2next), true);
        assertBucketRange(expected, new DoubleValue(d1next), false, new DoubleValue(d2), false);
        assertBucketRange(expected, new DoubleValue(d1), true, new DoubleValue(d2next), true);
        assertBucketRange(expected, new DoubleValue(d1), true, new DoubleValue(d2), false);

        resolver = new BucketResolver();
        resolver.push(new DoubleValue(d1next), true).push(new DoubleValue(d1), false);
        resolver.push(new DoubleValue(d1), true).push(new DoubleValue(d2next), true);
        resolver.push(new DoubleValue(d2next), false).push(new DoubleValue(d2), false);
        resolver.push(new DoubleValue(d2), false).push(new DoubleValue(ChoiceFormat.nextDouble(d2)), true);
        assertBucketOrder(resolver);
    }

    // --------------------------------------------------------------------------------
    //
    // Utilities
    //
    // --------------------------------------------------------------------------------

    private static void checkPushFail(List<ConstantValue> args, String expectedException) {
        BucketResolver resolver = new BucketResolver();
        try {
            int i = 0;
            for (ConstantValue exp : args) {
                boolean inclusive = ((i % 2) == 0);
                resolver.push(exp, inclusive);
                i++;
            }
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals(expectedException, e.getMessage());
        }
    }
}
