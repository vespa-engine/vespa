// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;


import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.container.search.LegacyEmulationConfig;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.prelude.fastsearch.Docsum;
import com.yahoo.prelude.fastsearch.DocsumDefinition;
import com.yahoo.prelude.fastsearch.DocsumDefinitionSet;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.hitfield.RawData;
import com.yahoo.prelude.hitfield.XMLString;
import com.yahoo.prelude.hitfield.JSONString;
import com.yahoo.search.result.NanNumber;
import com.yahoo.search.result.StructuredData;
import com.yahoo.document.DocumentId;
import com.yahoo.document.GlobalId;
import com.yahoo.slime.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.nio.charset.StandardCharsets;

import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.*;


public class SlimeSummaryTestCase {

    public static DocsumDefinitionSet createDocsumDefinitionSet(String configID) {
        DocumentdbInfoConfig config = new ConfigGetter<>(DocumentdbInfoConfig.class).getConfig(configID);
        return new DocsumDefinitionSet(config.documentdb(0));
    }

    public static DocsumDefinitionSet createDocsumDefinitionSet(String configID, LegacyEmulationConfig legacyEmulationConfig) {
        DocumentdbInfoConfig config = new ConfigGetter<>(DocumentdbInfoConfig.class).getConfig(configID);
        return new DocsumDefinitionSet(config.documentdb(0), legacyEmulationConfig);
    }

    public byte[] makeEmptyDocsum() {
        Slime slime = new Slime();
        Cursor docsum = slime.setObject();
        byte[] tmp = BinaryFormat.encode(slime);
        ByteBuffer buf = ByteBuffer.allocate(tmp.length + 4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(DocsumDefinitionSet.SLIME_MAGIC_ID);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put(tmp);
        return buf.array();
    }

    public byte[] makeDocsum() {
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
        byte[] tmp = BinaryFormat.encode(slime);
        ByteBuffer buf = ByteBuffer.allocate(tmp.length + 4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(DocsumDefinitionSet.SLIME_MAGIC_ID);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put(tmp);
        return buf.array();
    }

    @Test
    public void testDecodingEmpty() {
        String summary_cf = "file:src/test/java/com/yahoo/prelude/fastsearch/summary.cfg";
        LegacyEmulationConfig emul = new LegacyEmulationConfig(new LegacyEmulationConfig.Builder().forceFillEmptyFields(true));
        DocsumDefinitionSet set = createDocsumDefinitionSet(summary_cf, emul);
        byte[] docsum = makeEmptyDocsum();
        FastHit hit = new FastHit();
        set.lazyDecode("default", docsum, hit);
        assertThat(hit.getField("integer_field"), equalTo((Object) NanNumber.NaN));
        assertThat(hit.getField("short_field"),   equalTo((Object) NanNumber.NaN));
        assertThat(hit.getField("byte_field"),    equalTo((Object) NanNumber.NaN));
        assertThat(hit.getField("float_field"),   equalTo((Object) NanNumber.NaN));
        assertThat(hit.getField("double_field"),  equalTo((Object) NanNumber.NaN));
        assertThat(hit.getField("int64_field"),   equalTo((Object) NanNumber.NaN));
        assertThat(hit.getField("string_field"),  equalTo((Object)""));
        assertThat(hit.getField("data_field"), instanceOf(RawData.class));
        assertThat(hit.getField("data_field").toString(), equalTo(""));
        assertThat(hit.getField("longstring_field"), equalTo((Object)""));
        assertThat(hit.getField("longdata_field"), instanceOf(RawData.class));
        assertThat(hit.getField("longdata_field").toString(), equalTo(""));
        assertThat(hit.getField("xmlstring_field"), instanceOf(XMLString.class));
        assertThat(hit.getField("xmlstring_field").toString(), equalTo(""));
        // assertThat(hit.getField("jsonstring_field"), instanceOf(JSONString.class));
        assertThat(hit.getField("jsonstring_field").toString(), equalTo(""));
    }

    @Test
    public void testDecodingEmptyWithoutForcedFill() {
        String summary_cf = "file:src/test/java/com/yahoo/prelude/fastsearch/summary.cfg";
        DocsumDefinitionSet set = createDocsumDefinitionSet(summary_cf, new LegacyEmulationConfig(new LegacyEmulationConfig.Builder().forceFillEmptyFields(false)));
        byte[] docsum = makeEmptyDocsum();
        FastHit hit = new FastHit();
        set.lazyDecode("default", docsum, hit);
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
    }

    @Test
    public void testDecoding() {
        String summary_cf = "file:src/test/java/com/yahoo/prelude/fastsearch/summary.cfg";
        DocsumDefinitionSet set = createDocsumDefinitionSet(summary_cf);
        byte[] docsum = makeDocsum();
        FastHit hit = new FastHit();
        set.lazyDecode("default", docsum, hit);
        assertThat(hit.getField("integer_field"), equalTo((Object)new Integer(4)));
        assertThat(hit.getField("short_field"),   equalTo((Object)new Short((short)2)));
        assertThat(hit.getField("byte_field"),    equalTo((Object)new Byte((byte)1)));
        assertThat(hit.getField("float_field"),   equalTo((Object)new Float(4.5f)));
        assertThat(hit.getField("double_field"),  equalTo((Object)new Double(8.75)));
        assertThat(hit.getField("int64_field"),   equalTo((Object)new Long(8L)));
        assertThat(hit.getField("string_field"),  equalTo((Object)"string_value"));
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
    }
}
