// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;


import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.container.search.LegacyEmulationConfig;
import com.yahoo.prelude.hitfield.RawData;
import com.yahoo.prelude.hitfield.XMLString;
import com.yahoo.prelude.hitfield.JSONString;
import com.yahoo.search.result.NanNumber;
import com.yahoo.search.result.StructuredData;
import com.yahoo.slime.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.nio.charset.StandardCharsets;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.serialization.TypedBinaryFormat;
import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;


public class SlimeSummaryTestCase {

    @Test
    public void testDecodingEmpty() {
        String summary_cf = "file:src/test/java/com/yahoo/prelude/fastsearch/summary.cfg";
        LegacyEmulationConfig emul = new LegacyEmulationConfig(new LegacyEmulationConfig.Builder().forceFillEmptyFields(true));
        DocsumDefinitionSet set = createDocsumDefinitionSet(summary_cf, emul);
        byte[] docsum = makeEmptyDocsum();
        FastHit hit = new FastHit();
        assertNull(set.lazyDecode("default", docsum, hit));
        assertThat(hit.getField("integer_field"), equalTo(NanNumber.NaN));
        assertThat(hit.getField("short_field"),   equalTo(NanNumber.NaN));
        assertThat(hit.getField("byte_field"),    equalTo(NanNumber.NaN));
        assertThat(hit.getField("float_field"),   equalTo(NanNumber.NaN));
        assertThat(hit.getField("double_field"),  equalTo(NanNumber.NaN));
        assertThat(hit.getField("int64_field"),   equalTo(NanNumber.NaN));
        assertThat(hit.getField("string_field"),  equalTo(""));
        assertThat(hit.getField("data_field"), instanceOf(RawData.class));
        assertThat(hit.getField("data_field").toString(), equalTo(""));
        assertThat(hit.getField("longstring_field"), equalTo((Object)""));
        assertThat(hit.getField("longdata_field"), instanceOf(RawData.class));
        assertThat(hit.getField("longdata_field").toString(), equalTo(""));
        assertThat(hit.getField("xmlstring_field"), instanceOf(XMLString.class));
        assertThat(hit.getField("xmlstring_field").toString(), equalTo(""));
        // assertThat(hit.getField("jsonstring_field"), instanceOf(JSONString.class));
        assertThat(hit.getField("jsonstring_field").toString(), equalTo(""));
        // Empty tensors are represented by null because we don't have type information here to create the right empty tensor
        assertNull(hit.getField("tensor_field1"));
        assertNull(hit.getField("tensor_field2"));
    }

    private DocsumDefinitionSet createDocsumDefinitionSet(String configID, LegacyEmulationConfig legacyEmulationConfig) {
        DocumentdbInfoConfig config = new ConfigGetter<>(DocumentdbInfoConfig.class).getConfig(configID);
        return new DocsumDefinitionSet(config.documentdb(0), legacyEmulationConfig);
    }

    @Test
    public void testDecodingEmptyWithoutForcedFill() {
        String summary_cf = "file:src/test/java/com/yahoo/prelude/fastsearch/summary.cfg";
        DocsumDefinitionSet set = createDocsumDefinitionSet(summary_cf, new LegacyEmulationConfig(new LegacyEmulationConfig.Builder().forceFillEmptyFields(false)));
        byte[] docsum = makeEmptyDocsum();
        FastHit hit = new FastHit();
        assertNull(set.lazyDecode("default", docsum, hit));
        assertThat(hit.getField("integer_field"), equalTo(null));
        assertThat(hit.getField("short_field"),   equalTo(null));
        assertThat(hit.getField("byte_field"),    equalTo(null));
        assertThat(hit.getField("float_field"),   equalTo(null));
        assertThat(hit.getField("double_field"),  equalTo(null));
        assertThat(hit.getField("int64_field"),   equalTo(null));
        assertThat(hit.getField("string_field"),  equalTo(null));
        assertThat(hit.getField("data_field"), equalTo(null));
        assertThat(hit.getField("data_field"), equalTo(null));
        assertThat(hit.getField("longstring_field"), equalTo(null));
        assertThat(hit.getField("longdata_field"), equalTo(null));
        assertThat(hit.getField("longdata_field"), equalTo(null));
        assertThat(hit.getField("xmlstring_field"), equalTo(null));
        assertThat(hit.getField("xmlstring_field"), equalTo(null));
        assertThat(hit.getField("jsonstring_field"), equalTo(null));
        assertNull(hit.getField("tensor_field1"));
        assertNull(hit.getField("tensor_field2"));
    }

    private byte[] makeEmptyDocsum() {
        Slime slime = new Slime();
        Cursor docsum = slime.setObject();
        return encode((slime));
    }
    private byte [] encode(Slime slime) {
        byte[] tmp = BinaryFormat.encode(slime);
        ByteBuffer buf = ByteBuffer.allocate(tmp.length + 4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(DocsumDefinitionSet.SLIME_MAGIC_ID);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put(tmp);
        return buf.array();
    }

    @Test
    public void testTimeout() {
        String summary_cf = "file:src/test/java/com/yahoo/prelude/fastsearch/summary.cfg";
        DocsumDefinitionSet set = createDocsumDefinitionSet(summary_cf);
        byte[] docsum = makeTimeout();
        FastHit hit = new FastHit();
        assertEquals("Hit hit index:0/0/000000000000000000000000 (relevance null) [fasthit, globalid: 0 0 0 0 0 0 0 0 0 0 0 0, partId: 0, distributionkey: 0] failed: Timed out....", set.lazyDecode("default", docsum, hit));
    }

    @Test
    public void testDecoding() {
        Tensor tensor1 = Tensor.from("tensor(x{},y{}):{{x:foo,y:bar}:0.1}");
        Tensor tensor2 = Tensor.from("tensor(x[],y[1]):{{x:0,y:0}:-0.3}");

        String summary_cf = "file:src/test/java/com/yahoo/prelude/fastsearch/summary.cfg";
        DocsumDefinitionSet set = createDocsumDefinitionSet(summary_cf);
        byte[] docsum = makeDocsum(tensor1, tensor2);
        FastHit hit = new FastHit();
        assertNull(set.lazyDecode("default", docsum, hit));
        assertThat(hit.getField("integer_field"), equalTo(4));
        assertThat(hit.getField("short_field"),   equalTo((short)2));
        assertThat(hit.getField("byte_field"),    equalTo((byte)1));
        assertThat(hit.getField("float_field"),   equalTo(4.5f));
        assertThat(hit.getField("double_field"),  equalTo(8.75));
        assertThat(hit.getField("int64_field"),   equalTo(8L));
        assertThat(hit.getField("string_field"),  equalTo("string_value"));
        assertThat(hit.getField("data_field"), instanceOf(RawData.class));
        assertThat(hit.getField("data_field").toString(), equalTo("data_value"));
        assertThat(hit.getField("longstring_field"), equalTo((Object)"longstring_value"));
        assertThat(hit.getField("longdata_field"), instanceOf(RawData.class));
        assertThat(hit.getField("longdata_field").toString(), equalTo("longdata_value"));
        assertThat(hit.getField("xmlstring_field"), instanceOf(XMLString.class));
        assertThat(hit.getField("xmlstring_field").toString(), equalTo("<tag>xmlstring_value</tag>"));
        if (hit.getField("jsonstring_field") instanceof JSONString) {
            JSONString jstr = (JSONString) hit.getField("jsonstring_field");
            assertThat(jstr.getContent(), equalTo("{\"foo\":1,\"bar\":2}"));
            assertThat(jstr.getParsedJSON(), notNullValue());

            com.yahoo.data.access.Inspectable obj = jstr;
            com.yahoo.data.access.Inspector value = obj.inspect();
            assertThat(value.field("foo").asLong(), equalTo(1L));
            assertThat(value.field("bar").asLong(), equalTo(2L));
        } else {
            StructuredData sdata = (StructuredData) hit.getField("jsonstring_field");
            assertThat(sdata.toJson(), equalTo("{\"foo\":1,\"bar\":2}"));

            com.yahoo.data.access.Inspectable obj = sdata;
            com.yahoo.data.access.Inspector value = obj.inspect();
            assertThat(value.field("foo").asLong(), equalTo(1L));
            assertThat(value.field("bar").asLong(), equalTo(2L));
        }
        assertEquals(tensor1, hit.getField("tensor_field1"));
        assertEquals(tensor2, hit.getField("tensor_field2"));
    }

    private DocsumDefinitionSet createDocsumDefinitionSet(String configID) {
        DocumentdbInfoConfig config = new ConfigGetter<>(DocumentdbInfoConfig.class).getConfig(configID);
        return new DocsumDefinitionSet(config.documentdb(0));
    }

    private byte[] makeDocsum(Tensor tensor1, Tensor tensor2) {
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

    private byte [] makeTimeout() {
        Slime slime = new Slime();
        slime.setString("Timed out....");
        return encode((slime));
    }

}
