// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.slime.SlimeAdapter;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.serialization.TypedBinaryFormat;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
        var tester = new FeatureDataTester();

        var featureData = tester.featureData;
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
        assertEquals(tester.tensor1, featureData.getTensor("tensor1"));
        assertEquals(tester.tensor2, featureData.getTensor("tensor2"));
        assertEquals(tester.tensor2, featureData.getTensor("tensor2"), "Cached lookup");
        assertNull(featureData.getTensor("nosuch2"));
        assertNull(featureData.getTensor("nosuch2"));

        String expectedJson = """
            {
                "scalar1":1.5,
                "scalar2":2.5,
                "tensor1":{"type":"tensor(x[3])","cells":[{"address":{"x":"0"},"value":1.5},{"address":{"x":"1"},"value":2.0},{"address":{"x":"2"},"value":2.5}]},
                "tensor2":{"type":"tensor(key{})","cells":[{"address":{"key":"a"},"value":0.5},{"address":{"key":"b"},"value":1.5}]}
            }
            """;
        assertEquals(expectedJson.replace("\n", "").replace(" ",""), featureData.toJson());

        Map<String, Tensor> featureMap = new LinkedHashMap<>();
        for (String featureName : featureData.featureNames())
            featureMap.put(featureName, featureData.getTensor(featureName));
        FeatureData fromMap = new FeatureData(featureMap);
        assertEquals(featureData, fromMap);
        assertEquals(featureData.toJson(), fromMap.toJson());
        assertEquals(featureData.toJson(true, false), fromMap.toJson(true, false));
        assertEquals(featureData.toJson(true, true), fromMap.toJson(true, true));
    }

    @Test
    void testMutatingFeatureData() {
        var tester = new FeatureDataTester();

        var featureData = tester.featureData;
        featureData.set("scalar2", -2.5);
        featureData.set("scalar3", 3.5);
        featureData.set("tensor2", Tensor.from("tensor(x[2]):[1.0, 2.0]"));
        featureData.set("tensor3", Tensor.from("tensor(x[1]):[3.0]"));
        String expectedJson = """
            {
                "scalar2":-2.5,
                "scalar3":3.5,
                "tensor2":[1.0,2.0],
                "tensor3":[3.0],
                "scalar1":1.5,
                "tensor1":[1.5,2.0,2.5]
            }
            """;
        assertEquals(expectedJson.replace("\n", "").replace(" ",""), featureData.toJson(true, true));

        assertEquals(Set.of("scalar1", "scalar2", "scalar3", "tensor1", "tensor2", "tensor3"), featureData.featureNames());

        Inspector inspector = featureData.inspect();
        Map<String, Tensor> values = new HashMap<>();
        inspector.traverse((String name, Inspector value) -> values.put(name, tester.decodeTensor(value)));
        assertEquals(6, values.size());
        assertEquals(Tensor.from(1.5), values.get("scalar1"));
        assertEquals(Tensor.from(-2.5), values.get("scalar2"));
        assertEquals(Tensor.from(3.5), values.get("scalar3"));
        assertEquals(Tensor.from("tensor(x[3]):[1.5,2.0,2.5]"), values.get("tensor1"));
        assertEquals(Tensor.from("tensor(x[2]):[1.0,2.0]"), values.get("tensor2"));
        assertEquals(Tensor.from("tensor(x[1]):[3.0]"), values.get("tensor3"));

        assertEquals(Tensor.from(1.5), featureData.getTensor("scalar1"));
        assertEquals(Tensor.from(-2.5), featureData.getTensor("scalar2"));
        assertEquals(Tensor.from(3.5), featureData.getTensor("scalar3"));
        assertEquals(Tensor.from("tensor(x[3]):[1.5,2.0,2.5]"), featureData.getTensor("tensor1"));
        assertEquals(Tensor.from("tensor(x[2]):[1.0,2.0]"), featureData.getTensor("tensor2"));
        assertEquals(Tensor.from("tensor(x[1]):[3.0]"), featureData.getTensor("tensor3"));
    }

    static class FeatureDataTester {

        FeatureData featureData;
        Tensor tensor1, tensor2;

        FeatureDataTester() {
            Cursor features = new Slime().setObject();
            features.setDouble("scalar1", 1.5);
            features.setDouble("scalar2", 2.5);
            tensor1 = Tensor.from("tensor(x[3]):[1.5, 2, 2.5]");
            features.setData("tensor1", TypedBinaryFormat.encode(tensor1));
            tensor2 = Tensor.from("tensor(key{}):{a:0.5, b:1.5}");
            features.setData("tensor2", TypedBinaryFormat.encode(tensor2));
            featureData = new FeatureData(new SlimeAdapter(features));
        }

        Tensor decodeTensor(Inspector featureValue) {
            if ( ! featureValue.valid()) return null;

            return switch (featureValue.type()) {
                case DOUBLE -> Tensor.from(featureValue.asDouble());
                case DATA -> tensorFromData(featureValue.asData());
                default -> throw new IllegalStateException("Unexpected feature value type " + featureValue.type());
            };
        }

        private static Tensor tensorFromData(byte[] value) {
            return TypedBinaryFormat.decode(Optional.empty(), GrowableByteBuffer.wrap(value));
        }


    }

}
