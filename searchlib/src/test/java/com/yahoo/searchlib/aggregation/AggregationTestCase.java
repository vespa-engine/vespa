// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.document.DocumentId;
import com.yahoo.document.GlobalId;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.searchlib.expression.*;
import com.yahoo.vespa.objects.BufferSerializer;
import com.yahoo.vespa.objects.Identifiable;
import com.yahoo.vespa.objects.ObjectOperation;
import com.yahoo.vespa.objects.ObjectPredicate;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author baldersheim
 */
public class AggregationTestCase {

    private static final double delta = 0.0000000001;

    @Test
    public void testSumAggregationResult() {
        SumAggregationResult a = new SumAggregationResult();
        a.setExpression(new AttributeNode("attributeA"));
        a.setSum(new IntegerResultNode(7));
        assertEquals(a.getSum().getInteger(), 7);
        SumAggregationResult b = (SumAggregationResult)serializeDeserialize(a);
        assertEquals(b.getSum().getInteger(), 7);
        b.merge(a);
        assertEquals(b.getSum().getInteger(), 14);
    }

    @Test
    public void testXorAggregationResult() {
        XorAggregationResult a = new XorAggregationResult(6);
        a.setExpression(new AttributeNode("attributeA"));
        assertEquals(a.getXor(), 6);
        a.setXor(7);
        assertEquals(a.getXor(), 7);
        XorAggregationResult b = (XorAggregationResult)serializeDeserialize(a);
        assertEquals(b.getXor(), 7);
        b.merge(a);
        assertEquals(b.getXor(), 0);
    }

    @Test
    public void testCountAggregationResult() {
        CountAggregationResult a = new CountAggregationResult(6);
        a.setExpression(new AttributeNode("attributeA"));
        assertEquals(a.getCount(), 6);
        a.setCount(7);
        assertEquals(a.getCount(), 7);
        CountAggregationResult b = (CountAggregationResult)serializeDeserialize(a);
        assertEquals(b.getCount(), 7);
        b.merge(a);
        assertEquals(b.getCount(), 14);
    }

    @Test
    public void testMinAggregationResult() {
        MinAggregationResult a = new MinAggregationResult(new IntegerResultNode(6));
        a.setExpression(new AttributeNode("attributeA"));
        assertEquals(a.getMin().getInteger(), 6);
        a.setMin(new IntegerResultNode(7));
        assertEquals(a.getMin().getInteger(), 7);
        MinAggregationResult b = (MinAggregationResult)serializeDeserialize(a);
        a.setMin(new IntegerResultNode(6));
        assertEquals(b.getMin().getInteger(), 7);
        b.merge(a);
        assertEquals(b.getMin().getInteger(), 6);
    }

    @Test
    public void testMaxAggregationResult() {
        MaxAggregationResult a = new MaxAggregationResult(new IntegerResultNode(6));
        a.setExpression(new AttributeNode("attributeA"));
        assertEquals(a.getMax().getInteger(), 6);
        a.setMax(new IntegerResultNode(7));
        assertEquals(a.getMax().getInteger(), 7);
        MaxAggregationResult b = (MaxAggregationResult)serializeDeserialize(a);
        a.setMax(new IntegerResultNode(6));
        assertEquals(b.getMax().getInteger(), 7);
        b.merge(a);
        assertEquals(b.getMax().getInteger(), 7);
    }

    @Test
    public void testAverageAggregationResult() {
        AverageAggregationResult a = new AverageAggregationResult(new FloatResultNode(72), 6);
        a.setExpression(new AttributeNode("attributeA"));
        assertEquals(a.getCount(), 6);
        a.setCount(8);
        assertEquals(a.getCount(), 8);
        AverageAggregationResult b = (AverageAggregationResult)serializeDeserialize(a);
        assertEquals(b.getCount(), 8);
        a.setCount(6);
        b.merge(a);
        assertEquals(b.getCount(), 14);
        assertEquals(b.getSum().getInteger(), 144);
    }

    private static boolean equals(Object a, Object b) {
        return a.equals(b);
    }

    private GlobalId createGlobalId(int docId) {
        return new GlobalId((new DocumentId("doc:test:" + docId)).getGlobalId());
    }

    @Test
    public void testFs4HitsAggregationResult() {
        double rank1 = 1;
        double rank2 = 2;
        assertEquals(new FS4Hit(1, createGlobalId(1), rank1), new FS4Hit(1, createGlobalId(1), rank1));
        assertFalse(equals(new FS4Hit(1, createGlobalId(1), rank1), new FS4Hit(2, createGlobalId(1), rank1)));
        assertFalse(equals(new FS4Hit(1, createGlobalId(1), rank1), new FS4Hit(1, createGlobalId(2), rank1)));
        assertFalse(equals(new FS4Hit(1, createGlobalId(1), rank1), new FS4Hit(1, createGlobalId(1), rank2)));

        HitsAggregationResult a = new HitsAggregationResult(5);
        assertEquals(5, a.getMaxHits());
        assertEquals(0, a.getHits().size());
        a.setExpression(new AttributeNode("attributeA"));
        a.addHit(new FS4Hit(1, createGlobalId(2), rank1));
        a.addHit(new FS4Hit(5, createGlobalId(7), rank2));
        assertEquals(2, a.getHits().size());
        HitsAggregationResult b = (HitsAggregationResult)serializeDeserialize(a);
        assertEquals(a, b);
        a.postMerge();
        assertEquals(2, a.getHits().size());
        assertEquals(2.0, a.getHits().get(0).getRank(), delta);
        a.setMaxHits(1).postMerge();
        assertEquals(1, a.getHits().size());
        assertEquals(2.0, a.getHits().get(0).getRank(), delta);

        HitsAggregationResult hits = new HitsAggregationResult(3)
            .addHit(new FS4Hit(1, createGlobalId(3), 1))
            .addHit(new FS4Hit(2, createGlobalId(2), 2))
            .addHit(new FS4Hit(3, createGlobalId(1), 3));
        Grouping request = new Grouping()
            .setRoot(new Group()
                     .addAggregationResult(hits.clone())
                     .addChild(new Group()
                               .addAggregationResult(hits.clone())
                               .addChild(new Group()
                                         .addAggregationResult(hits.clone())))
                     .addChild(new Group()
                               .addAggregationResult(hits.clone())
                               .addChild(new Group()
                                         .addAggregationResult(hits.clone())
                                         .addChild(new Group()
                                                   .addAggregationResult(hits.clone())))));
        assertFS4Hits(request, 0, 0, 3);
        assertFS4Hits(request, 1, 1, 6);
        assertFS4Hits(request, 2, 2, 6);
        assertFS4Hits(request, 3, 3, 3);
        assertFS4Hits(request, 4, 4, 0);

        assertFS4Hits(request, 0, 1, 9);
        assertFS4Hits(request, 0, 2, 15);
        assertFS4Hits(request, 0, 3, 18);
        assertFS4Hits(request, 0, 4, 18);
        assertFS4Hits(request, 1, 4, 15);
        assertFS4Hits(request, 2, 4, 9);
        assertFS4Hits(request, 3, 4, 3);

        assertFS4Hits(request, 1, 2, 12);
        assertFS4Hits(request, 2, 3, 9);
        assertFS4Hits(request, 3, 4, 3);
        assertFS4Hits(request, 4, 5, 0);
    }

    @Test
    public void testVdsHitsAggregationResult() {
        double rank1 = 1;
        double rank2 = 2;
        byte [] s1 = {'a','b','c'};
        byte [] s2 = {'n','o','e'};
        byte [] s3 = {'n','o','3'};
        assertEquals(new VdsHit("1", s1, rank1), new VdsHit("1", s1, rank1));
        assertFalse(equals(new VdsHit("1", s1, rank1), new VdsHit("2", s1, rank1)));
        assertFalse(equals(new VdsHit("1", s1, rank1), new VdsHit("1", s2, rank1)));
        assertFalse(equals(new VdsHit("1", s1, rank1), new VdsHit("1", s1, rank2)));

        HitsAggregationResult a = new HitsAggregationResult(5);
        assertEquals(5, a.getMaxHits());
        assertEquals(0, a.getHits().size());
        a.setExpression(new AttributeNode("attributeA"));
        a.addHit(new VdsHit("1", s2, rank1));
        HitsAggregationResult b = (HitsAggregationResult)serializeDeserialize(a);
        assertEquals(a, b);

        HitsAggregationResult hits = new HitsAggregationResult(3)
            .addHit(new VdsHit("1", s3, 1))
            .addHit(new VdsHit("2", s2, 2))
            .addHit(new VdsHit("3", s1, 3));
        Grouping request = new Grouping()
            .setRoot(new Group()
                     .addAggregationResult(hits.clone())
                     .addChild(new Group()
                               .addAggregationResult(hits.clone())
                               .addChild(new Group()
                                         .addAggregationResult(hits.clone())))
                     .addChild(new Group()
                               .addAggregationResult(hits.clone())
                               .addChild(new Group()
                                         .addAggregationResult(hits.clone())
                                         .addChild(new Group()
                                                   .addAggregationResult(hits.clone())))));
        assertVdsHits(request, 0, 0, 3);
        assertVdsHits(request, 1, 1, 6);
        assertVdsHits(request, 2, 2, 6);
        assertVdsHits(request, 3, 3, 3);
        assertVdsHits(request, 4, 4, 0);

        assertVdsHits(request, 0, 1, 9);
        assertVdsHits(request, 0, 2, 15);
        assertVdsHits(request, 0, 3, 18);
        assertVdsHits(request, 0, 4, 18);
        assertVdsHits(request, 1, 4, 15);
        assertVdsHits(request, 2, 4, 9);
        assertVdsHits(request, 3, 4, 3);

        assertVdsHits(request, 1, 2, 12);
        assertVdsHits(request, 2, 3, 9);
        assertVdsHits(request, 3, 4, 3);
        assertVdsHits(request, 4, 5, 0);
    }

    private void assertFS4Hits(Grouping request, int firstLevel, int lastLevel, int expected) {
        CountFS4Hits obj = new CountFS4Hits();
        request.setFirstLevel(firstLevel);
        request.setLastLevel(lastLevel);
        request.select(obj, obj);
        assertEquals(expected, obj.count);
    }

    private void assertVdsHits(Grouping request, int firstLevel, int lastLevel, int expected) {
        CountVdsHits obj = new CountVdsHits();
        request.setFirstLevel(firstLevel);
        request.setLastLevel(lastLevel);
        request.select(obj, obj);
        assertEquals(expected, obj.count);
    }

    private class CountFS4Hits implements ObjectPredicate, ObjectOperation {
        int count;
        public boolean check(Object obj) {
            return obj instanceof FS4Hit;
        }
        public void execute(Object obj) {
            ++count;
        }
    }

    private class CountVdsHits implements ObjectPredicate, ObjectOperation {
        int count;
        public boolean check(Object obj) {
            return obj instanceof VdsHit;
        }
        public void execute(Object obj) {
            ++count;
        }
    }

    @Test
    public void testGroup() {
        Group a = new Group();
        a.setId(new IntegerResultNode(17));
        a.addAggregationResult(new XorAggregationResult());
        serializeDeserialize1(a);
    }

    @Test
    public void testGrouping() {
        Grouping a = new Grouping();
        GroupingLevel level = new GroupingLevel();
        level.setExpression(new AttributeNode("folder"));

        XorAggregationResult xor = new XorAggregationResult();
        xor.setExpression(new MD5BitFunctionNode(new AttributeNode("docid"), 64));
        level.getGroupPrototype().addAggregationResult(xor);

        SumAggregationResult sum = new SumAggregationResult();
        MinFunctionNode min = new MinFunctionNode();
        min.addArg(new AttributeNode("attribute1"));
        min.addArg(new AttributeNode("attribute2"));
        sum.setExpression(min);
        level.getGroupPrototype().addAggregationResult(sum);

        CatFunctionNode cat = new CatFunctionNode();
        cat.addArg(new GetDocIdNamespaceSpecificFunctionNode());
        cat.addArg(new DocumentFieldNode("folder"));
        cat.addArg(new DocumentFieldNode("flags"));
        XorAggregationResult xor2 = new XorAggregationResult();
        xor2.setExpression(new XorBitFunctionNode(cat, 64));
        level.getGroupPrototype().addAggregationResult(xor2);
        a.addLevel(level);

        Group g = new Group();
        g.setId(new IntegerResultNode(17));
        g.addAggregationResult(xor); // XXX: this is BAD
        a.getRoot().addChild(g);
        serializeDeserialize1(a);

        Grouping foo = new Grouping();
        foo.addLevel(level);
        int hashBefore = foo.hashCode();
        foo.setFirstLevel(66);
        assertEquals(hashBefore, foo.hashCode());
        foo.setFirstLevel(99);
        assertEquals(hashBefore, foo.hashCode());
        foo.setLastLevel(66);
        assertEquals(hashBefore, foo.hashCode());
        foo.setLastLevel(99);
        assertEquals(hashBefore, foo.hashCode());
        foo.getRoot().addChild(g);
        assertEquals(hashBefore, foo.hashCode());
    }

    // --------------------------------------------------------------------------------
    //
    // Everything below this point is helper functions.
    //
    // --------------------------------------------------------------------------------
    private static Identifiable serializeDeserialize1(Identifiable a) {
        BufferSerializer buf = new BufferSerializer(new GrowableByteBuffer());
        a.serializeWithId(buf);
        buf.flip();
        Identifiable b = Identifiable.create(buf);
        assertEquals(a.getClass(), b.getClass());
        assertFalse(buf.getBuf().hasRemaining());
        Identifiable c = b.clone();
        assertEquals(b.getClass(), c.getClass());
        BufferSerializer  bb = new BufferSerializer(new GrowableByteBuffer());
        BufferSerializer cb = new BufferSerializer(new GrowableByteBuffer());
        b.serializeWithId(bb);
        c.serializeWithId(cb);
        assertEquals(bb.getBuf().limit(), cb.getBuf().limit());
        assertEquals(bb.position(), cb.position());
        bb.getBuf().flip();
        cb.getBuf().flip();
        for (int i = 0; i < bb.getBuf().limit(); i++) {
            assertEquals(bb.getBuf().get(), cb.getBuf().get());
        }

        return b;
    }

    private static AggregationResult serializeDeserialize(AggregationResult a) {
        AggregationResult b = (AggregationResult)serializeDeserialize1(a);
        assertEquals(a.getExpression().getClass(), b.getExpression().getClass());
        return b;
    }

}
