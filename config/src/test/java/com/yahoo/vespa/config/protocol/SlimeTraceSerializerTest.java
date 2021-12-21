// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.text.Utf8;
import com.yahoo.yolean.trace.TraceNode;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class SlimeTraceSerializerTest {

    @Test
    public void test_serializer() throws IOException {
        TraceNode root = new TraceNode(null, 1);
        root.add(new TraceNode("foo", 4));
        root.add(new TraceNode("bar", 5).add(new TraceNode("baz", 2).add(new TraceNode("quux", 10))));
        assertThat(toJson(root), is("{\"timestamp\":1,\"children\":[{\"timestamp\":5,\"payload\":\"bar\",\"children\":[{\"timestamp\":2,\"payload\":\"baz\",\"children\":[{\"timestamp\":10,\"payload\":\"quux\"}]}]},{\"timestamp\":4,\"payload\":\"foo\"}]}"));
        assertSerialize(root);
    }

    private String toJson(TraceNode root) throws IOException {
        Slime slime = new Slime();
        SlimeTraceSerializer serializer = new SlimeTraceSerializer(slime.setObject());
        root.accept(serializer);
        JsonFormat format = new JsonFormat(true);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        format.encode(baos, slime);
        return Utf8.toString(baos.toByteArray());
    }

    private void assertSerialize(TraceNode root) {
        Slime slime = new Slime();
        SlimeTraceSerializer serializer = new SlimeTraceSerializer(slime.setObject());
        root.accept(serializer);
        SlimeTraceDeserializer deserializer = new SlimeTraceDeserializer(slime.get());
        TraceNode deser = deserializer.deserialize();
        assertTraceEqual(deser, root);
    }

    private void assertTraceEqual(TraceNode deser, TraceNode root) {
        assertThat(deser.timestamp(), is(root.timestamp()));
        assertThat(deser.payload(), is(root.payload()));
        Iterator<TraceNode> actualIt = deser.children().iterator();
        Iterator<TraceNode> expectedIt = root.children().iterator();
        Map<Long, TraceNode> expectedMapping = new HashMap<>();
        Map<Long, TraceNode> actualMapping = new HashMap<>();
        while (expectedIt.hasNext()) {
            assertTrue(actualIt.hasNext());
            TraceNode actualNode = actualIt.next();
            TraceNode expectedNode = expectedIt.next();
            expectedMapping.put(expectedNode.timestamp(), expectedNode);
            actualMapping.put(actualNode.timestamp(), actualNode);
        }
        assertFalse(expectedIt.hasNext());
        assertFalse(actualIt.hasNext());
        for (long timestamp : expectedMapping.keySet()) {
            assertTraceEqual(actualMapping.get(timestamp), expectedMapping.get(timestamp));
        }
    }

    @Test
    public void test_long() throws IOException {
        TraceNode root = new TraceNode(14L, 5);
        assertThat(toJson(root), is("{\"timestamp\":5,\"payload\":14}"));
        assertSerialize(root);
    }

    @Test
    public void test_double() throws IOException {
        TraceNode root = new TraceNode(3.5, 5);
        assertThat(toJson(root), is("{\"timestamp\":5,\"payload\":3.5}"));
        assertSerialize(root);
    }

    @Test
    public void test_bool() throws IOException {
        TraceNode root = new TraceNode(true, 5);
        assertThat(toJson(root), is("{\"timestamp\":5,\"payload\":true}"));
        assertSerialize(root);
    }

    @Test
    public void test_string() throws IOException {
        TraceNode root = new TraceNode("bar", 5);
        assertThat(toJson(root), is("{\"timestamp\":5,\"payload\":\"bar\"}"));
        assertSerialize(root);
    }

    @Test
    public void test_unknown() throws IOException {
        TraceNode root = new TraceNode(new ArrayList<String>(), 5);
        assertThat(toJson(root), is("{\"timestamp\":5}"));
    }

    @Test
    public void test_null() throws IOException {
        TraceNode root = new TraceNode(null, 5);
        assertThat(toJson(root), is("{\"timestamp\":5}"));
        assertSerialize(root);
    }

    @Test
    public void test_bytearray() throws IOException {
        TraceNode root = new TraceNode(new byte[] { 3, 5 }, 5);
        assertThat(toJson(root), is("{\"timestamp\":5,\"payload\":\"0x0305\"}"));
        assertSerialize(root);
    }
}
