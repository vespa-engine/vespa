// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.container.search.LegacyEmulationConfig;
import com.yahoo.prelude.hitfield.RawData;
import com.yahoo.prelude.hitfield.XMLString;
import com.yahoo.prelude.hitfield.JSONString;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.NanNumber;
import com.yahoo.search.result.StructuredData;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.yahoo.slime.BinaryFormat;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.serialization.TypedBinaryFormat;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SlimeSummaryTestCase {

    private static final String summary_cf = "file:src/test/java/com/yahoo/prelude/fastsearch/summary.cfg";

    @Test
    public void testDecodingEmpty() {
        DocsumDefinitionSet set = createDocsumDefinitionSet(summary_cf);
        FastHit hit = new FastHit();
        assertNull(set.lazyDecode("default", emptySummary(), hit));
        assertNull(hit.getField("integer_field"));
        assertNull(hit.getField("short_field"));
        assertNull(hit.getField("byte_field"));
        assertNull(hit.getField("float_field"));
        assertNull(hit.getField("double_field"));
        assertNull(hit.getField("int64_field"));
        assertNull(hit.getField("string_field"));
        assertNull(hit.getField("data_field"));
        assertNull(hit.getField("data_field"));
        assertNull(hit.getField("longstring_field"));
        assertNull(hit.getField("longdata_field"));
        assertNull(hit.getField("longdata_field"));
        assertNull(hit.getField("xmlstring_field"));
        assertNull(hit.getField("xmlstring_field"));
        assertNull(hit.getField("jsonstring_field"));
        assertNull(hit.getField("tensor_field1"));
        assertNull(hit.getField("tensor_field2"));
    }

    @Test
    public void testDecodingEmptyWithLegacyEmulation() {
        LegacyEmulationConfig emulationConfig = new LegacyEmulationConfig(new LegacyEmulationConfig.Builder().forceFillEmptyFields(true));
        DocsumDefinitionSet set = createDocsumDefinitionSet(summary_cf, emulationConfig);
        FastHit hit = new FastHit();
        assertNull(set.lazyDecode("default", emptySummary(), hit));
        assertEquals(NanNumber.NaN, hit.getField("integer_field"));
        assertEquals(NanNumber.NaN, hit.getField("short_field"));
        assertEquals(NanNumber.NaN, hit.getField("byte_field"));
        assertEquals(NanNumber.NaN, hit.getField("float_field"));
        assertEquals(NanNumber.NaN, hit.getField("double_field"));
        assertEquals(NanNumber.NaN, hit.getField("int64_field"));
        assertEquals("", hit.getField("string_field"));
        assertEquals(RawData.class, hit.getField("data_field").getClass());
        assertEquals("", hit.getField("data_field").toString());
        assertEquals("", hit.getField("longstring_field"));
        assertEquals(RawData.class, hit.getField("longdata_field").getClass());
        assertEquals("", hit.getField("longdata_field").toString());
        assertEquals(XMLString.class, hit.getField("xmlstring_field").getClass());
        assertEquals("", hit.getField("xmlstring_field").toString());
        // assertEquals(hit.getField("jsonstring_field"), instanceOf(JSONString.class));
        assertEquals("", hit.getField("jsonstring_field").toString());
        // Empty tensors are represented by null because we don't have type information here to create the right empty tensor
        assertNull(hit.getField("tensor_field1"));
        assertNull(hit.getField("tensor_field2"));
    }

    @Test
    public void testTimeout() {
        DocsumDefinitionSet set = createDocsumDefinitionSet(summary_cf);
        FastHit hit = new FastHit();
        assertEquals("Hit hit index:null/0/000000000000000000000000 (relevance null) [fasthit, globalid: 0 0 0 0 0 0 0 0 0 0 0 0, partId: 0, distributionkey: 0] failed: Timed out....",
                     set.lazyDecode("default", timeoutSummary(), hit));
    }

    @Test
    public void testDecoding() {
        Tensor tensor1 = Tensor.from("tensor(x{},y{}):{{x:foo,y:bar}:0.1}");
        Tensor tensor2 = Tensor.from("tensor(x[],y[1]):{{x:0,y:0}:-0.3}");
        DocsumDefinitionSet set = createDocsumDefinitionSet(summary_cf);
        FastHit hit = new FastHit();
        assertNull(set.lazyDecode("default", fullSummary(tensor1, tensor2), hit));
        assertEquals(4, hit.getField("integer_field"));
        assertEquals((short)2, hit.getField("short_field"));
        assertEquals((byte)1, hit.getField("byte_field"));
        assertEquals(4.5F, hit.getField("float_field"));
        assertEquals(8.75, hit.getField("double_field"));
        assertEquals(8L, hit.getField("int64_field"));
        assertEquals("string_value", hit.getField("string_field"));
        assertEquals(RawData.class, hit.getField("data_field").getClass());
        assertEquals("data_value", hit.getField("data_field").toString());
        assertEquals("longstring_value", hit.getField("longstring_field"));
        assertEquals(RawData.class, hit.getField("longdata_field").getClass());
        assertEquals("longdata_value", hit.getField("longdata_field").toString());
        assertEquals(XMLString.class, hit.getField("xmlstring_field").getClass());
        assertEquals("<tag>xmlstring_value</tag>", hit.getField("xmlstring_field").toString());
        if (hit.getField("jsonstring_field") instanceof JSONString) {
            JSONString jstr = (JSONString) hit.getField("jsonstring_field");
            assertEquals("{\"foo\":1,\"bar\":2}", jstr.getContent());
            assertNotNull(jstr.getParsedJSON());

            com.yahoo.data.access.Inspector value = jstr.inspect();
            assertEquals(1L, value.field("foo").asLong());
            assertEquals(2L, value.field("bar").asLong());
        } else {
            StructuredData sdata = (StructuredData) hit.getField("jsonstring_field");
            assertEquals("{\"foo\":1,\"bar\":2}", sdata.toJson());

            com.yahoo.data.access.Inspector value = sdata.inspect();
            assertEquals(1L, value.field("foo").asLong());
            assertEquals(2L, value.field("bar").asLong());
        }
        assertEquals(tensor1, hit.getField("tensor_field1"));
        assertEquals(tensor2, hit.getField("tensor_field2"));
    }

    @Test
    public void testFieldAccessAPI() {
        DocsumDefinitionSet set = createDocsumDefinitionSet(summary_cf);
        FastHit hit = new FastHit();
        Map<String, Object> expected = new HashMap<>();

        assertFields(expected, hit);

        set.lazyDecode("default", partialSummary1(), hit);
        expected.put("integer_field", 4);
        expected.put("short_field", (short) 2);
        assertFields(expected, hit);

        set.lazyDecode("default", partialSummary2(), hit);
        expected.put("float_field", 4.5F);
        expected.put("double_field", 8.75D);
        assertFields(expected, hit);

        hit.removeField("short_field");
        expected.remove("short_field");
        assertFields(expected, hit);

        hit.setField("string", "hello");
        expected.put("string", "hello");
        assertFields(expected, hit);

        hit.setField("short_field", 3.8F);
        expected.put("short_field", 3.8F);
        assertFields(expected, hit);

        hit.removeField("string");
        expected.remove("string");
        assertFields(expected, hit);

        hit.removeField("integer_field");
        hit.removeField("double_field");
        expected.remove("integer_field");
        expected.remove("double_field");
        assertFields(expected, hit);

        hit.clearFields();
        expected.clear();
        assertFields(expected, hit);

        // TODO:
        // - removing from field and field name iterators
        // - removing all fields in some summary, then iterating
        // - Ensure no overlapping fields between summaries?
        // - Remove some field which is then added from a summary?
    }


    /** Asserts that the expected fields are what is returned from every access method of Hit */
    private void assertFields(Map<String, Object> expected, Hit hit) {
        // fieldKeys
        int fieldNameIteratorFieldCount = 0;
        for (Iterator<String> i = hit.fieldKeys().iterator(); i.hasNext(); ) {
            fieldNameIteratorFieldCount++;
            assertTrue(expected.containsKey(i.next()));
        }
        assertEquals(expected.size(), fieldNameIteratorFieldCount);
        assertEquals(expected.keySet(), hit.fieldKeys());
        // getField
        for (Map.Entry<String, Object> field : expected.entrySet())
            assertEquals(field.getValue(), hit.getField(field.getKey()));
        // fields
        assertEquals(expected, hit.fields());
        // fieldIterator
        int fieldIteratorFieldCount = 0;
        for (Iterator<Map.Entry<String, Object>> i = hit.fieldIterator(); i.hasNext(); ) {
            fieldIteratorFieldCount++;
            Map.Entry<String, Object> field = i.next();
            assertEquals(field.getValue(), expected.get(field.getKey()));
        }
        assertEquals(expected.size(), fieldIteratorFieldCount);
    }

    private byte[] emptySummary() {
        Slime slime = new Slime();
        slime.setObject();
        return encode((slime));
    }

    private byte [] timeoutSummary() {
        Slime slime = new Slime();
        slime.setString("Timed out....");
        return encode((slime));
    }

    private byte[] partialSummary1() {
        Slime slime = new Slime();
        Cursor docsum = slime.setObject();
        docsum.setLong("integer_field", 4);
        docsum.setLong("short_field", 2);
        return encode((slime));
    }

    private byte[] partialSummary2() {
        Slime slime = new Slime();
        Cursor docsum = slime.setObject();
        docsum.setDouble("float_field", 4.5);
        docsum.setDouble("double_field", 8.75);
        return encode((slime));
    }

    private byte[] fullSummary(Tensor tensor1, Tensor tensor2) {
        Slime slime = new Slime();
        Cursor docsum = slime.setObject();
        docsum.setLong("integer_field", 4);
        docsum.setLong("short_field", 2);
        docsum.setLong("byte_field", 1);
        docsum.setDouble("float_field", 4.5);
        docsum.setDouble("double_field", 8.75);
        docsum.setLong("int64_field", 8);
        docsum.setString("string_field", "string_value");
        docsum.setData("data_field", "data_value".getBytes(StandardCharsets.UTF_8));
        docsum.setString("longstring_field", "longstring_value");
        docsum.setData("longdata_field", "longdata_value".getBytes(StandardCharsets.UTF_8));
        docsum.setString("xmlstring_field", "<tag>xmlstring_value</tag>");
        {
            Cursor field = docsum.setObject("jsonstring_field");
            field.setLong("foo", 1);
            field.setLong("bar", 2);
        }
        docsum.setData("tensor_field1", TypedBinaryFormat.encode(tensor1));
        docsum.setData("tensor_field2", TypedBinaryFormat.encode(tensor2));
        return encode((slime));
    }

    private byte[] encode(Slime slime) {
        byte[] tmp = BinaryFormat.encode(slime);
        ByteBuffer buf = ByteBuffer.allocate(tmp.length + 4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(DocsumDefinitionSet.SLIME_MAGIC_ID);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put(tmp);
        return buf.array();
    }

    private DocsumDefinitionSet createDocsumDefinitionSet(String configID) {
        DocumentdbInfoConfig config = new ConfigGetter<>(DocumentdbInfoConfig.class).getConfig(configID);
        return new DocsumDefinitionSet(config.documentdb(0));
    }

    private DocsumDefinitionSet createDocsumDefinitionSet(String configID, LegacyEmulationConfig legacyEmulationConfig) {
        DocumentdbInfoConfig config = new ConfigGetter<>(DocumentdbInfoConfig.class).getConfig(configID);
        return new DocsumDefinitionSet(config.documentdb(0), legacyEmulationConfig);
    }

}
