// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.tensor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.BooleanValue;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleCompatibleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.StringValue;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.serialization.TypedBinaryFormat;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class TensorConformanceTest {

    private static String testPath = "eval/src/apps/tensor_conformance/test_spec.json";

    @Test
    public void testConformance() throws IOException {
        File testSpec = new File(testPath);
        if (!testSpec.exists()) {
            testSpec = new File("../" + testPath);
        }
        int count = 0;
        List<Integer> failList = new ArrayList<>();

        try(BufferedReader br = new BufferedReader(new FileReader(testSpec))) {
            String test = br.readLine();
            while (test != null) {
                boolean success = testCase(test, count);
                if (!success) {
                    failList.add(count);
                }
                test = br.readLine();
                count++;
            }
        }
        assertEquals(failList.size() + " conformance test fails: " + failList, 0, failList.size());
    }

    private boolean testCase(String test, int count) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(test);

            if (node.has("num_tests")) {
                Assert.assertEquals(node.get("num_tests").asInt(), count);
                return true;
            }
            if (!node.has("expression")) {
                return true; // ignore
            }

            String expression = node.get("expression").asText();
            MapContext context = getInput(node.get("inputs"));
            Tensor expect = getTensor(node.get("result").get("expect").asText());
            Tensor result = evaluate(expression, context);
            boolean equals = Tensor.equals(result, expect);
            if (!equals) {
                System.out.println(count + " : Tensors not equal. Result: " + result.toString() + " Expected: " + expect.toString() + " -> expression \"" + expression + "\"");
            }
            return equals;

        } catch (Exception e) {
            System.out.println(count + " : " + e.toString());
        }
        return false;
    }

    private Tensor evaluate(String expression, MapContext context) throws ParseException {
        Value value = new RankingExpression(expression).evaluate(context);
        if (!(value instanceof TensorValue)) {
            throw new IllegalArgumentException("Result is not a tensor");
        }
        return ((TensorValue)value).asTensor();
    }

    private MapContext getInput(JsonNode inputs) {
        MapContext context = new MapContext();
        for (Iterator<String> i = inputs.fieldNames(); i.hasNext(); ) {
            String name = i.next();
            String value = inputs.get(name).asText();
            Tensor tensor = getTensor(value);
            context.put(name, new TensorValue(tensor));
        }
        return context;
    }

    private Tensor getTensor(String binaryRepresentation) {
        byte[] bin = getBytes(binaryRepresentation);
        return TypedBinaryFormat.decode(Optional.empty(), GrowableByteBuffer.wrap(bin));
    }

    private byte[] getBytes(String binaryRepresentation) {
        return parseHexValue(binaryRepresentation.substring(2));
    }

    private byte[] parseHexValue(String s) {
        final int len = s.length();
        byte[] bytes = new byte[len/2];
        for (int i = 0; i < len; i += 2) {
            int c1 = hexValue(s.charAt(i)) << 4;
            int c2 = hexValue(s.charAt(i + 1));
            bytes[i/2] = (byte)(c1 + c2);
        }
        return bytes;
    }

    private int hexValue(Character c) {
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        } else if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        } else if (c >= '0' && c <= '9') {
            return c - '0';
        }
        throw new IllegalArgumentException("Hex contains illegal characters");
    }

}

