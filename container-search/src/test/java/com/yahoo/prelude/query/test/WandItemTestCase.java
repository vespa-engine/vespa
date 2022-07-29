// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.test;

import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.PureWeightedString;
import com.yahoo.prelude.query.WandItem;
import com.yahoo.prelude.query.textualrepresentation.Discloser;
import com.yahoo.prelude.query.textualrepresentation.TextualQueryRepresentation;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for WandItem.
 */
public class WandItemTestCase {

    private static final double DELTA = 0.0000001;

    @Test
    void requireThatWandItemCanBeConstructed() {
        WandItem item = new WandItem("myfield", 10);
        assertEquals("myfield", item.getIndexName());
        assertEquals(10, item.getTargetNumHits());
        assertEquals(0.0, item.getScoreThreshold(), DELTA);
        assertEquals(1.0, item.getThresholdBoostFactor(), DELTA);
        assertEquals(Item.ItemType.WAND, item.getItemType());
    }

    @Test
    void requireThatEncodeIsWorking() {
        WandItem item = createSimpleItem();

        ByteBuffer actual = ByteBuffer.allocate(128);
        ByteBuffer expect = ByteBuffer.allocate(128);
        expect.put((byte) 22).put((byte) 1);
        Item.putString("myfield", expect);
        expect.put((byte) 10);  // targetNumHits
        expect.putDouble(20);  // scoreThreshold
        expect.putDouble(2.0); // thresholdBoostFactor
        new PureWeightedString("foo", 30).encode(expect);

        assertEquals(2, item.encode(actual));

        actual.flip();
        expect.flip();

        assertEquals(expect, actual);
    }

    @Test
    void requireThatToStringIsWorking() {
        assertEquals("WAND(10,20.0,2.0) myfield{[30]:\"foo\"}", createSimpleItem().toString());
    }

    @Test
    void requireThatDiscloseIsWorking() {
        class TestDiscloser implements Discloser {
            public final Map<String, Object> props = new HashMap<>();

            public void addProperty(String key, Object value) {
                props.put(key, value);
            }

            public void setValue(Object value) {
            }

            public void addChild(Item item) {
            }
        }
        TestDiscloser discloser = new TestDiscloser();
        createSimpleItem().disclose(discloser);
        assertEquals(10, discloser.props.get("targetNumHits"));
        assertEquals(20.0, discloser.props.get("scoreThreshold"));
        assertEquals(2.0, discloser.props.get("thresholdBoostFactor"));
        assertEquals("myfield", discloser.props.get("index"));
    }

    @Test
    void testTextualRepresentation() {
        WandItem item = new WandItem("myfield", 10);
        item.addToken("term1", 10);
        item.setScoreThreshold(20);
        item.setThresholdBoostFactor(2.0);
        assertEquals("WAND[index=\"myfield\" scoreThreshold=20.0 targetNumHits=10 thresholdBoostFactor=2.0]{\n" +
                "  PURE_WEIGHTED_STRING[weight=10]{\n" +
                "    \"term1\"\n" +
                "  }\n" +
                "}\n",
                new TextualQueryRepresentation(item).toString());
    }

    private static WandItem createSimpleItem() {
        WandItem item = new WandItem("myfield", 10);
        item.addToken("foo", 30);
        item.setScoreThreshold(20);
        item.setThresholdBoostFactor(2.0);
        return item;
    }

}
