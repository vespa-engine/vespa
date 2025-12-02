// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.data.disclosure.slime.SlimeDataSink;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import org.junit.Test;

import static com.yahoo.test.JunitCompat.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * @author baldersheim
 */
public class NumericFieldValueTestCase {

    private void assertSlime(Slime expected, Slime actual) {
        assertTrue("Expected " + SlimeUtils.toJson(expected) + " but got " + SlimeUtils.toJson(actual),
                expected.get().equalTo(actual.get()));
    }

    @Test
    public void requireThatConstructionFromStringWorks() {
        assertEquals(67, new IntegerFieldValue("67").getInteger());
        assertEquals(67, new LongFieldValue("67").getLong());
        assertEquals(67, new ByteFieldValue("67").getByte());
        assertEquals(67.45, new FloatFieldValue("67.45").getFloat(),    0.00001);
        assertEquals(67.45, new DoubleFieldValue("67.45").getDouble(), 0.000001);
    }

    @Test
    public void testNumericFieldValueEmitsCorrectly() {
        assertSlime(SlimeUtils.jsonToSlime("67"), SlimeDataSink.buildSlime(new IntegerFieldValue("67")));
        assertSlime(SlimeUtils.jsonToSlime("67"), SlimeDataSink.buildSlime(new LongFieldValue("67")));
        assertSlime(SlimeUtils.jsonToSlime("67"), SlimeDataSink.buildSlime(new ByteFieldValue("67")));
        assertSlime(SlimeUtils.jsonToSlime("8.5"), SlimeDataSink.buildSlime(new Float16FieldValue("8.5")));
        assertSlime(SlimeUtils.jsonToSlime("8.5"), SlimeDataSink.buildSlime(new FloatFieldValue("8.5")));
        assertSlime(SlimeUtils.jsonToSlime("8.5"), SlimeDataSink.buildSlime(new DoubleFieldValue("8.5")));
    }
}
