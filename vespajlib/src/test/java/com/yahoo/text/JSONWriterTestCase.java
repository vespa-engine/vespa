// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static org.junit.Assert.*;

/**
 * Tests the JSON writer
 *
 * @author bratseth
 */
@SuppressWarnings("deprecation")
public class JSONWriterTestCase {

    @Test
    public void testJSONWriter() throws IOException {
        OutputStream out = new ByteArrayOutputStream();
        JSONWriter w = new JSONWriter(out);

        w.beginObject();

        w.beginField("string").value("a string").endField();
        w.beginField("number").value(37).endField();
        w.beginField("true").value(true).endField();
        w.beginField("false").value(false).endField();
        w.beginField("null").value().endField();

        w.beginField("object").beginObject();
        w.beginField("nested-array").beginArray().beginArrayValue().value(1).endArrayValue().endArray().endField();
        w.endObject().endField();

        w.beginField("array").beginArray();
        w.beginArrayValue().value("item1").endArrayValue();
        w.beginArrayValue().value("item2").endArrayValue();
        w.beginArrayValue().beginObject().beginField("nested").value("item3").endField().endObject().endArrayValue();
        w.endArray().endField();

        w.endObject();

        assertEquals("{\"string\":\"a string\"," +
                      "\"number\":37," +
                      "\"true\":true," +
                      "\"false\":false," +
                      "\"null\":null," +
                      "\"object\":{\"nested-array\":[1]}," +
                      "\"array\":[\"item1\",\"item2\",{\"nested\":\"item3\"}]}",
                     out.toString());
    }

    @Test
    public void testJSONWriterEmptyObject() throws IOException {
        OutputStream out = new ByteArrayOutputStream();
        JSONWriter w = new JSONWriter(out);
        w.beginObject();
        w.endObject();

        assertEquals("{}",out.toString());
    }

    @Test
    public void testJSONWriterEmptyArray() throws IOException {
        OutputStream out = new ByteArrayOutputStream();
        JSONWriter w = new JSONWriter(out);
        w.beginArray();
        w.endArray();

        assertEquals("[]",out.toString());
    }

    @Test
    public void testJSONWriterStringOnly() throws IOException {
        OutputStream out = new ByteArrayOutputStream();
        JSONWriter w = new JSONWriter(out);
        w.value("Hello, world!");

        assertEquals("\"Hello, world!\"",out.toString());
    }

    @Test
    public void testJSONWriterNestedArrays() throws IOException {
        OutputStream out = new ByteArrayOutputStream();
        JSONWriter w = new JSONWriter(out);
        w.beginArray();

        w.beginArrayValue().beginArray();
        w.endArray().endArrayValue();

        w.beginArrayValue().beginArray();
        w.beginArrayValue().value("hello").endArrayValue();
        w.beginArrayValue().value("world").endArrayValue();
        w.endArray().endArrayValue();

        w.beginArrayValue().beginArray();
        w.endArray().endArrayValue();

        w.beginArrayValue().beginArray();
        w.beginArrayValue().beginArray();
        w.endArray().endArrayValue();
        w.endArray().endArrayValue();

        w.beginArrayValue().beginArray();
        w.endArray().endArrayValue();

        w.endArray();

        assertEquals("[[],[\"hello\",\"world\"],[],[[]],[]]",out.toString());
    }

}
