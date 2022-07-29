// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.dispatch.rpc;

import com.yahoo.data.access.ArrayTraverser;
import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.ObjectTraverser;
import com.yahoo.data.access.Type;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author arnej
 */
public class MatchFeatureDataTest {

    @Test
    void testHitValueAPI() {
        List<String> names = List.of("foo", "bar", "baz", "qux", "quux");
        var mf = new MatchFeatureData(names);
        var hit = mf.addHit();
        assertEquals(hit.type(), Type.OBJECT);
        assertTrue(hit.valid());
        hit.set(0, 1.0);
        byte[] somebytes = {42, 0, 17};
        hit.set(2, somebytes);
        hit.set(4, 5.0);
        hit.set(1, 2.0);
        hit.set(3, 4.0);
        assertEquals(0, hit.entryCount());
        assertEquals(5, hit.fieldCount());
        var f0 = hit.field("not");
        assertFalse(f0.valid());

        var f1 = hit.field("foo");
        assertTrue(f1.valid());
        assertEquals(f1.type(), Type.DOUBLE);
        assertEquals(f1.asDouble(), 1.0, 0.0);

        var f2 = hit.field("bar");
        assertTrue(f2.valid());
        assertEquals(f2.type(), Type.DOUBLE);
        assertEquals(f2.asDouble(), 2.0, 0.0);

        var f3 = hit.field("baz");
        assertTrue(f3.valid());
        assertEquals(f3.type(), Type.DATA);
        var gotbytes = f3.asData();
        assertEquals(3, gotbytes.length);
        assertEquals(42, gotbytes[0]);
        assertEquals(0,  gotbytes[1]);
        assertEquals(17, gotbytes[2]);

        var f5 = hit.field("quux");
        assertTrue(f5.valid());
        assertEquals(f5.type(), Type.DOUBLE);
        assertEquals(f5.asDouble(), 5.0, 0.0);

        var fields = hit.fields().iterator();
        assertTrue(fields.hasNext());
        Map.Entry<String, Inspector> entry = fields.next();
        assertEquals("foo", entry.getKey());
        assertEquals(f1.type(), entry.getValue().type());
        assertEquals(f1.asDouble(), entry.getValue().asDouble(), 0.0);

        assertTrue(fields.hasNext());
        entry = fields.next();
        assertEquals("bar", entry.getKey());

        assertTrue(fields.hasNext());
        entry = fields.next();
        assertEquals("baz", entry.getKey());
        assertEquals(f3.type(), entry.getValue().type());
        assertEquals(f3.asData(), entry.getValue().asData());

        assertTrue(fields.hasNext());
        entry = fields.next();
        assertEquals("qux", entry.getKey());
        var f4 = entry.getValue();
        assertTrue(f4.valid());
        assertEquals(f4.type(), Type.DOUBLE);
        assertEquals(f4.asDouble(), 4.0, 0.0);

        assertTrue(fields.hasNext());
        entry = fields.next();
        assertEquals("quux", entry.getKey());
        assertEquals(f5.type(), entry.getValue().type());
        assertEquals(f5.asDouble(), entry.getValue().asDouble(), 0.0);

        assertFalse(fields.hasNext());

        assertEquals("{\"foo\":1.0,\"bar\":2.0,\"baz\":\"0x2A0011\",\"qux\":4.0,\"quux\":5.0}",
                hit.toString());
    }

}
