// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Basic tensor tests. Tensor operations are tested in EvaluationTestCase
 *
 * @author bratseth
 */
public class MapTensorTestCase {

    @Test
    public void testStringForm() {
        assertEquals("{}", MapTensor.from("{}").toString());
        assertEquals("{{d1:l1}:5.0,{d1:l1,d2:l2}:6.0}", MapTensor.from("{ {d1:l1}:5, {d2:l2, d1:l1}:6.0} ").toString());
        assertEquals("{{d1:l1}:-5.3,{d1:l1,d2:l2}:0.0}", MapTensor.from("{ {d1:l1}:-5.3, {d2:l2, d1:l1}:0}").toString());
    }

    @Test
    public void testParseError() {
        try {
            MapTensor.from("--");
            fail("Expected parse error");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("Excepted a string starting by { or (, got '--'", expected.getMessage());
        }
    }

    @Test
    public void testConstruction() {
        assertEquals("{}", new MapTensor(Collections.emptyMap()).toString());
        assertEquals("{{}:5.0}", new MapTensor(Collections.singletonMap(TensorAddress.empty, 5.0)).toString());

        Map<TensorAddress, Double> cells = new LinkedHashMap<>();
        cells.put(TensorAddress.fromSorted(Collections.singletonList(new TensorAddress.Element("d1","l1"))), 5.0);
        cells.put(TensorAddress.fromSorted(Collections.singletonList(new TensorAddress.Element("d2","l1"))), 6.0);
        cells.put(TensorAddress.empty, 7.0);
        assertEquals("{{}:7.0,{d1:l1}:5.0,{d2:l1}:6.0}", new MapTensor(cells).toString());
    }

    @Test
    public void testDimensions() {
        Set<String> dimensions1 = MapTensor.from("{} ").dimensions();
        assertEquals(0, dimensions1.size());

        Set<String> dimensions2 = MapTensor.from("{ {d1:l1}:5, {d2:l2, d1:l1}:6.0} ").dimensions();
        assertEquals(2, dimensions2.size());
        assertTrue(dimensions2.contains("d1"));
        assertTrue(dimensions2.contains("d2"));

        Set<String> dimensions3 = MapTensor.from("{ {d1:l1, d2:l1}:5, {d2:l2, d3:l1}:6.0} ").dimensions();
        assertEquals(3, dimensions3.size());
        assertTrue(dimensions3.contains("d1"));
        assertTrue(dimensions3.contains("d2"));
        assertTrue(dimensions3.contains("d3"));
    }

}
