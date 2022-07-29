// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Simon Thoresen Hult
 */
public class BinaryFormatTest {

    @Test
    void requireThatEncodeNullThrows() {
        try {
            BinaryFormat.encode(null);
            fail();
        } catch (NullPointerException e) {
            assertEquals("predicate", e.getMessage());
        }
    }

    @Test
    void requireThatDecodeNullThrows() {
        try {
            BinaryFormat.decode(null);
            fail();
        } catch (NullPointerException e) {
            assertEquals("buf", e.getMessage());
        }
    }

    @Test
    void requireThatDecodeEmptyThrows() {
        try {
            BinaryFormat.decode(new byte[0]);
            fail();
        } catch (UnsupportedOperationException e) {
            assertEquals("0", e.getMessage());
        }
    }

    @Test
    void requireThatConjunctionCanBeSerialized() {
        assertSerialize(new Conjunction(new FeatureSet("foo", "bar"), new FeatureSet("baz", "cox")));
    }

    @Test
    void requireThatDisjunctionCanBeSerialized() {
        assertSerialize(new Disjunction(new FeatureSet("foo", "bar"), new FeatureSet("baz", "cox")));
    }

    @Test
    void requireThatFeatureRangeCanBeSerialized() {
        assertSerialize(new FeatureRange("foo", null, null));
        assertSerialize(new FeatureRange("foo", null, 9L));
        assertSerialize(new FeatureRange("foo", 6L, null));
        assertSerialize(new FeatureRange("foo", 6L, 9L));
    }

    @Test
    void requireThatPartitionedFeatureRangeCanBeSerialized() {
        FeatureRange expected = new FeatureRange("foo", 8L, 20L);
        FeatureRange f = new FeatureRange("foo", 8L, 20L);
        f.addPartition(new RangeEdgePartition("foo=0", 0, 8, -1));
        f.addPartition(new RangeEdgePartition("foo=20", 20, 0, 0));
        f.addPartition(new RangePartition("foo", 10, 19, false));
        assertSerializesTo(expected, f);
        Slime slime = com.yahoo.slime.BinaryFormat.decode(BinaryFormat.encode(f));
        assertEquals(BinaryFormat.TYPE_FEATURE_RANGE, slime.get().field(BinaryFormat.NODE_TYPE).asLong());
        Inspector in1 = slime.get().field(BinaryFormat.HASHED_PARTITIONS);
        assertEquals(1, in1.entries());
        assertEquals(0xf2b6d1cc6322cb99L, in1.entry(0).asLong());
        Inspector in2 = slime.get().field(BinaryFormat.HASHED_EDGE_PARTITIONS);
        assertEquals(2, in2.entries());
        Inspector obj1 = in2.entry(0);
        assertEquals(0xb2b301e26efffdc2L, obj1.field(BinaryFormat.HASH).asLong());
        assertEquals(0, obj1.field(BinaryFormat.VALUE).asLong());
        assertEquals(0x80000008L, obj1.field(BinaryFormat.PAYLOAD).asLong());
        Inspector obj2 = in2.entry(1);
        assertEquals(0x22acb2ed72523c36L, obj2.field(BinaryFormat.HASH).asLong());
        assertEquals(20, obj2.field(BinaryFormat.VALUE).asLong());
        assertEquals(0x40000001L, obj2.field(BinaryFormat.PAYLOAD).asLong());
    }

    @Test
    void requireThatFeatureSetCanBeSerialized() {
        assertSerialize(new FeatureSet("foo"));
        assertSerialize(new FeatureSet("foo", "bar"));
        assertSerialize(new FeatureSet("foo", "bar", "baz"));
    }

    @Test
    void requireThatNegationCanBeSerialized() {
        assertSerialize(new Negation(new FeatureSet("foo", "bar")));
    }

    @Test
    void requireThatBooleanCanBeSerialized() {
        assertSerialize(new BooleanPredicate(true));
        assertSerialize(new BooleanPredicate(false));
    }

    @Test
    void requireThatUnknownNodeThrows() {
        try {
            BinaryFormat.encode(SimplePredicates.newString("foo"));
            fail();
        } catch (UnsupportedOperationException e) {

        }
    }

    private static void assertSerializesTo(Predicate expected, Predicate predicate) {
        assertEquals(expected, BinaryFormat.decode(BinaryFormat.encode(predicate)));
    }

    private static void assertSerialize(Predicate predicate) {
        assertSerializesTo(predicate, predicate);
    }
}
