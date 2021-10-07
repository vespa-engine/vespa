// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.test;

import com.yahoo.io.HexDump;
import com.yahoo.prelude.query.textualrepresentation.Discloser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.yahoo.prelude.query.*;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for WandItem.
 */
public class WandItemTestCase {

    private static double DELTA = 0.0000001;

    private static WandItem createSimpleItem() {
        WandItem item = new WandItem("myfield", 10);
        item.addToken("foo", 30);
        item.setScoreThreshold(20);
        item.setThresholdBoostFactor(2.0);
        return item;
    }

    @Test
    public void requireThatWandItemCanBeConstructed() {
        WandItem item = new WandItem("myfield", 10);
        assertEquals("myfield", item.getIndexName());
        assertEquals(10, item.getTargetNumHits());
        assertEquals(0.0, item.getScoreThreshold(), DELTA);
        assertEquals(1.0, item.getThresholdBoostFactor(), DELTA);
        assertEquals(Item.ItemType.WAND, item.getItemType());
    }

    @Test
    public void requireThatEncodeIsWorking() {
        WandItem item = createSimpleItem();

        ByteBuffer actual = ByteBuffer.allocate(128);
        ByteBuffer expect = ByteBuffer.allocate(128);
        expect.put((byte) 22).put((byte) 1);
        Item.putString("myfield", expect);
        expect.put((byte)10);  // targetNumHits
        expect.putDouble(20);  // scoreThreshold
        expect.putDouble(2.0); // thresholdBoostFactor
        new PureWeightedString("foo", 30).encode(expect);

        assertEquals(2, item.encode(actual));

        actual.flip();
        expect.flip();

        assertTrue(actual.equals(expect));
    }

    @Test
    public void requireThatToStringIsWorking() {
       assertEquals("WAND(10,20.0,2.0) myfield{[30]:\"foo\"}", createSimpleItem().toString());
    }

    @Test
    public void requireThatDiscloseIsWorking() {
        class TestDiscloser implements Discloser {
            public Map<String, Object> props = new HashMap<>();
            public void addProperty(String key, Object value) {
                props.put(key, value);
            }
            public void setValue(Object value) {}
            public void addChild(Item item) {}
        }
        TestDiscloser discloser = new TestDiscloser();
        createSimpleItem().disclose(discloser);
        assertEquals(10, discloser.props.get("targetNumHits"));
        assertEquals(20.0, discloser.props.get("scoreThreshold"));
        assertEquals(2.0, discloser.props.get("thresholdBoostFactor"));
        assertEquals("myfield", discloser.props.get("index"));
    }
}
