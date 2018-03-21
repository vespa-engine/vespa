// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.test;

import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.PredicateQueryItem;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * @author Magnar Nedland
 */
public class PredicateQueryItemTestCase {

    @Test
    public void requireThatItemConstantsAreSet() {
        PredicateQueryItem item = new PredicateQueryItem();
        assertEquals(Item.ItemType.PREDICATE_QUERY, item.getItemType());
        assertEquals("PREDICATE_QUERY_ITEM", item.getName());
        assertEquals(1, item.getTermCount());
        assertEquals("predicate", item.getIndexName());
        item.setIndexName("foobar");
        assertEquals("foobar", item.getIndexName());
    }

    @Test
    public void requireThatFeaturesCanBeAdded() {
        PredicateQueryItem item = new PredicateQueryItem();
        assertEquals(0, item.getFeatures().size());
        item.addFeature("foo", "bar");
        item.addFeature("foo", "baz", 0xffff);
        item.addFeature(new PredicateQueryItem.Entry("qux", "quux"));
        item.addFeature(new PredicateQueryItem.Entry("corge", "grault", 0xf00ba));
        assertEquals(4, item.getFeatures().size());
        Iterator<PredicateQueryItem.Entry> it = item.getFeatures().iterator();
        assertEquals(-1, it.next().getSubQueryBitmap());
        assertEquals(0xffffL, it.next().getSubQueryBitmap());
        assertEquals(-1, it.next().getSubQueryBitmap());
        assertEquals(0xf00baL, it.next().getSubQueryBitmap());
    }

    @Test
    public void requireThatRangeFeaturesCanBeAdded() {
        PredicateQueryItem item = new PredicateQueryItem();
        assertEquals(0, item.getRangeFeatures().size());
        item.addRangeFeature("foo", 23);
        item.addRangeFeature("foo", 34, 0x12345678L);
        item.addRangeFeature(new PredicateQueryItem.RangeEntry("qux", 43));
        item.addRangeFeature(new PredicateQueryItem.RangeEntry("corge", 54, 0xf00ba));
        assertEquals(4, item.getRangeFeatures().size());
        Iterator<PredicateQueryItem.RangeEntry> it = item.getRangeFeatures().iterator();
        assertEquals(-1, it.next().getSubQueryBitmap());
        assertEquals(0x12345678L, it.next().getSubQueryBitmap());
        assertEquals(-1, it.next().getSubQueryBitmap());
        assertEquals(0xf00baL, it.next().getSubQueryBitmap());
    }

    @Test
    public void requireThatToStringWorks() {
        PredicateQueryItem item = new PredicateQueryItem();
        assertEquals("PREDICATE_QUERY_ITEM ", item.toString());
        item.addFeature("foo", "bar");
        item.addFeature("foo", "baz", 0xffffL);
        assertEquals("PREDICATE_QUERY_ITEM foo=bar, foo=baz[0xffff]", item.toString());
        item.addRangeFeature("foo", 23);
        item.addRangeFeature("foo", 34, 0xfffffffffffffffeL);
        assertEquals("PREDICATE_QUERY_ITEM foo=bar, foo=baz[0xffff], foo:23, foo:34[0xfffffffffffffffe]", item.toString());
    }

    @Test
    public void requireThatPredicateQueryItemCanBeEncoded() {
        PredicateQueryItem item = new PredicateQueryItem();
        assertEquals("PREDICATE_QUERY_ITEM ", item.toString());
        item.addFeature("foo", "bar");
        item.addFeature("foo", "baz", 0xffffL);
        ByteBuffer buffer = ByteBuffer.allocate(1000);
        item.encode(buffer);
        buffer.flip();
        byte[] actual = new byte[buffer.remaining()];
        buffer.get(actual);
        assertArrayEquals(new byte[]{
                23,  // PREDICATE_QUERY code 23
                9, 'p', 'r', 'e', 'd', 'i', 'c', 'a', 't', 'e',
                2,  // 2 features
                3, 'f', 'o', 'o', 3, 'b', 'a', 'r', -1, -1, -1, -1, -1, -1, -1, -1,  // key, value, subquery
                3, 'f', 'o', 'o', 3, 'b', 'a', 'z', 0, 0, 0, 0, 0, 0, -1, -1,  // key, value, subquery
                0},  // no range features
                actual);

        item.addRangeFeature("foo", 23);
        item.addRangeFeature("foo", 34, 0xfffffffffffffffeL);
        buffer.clear();
        item.encode(buffer);
        buffer.flip();
        actual = new byte[buffer.remaining()];
        buffer.get(actual);
        assertArrayEquals(new byte[]{
                23,  // PREDICATE_QUERY code 23
                9, 'p', 'r', 'e', 'd', 'i', 'c', 'a', 't', 'e',
                2,  // 2 features
                3, 'f', 'o', 'o',  3, 'b', 'a', 'r',  -1, -1, -1, -1, -1, -1, -1, -1,  // key, value, subquery
                3, 'f', 'o', 'o',  3, 'b', 'a', 'z',  0, 0, 0, 0, 0, 0, -1, -1,  // key, value, subquery
                2,  // 2 range features
                3, 'f', 'o', 'o',  0, 0, 0, 0, 0, 0, 0, 23,  -1, -1, -1, -1, -1, -1, -1, -1,  // key, value, subquery
                3, 'f', 'o', 'o',  0, 0, 0, 0, 0, 0, 0, 34,  -1, -1, -1, -1, -1, -1, -1, -2},  // key, value, subquery
                actual);
    }

    @Test
    public void requireThatPredicateQueryItemWithManyAttributesCanBeEncoded() {
        PredicateQueryItem item = new PredicateQueryItem();
        assertEquals("PREDICATE_QUERY_ITEM ", item.toString());
        for (int i = 0; i < 200; ++i) {
            item.addFeature("foo", "bar");
        }
        ByteBuffer buffer = ByteBuffer.allocate(10000);
        item.encode(buffer);
        buffer.flip();
        byte[] actual = new byte[buffer.remaining()];
        buffer.get(actual);
        byte [] expectedPrefix = new byte[]{
                23,  // PREDICATE_QUERY code 23
                9, 'p', 'r', 'e', 'd', 'i', 'c', 'a', 't', 'e',
                (byte)0x80, (byte)0xc8,  // 200 features (0x80c8 => 0xc8 == 200)
                3, 'f', 'o', 'o',  3, 'b', 'a', 'r',  -1, -1, -1, -1, -1, -1, -1, -1,  // key, value, subquery
                3, 'f', 'o', 'o',  3, 'b', 'a', 'r',  -1, -1, -1, -1, -1, -1, -1, -1,  // key, value, subquery
                3, 'f', 'o', 'o',  3, 'b', 'a', 'r',  -1, -1, -1, -1, -1, -1, -1, -1,  // key, value, subquery
                };  // ...
        assertArrayEquals(expectedPrefix, Arrays.copyOfRange(actual, 0, expectedPrefix.length));

    }

}
