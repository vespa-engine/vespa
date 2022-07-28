// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import com.yahoo.data.access.slime.SlimeAdapter;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.serialization.TypedBinaryFormat;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author bratseth
 */
public class FeatureDataTestCase {

    private static final double delta = 0.00000001;

    @Test
    void testFeatureData() {
        Cursor features = new Slime().setObject();
        features.setDouble("scalar1", 1.5);
        features.setDouble("scalar2", 2.5);
        Tensor tensor1 = Tensor.from("tensor(x[3]):[1.5, 2, 2.5]");
        features.setData("tensor1", TypedBinaryFormat.encode(tensor1));
        Tensor tensor2 = Tensor.from(0.5);
        features.setData("tensor2", TypedBinaryFormat.encode(tensor2));

        FeatureData featureData = new FeatureData(new SlimeAdapter(features));
        assertEquals("scalar1,scalar2,tensor1,tensor2",
                featureData.featureNames().stream().sorted().collect(Collectors.joining(",")));
        assertNull(featureData.getDouble("nosuch1"));
        assertEquals(1.5, featureData.getDouble("scalar1"), delta);
        assertEquals(2.5, featureData.getDouble("scalar2"), delta);
        assertEquals(2.5, featureData.getDouble("scalar2"), delta, "Cached lookup");
        assertNull(featureData.getDouble("nosuch2"));
        assertNull(featureData.getDouble("nosuch2"));

        assertNull(featureData.getTensor("nosuch1"));
        assertEquals(Tensor.from(1.5), featureData.getTensor("scalar1"));
        assertEquals(Tensor.from(2.5), featureData.getTensor("scalar2"));
        assertEquals(tensor1, featureData.getTensor("tensor1"));
        assertEquals(tensor2, featureData.getTensor("tensor2"));
        assertEquals(tensor2, featureData.getTensor("tensor2"), "Cached lookup");
        assertNull(featureData.getTensor("nosuch2"));
        assertNull(featureData.getTensor("nosuch2"));

        String expectedJson =
                "{" +
                        "\"scalar1\":1.5," +
                        "\"scalar2\":2.5," +
                        "\"tensor1\":{\"type\":\"tensor(x[3])\",\"cells\":[{\"address\":{\"x\":\"0\"},\"value\":1.5},{\"address\":{\"x\":\"1\"},\"value\":2.0},{\"address\":{\"x\":\"2\"},\"value\":2.5}]}," +
                        "\"tensor2\":{\"type\":\"tensor()\",\"cells\":[{\"address\":{},\"value\":0.5}]}" +
                        "}";
        assertEquals(expectedJson, featureData.toJson());
    }

}
