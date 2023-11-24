// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.tensor.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.test.json.Jackson;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SerializationTestCase {

    private static final String testPath = "eval/src/apps/make_tensor_binary_format_test_spec/test_spec.json";
    private static final List<String> tests = new ArrayList<>();

    @Before
    public void loadTests() throws IOException {
        File testSpec = new File(testPath);
        if (!testSpec.exists()) {
            testSpec = new File("../" + testPath);
        }
        try(BufferedReader br = new BufferedReader(new FileReader(testSpec))) {
            String test = br.readLine();
            while (test != null) {
                tests.add(test);
                test = br.readLine();
            }
        }
    }

    @Test
    public void testSerialization() throws IOException {
        var mapper = Jackson.mapper();
        for (String test : tests) {
            JsonNode node = mapper.readTree(test);
            if (node.has("tensor") && node.has("binary")) {
                System.out.println("Running test: " + test);

                Tensor tensor = buildTensor(node.get("tensor"));
                String spec = getSpec(node.get("tensor"));
                byte[] encodedTensor = TypedBinaryFormat.encode(tensor);
                boolean serializedToABinaryRepresentation = false;

                JsonNode binaryNode = node.get("binary");
                for (int i = 0; i < binaryNode.size(); ++i) {
                    byte[] bin = getBytes(binaryNode.get(i).asText());
                    Tensor decodedTensor = TypedBinaryFormat.decode(Optional.empty(), GrowableByteBuffer.wrap(bin));

                    if (spec.equalsIgnoreCase("double")) {
                        assertEquals(tensor.asDouble(), decodedTensor.asDouble(), 1e-6);
                    } else {
                        assertEquals(tensor, decodedTensor);
                    }

                    if (Arrays.equals(encodedTensor, bin)) {
                        serializedToABinaryRepresentation = true;
                    }
                }
                assertTrue("Tensor serialized to one of the given representations",
                           serializedToABinaryRepresentation);
            }
        }
    }

    private Tensor buildTensor(JsonNode tensor) {
        TensorType type = tensorType(tensor);
        Tensor.Builder builder = Tensor.Builder.of(type);
        tensorCells(tensor, builder);
        return builder.build();
    }

    private TensorType tensorType(JsonNode tensor) {
        String spec = getSpec(tensor);
        if (spec.equalsIgnoreCase("double")) {
            spec = "tensor()";
        }
        return TensorType.fromSpec(spec);
    }

    private String getSpec(JsonNode tensor) {
        return tensor.get("type").asText();
    }

    private void tensorCells(JsonNode tensor, Tensor.Builder builder) {
        JsonNode cells = tensor.get("cells");
        for (JsonNode cell : cells) {
            tensorCell(cell, builder.cell());
        }
    }

    private void tensorCell(JsonNode cell, Tensor.Builder.CellBuilder cellBuilder) {
        tensorCellAddress(cellBuilder, cell.get("address"));
        tensorCellValue(cellBuilder, cell.get("value"));
    }

    private void tensorCellValue(Tensor.Builder.CellBuilder cellBuilder, JsonNode value) {
        cellBuilder.value(value.doubleValue());
    }

    private void tensorCellAddress(Tensor.Builder.CellBuilder cellBuilder, JsonNode address) {
        Iterator<String> dimension = address.fieldNames();
        while (dimension.hasNext()) {
            String name = dimension.next();
            JsonNode label = address.get(name);
            cellBuilder.label(name, label.asText());
        }
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
