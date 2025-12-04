// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.rendering;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.data.access.simple.Value;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.test.json.Jackson;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.fasterxml.jackson.databind.SerializationFeature.FLUSH_AFTER_WRITE_VALUE;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author johsol
 */
public class JsonGeneratorDataSinkTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonFactory FACTORY = new JsonFactory();

    private static JsonFactory createGeneratorFactory() {
        return Jackson.createMapper(new JsonFactoryBuilder()
                        .streamReadConstraints(StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build()))
                .disable(FLUSH_AFTER_WRITE_VALUE).getFactory();
    }

    /**
     * Gathers json produced by JsonGenerator.
     */
    static class SlimeOutputStream extends ByteArrayOutputStream {
        Slime toSlime() {
            return SlimeUtils.jsonToSlime(toString(StandardCharsets.UTF_8));
        }
    }

    private void assertSlime(Slime expected, Slime actual) {
        assertTrue(expected.get().equalTo(actual.get()),
                () -> "Expected " + SlimeUtils.toJson(expected) +
                        " but got " + SlimeUtils.toJson(actual));
    }

    @Test
    public void testGenerateObject() throws IOException {
        var factory = createGeneratorFactory();
        var out = new SlimeOutputStream();
        var gen = factory.createGenerator(out, JsonEncoding.UTF8);
        var sink = new JsonGeneratorDataSink(gen);

        sink.startObject();
        sink.fieldName("my_long");
        sink.longValue(8);
        sink.fieldName("my_bool");
        sink.booleanValue(true);
        sink.fieldName("my_empty");
        sink.emptyValue();
        sink.fieldName("my_string");
        sink.stringValue("some_string");
        sink.fieldName("my_double");
        sink.doubleValue(3.14);
        sink.endObject();

        gen.flush();

        var expected = SlimeUtils.jsonToSlime("{ my_long: 8," +
                                              "  my_bool: true," +
                                              "  my_empty: null," +
                                              "  my_string: 'some_string'," +
                                              "  my_double: 3.14 }");
        assertSlime(expected, out.toSlime());
    }

    @Test
    public void testGenerateArray() throws IOException {
        var factory = createGeneratorFactory();
        var out = new SlimeOutputStream();
        var gen = factory.createGenerator(out, JsonEncoding.UTF8);
        var sink = new JsonGeneratorDataSink(gen);

        sink.startArray();
        sink.longValue(8);
        sink.booleanValue(true);
        sink.emptyValue();
        sink.stringValue("my_string");
        sink.doubleValue(3.14);
        sink.endArray();

        gen.flush();

        assertSlime(SlimeUtils.jsonToSlime("[ 8, true, null, 'my_string', 3.14 ]"), out.toSlime());
    }

    @Test
    public void testGenerateComplexObject() throws IOException {
        var factory = createGeneratorFactory();
        var out = new SlimeOutputStream();
        var gen = factory.createGenerator(out, JsonEncoding.UTF8);
        var sink = new JsonGeneratorDataSink(gen);

        sink.startObject();

        // utf16 field name, array value
        sink.fieldName("numbers", null);
        sink.startArray();
        sink.longValue(1);
        sink.longValue(2);
        sink.longValue(3);
        sink.endArray();

        // utf8 field name, nested object
        sink.fieldName(null, "nested".getBytes(StandardCharsets.UTF_8));
        sink.startObject();
        sink.fieldName("flag", null);
        sink.booleanValue(true);

        sink.fieldName(null, "text".getBytes(StandardCharsets.UTF_8));
        sink.stringValue(null, "æøå".getBytes(StandardCharsets.UTF_8));
        sink.endObject();

        sink.endObject();

        gen.flush();

        var expected = SlimeUtils.jsonToSlime(
                "{ " +
                "  numbers: [1, 2, 3]," +
                "  nested: { flag: true, text: 'æøå' }" +
                "}"
        );
        assertSlime(expected, out.toSlime());
    }

    @Test
    public void testGenerateComplexArray() throws IOException {
        var factory = createGeneratorFactory();
        var out = new SlimeOutputStream();
        var gen = factory.createGenerator(out, JsonEncoding.UTF8);
        var sink = new JsonGeneratorDataSink(gen);

        sink.startArray();

        // First element: object
        sink.startObject();
        sink.fieldName("id", null);
        sink.longValue(1);
        sink.fieldName("label", null);
        sink.stringValue("first", null);
        sink.endObject();

        // Second element: primitives
        sink.longValue(42);
        sink.booleanValue(false);

        // Third element: nested array
        sink.startArray();
        sink.stringValue("a", null);
        sink.stringValue("b", null);
        sink.endArray();

        sink.endArray();

        gen.flush();

        var expected = SlimeUtils.jsonToSlime(
                "[" +
                        "  { id: 1, label: 'first' }," +
                        "  42," +
                        "  false," +
                        "  [ 'a', 'b' ]" +
                        "]"
        );
        assertSlime(expected, out.toSlime());
    }

    @Test
    public void testGenerateLongVariations() throws IOException {
        var factory = createGeneratorFactory();
        var out = new SlimeOutputStream();
        var gen = factory.createGenerator(out, JsonEncoding.UTF8);
        var sink = new JsonGeneratorDataSink(gen);

        sink.startArray();
        sink.byteValue((byte) 1);
        sink.shortValue((short) 2);
        sink.intValue(3);
        sink.longValue(4L);
        sink.endArray();

        gen.flush();

        var expected = SlimeUtils.jsonToSlime("[ 1, 2, 3, 4 ]");
        assertSlime(expected, out.toSlime());
    }

    @Test
    public void testGenerateDoubleVariations() throws IOException {
        var factory = createGeneratorFactory();
        var out = new SlimeOutputStream();
        var gen = factory.createGenerator(out, JsonEncoding.UTF8);
        var sink = new JsonGeneratorDataSink(gen);

        sink.startArray();
        sink.doubleValue(1.5);
        sink.floatValue(2.5f);
        sink.endArray();

        gen.flush();

        var expected = SlimeUtils.jsonToSlime("[ 1.5, 2.5 ]");
        assertSlime(expected, out.toSlime());
    }

    @Test
    public void testBase64PathMatchesOldPath() throws Exception {
        assertPathMatchesOldPath(true);
    }

    @Test
    public void testHexPathMatchesOldPath() throws Exception {
        assertPathMatchesOldPath(false);
    }

    /**
     * Tests that the new path using sinks matches the old path where we used the WithBase64 class to handle
     * encoding of binary data.
     */
    private void assertPathMatchesOldPath(boolean enableRawAsBase64) throws Exception {
        var input = new byte[]{ (byte)0xDE, (byte)0xAD, (byte)0xBE, (byte)0xEF };

        // if base64 disabled, we expect: "{ bin: '0xDEADBEEF' }"
        var obj = new Value.ObjectValue();
        obj.put("bin", new Value.DataValue(input));

        String oldPathJson = JsonRenderer.WithBase64.toJsonString(obj, enableRawAsBase64);
        String newPathJson = renderViaSink(obj, enableRawAsBase64);

        assertSlime(SlimeUtils.jsonToSlime(oldPathJson), SlimeUtils.jsonToSlime(newPathJson));
    }

    private String renderViaSink(Value.ObjectValue obj, boolean enableRawAsBase64) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var gen = FACTORY.createGenerator(out, JsonEncoding.UTF8)) {
            obj.emit(new JsonGeneratorDataSink(gen, enableRawAsBase64));
            gen.flush();
        }
        return out.toString(StandardCharsets.UTF_8);
    }

}
