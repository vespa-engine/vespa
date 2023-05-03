// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.result;

import com.yahoo.prelude.hitfield.RawBase64;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class GroupIdTestCase {

    @Test
    void requireThatAccessorsWork() {
        ValueGroupId valueId = new DoubleId(6.9);
        assertEquals(6.9, valueId.getValue());
        BucketGroupId rangeId = new DoubleBucketId(6.0, 9.0);
        assertEquals(6.0, rangeId.getFrom());
        assertEquals(9.0, rangeId.getTo());

        valueId = new LongId(69L);
        assertEquals(69L, valueId.getValue());
        rangeId = new LongBucketId(6L, 9L);
        assertEquals(6L, rangeId.getFrom());
        assertEquals(9L, rangeId.getTo());

        valueId = new RawId(new byte[]{6, 9});
        assertEquals(new RawBase64(new byte[]{6, 9}), valueId.getValue());
        rangeId = new RawBucketId(new byte[]{6, 9}, new byte[]{9, 6});
        assertEquals(new RawBase64(new byte[]{6, 9}), rangeId.getFrom());
        assertEquals(new RawBase64(new byte[]{9, 6}), rangeId.getTo());

        valueId = new StringId("69");
        assertEquals("69", valueId.getValue());
        rangeId = new StringBucketId("6", "9");
        assertEquals("6", rangeId.getFrom());
        assertEquals("9", rangeId.getTo());

        valueId = new BoolId(true);
        assertEquals(true, valueId.getValue());
    }

    @Test
    void requireThatToStringCorrespondsToType() {
        assertEquals("group:double:6.9", new DoubleId(6.9).toString());
        assertEquals("group:double_bucket:6.0:9.0", new DoubleBucketId(6.0, 9.0).toString());
        assertEquals("group:long:69", new LongId(69L).toString());
        assertEquals("group:long_bucket:6:9", new LongBucketId(6L, 9L).toString());
        assertEquals("group:null", new NullId().toString());
        assertEquals("group:raw:Bgk=", new RawId(new byte[]{6, 9}).toString());
        assertEquals("group:raw_bucket:Bgk=:CQY=", new RawBucketId(new byte[]{6, 9}, new byte[]{9, 6}).toString());
        assertTrue(new RootId(0).toString().startsWith("group:root:"));
        assertEquals("group:string:69", new StringId("69").toString());
        assertEquals("group:string_bucket:6:9", new StringBucketId("6", "9").toString());
    }
}
