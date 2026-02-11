// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.data.access;

import org.junit.Test;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.ArrayTraverser;
import com.yahoo.data.access.ObjectTraverser;
import com.yahoo.data.access.Type;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

abstract public class InspectorConformanceTestBase {

    public abstract static class Try {
        abstract void f();
        public Exception call() {
            try {
                f();
            } catch (Exception e) {
                return e;
            }
            return null;
        }
    }

    public static class Entries implements ArrayTraverser {
        List<Inspector> entries = new ArrayList<>();
        public void entry(int idx, Inspector inspector) {
            entries.add(inspector);
        }
        public Entries traverse(Inspector value) {
            value.traverse(this);
            return this;
        }
        public Entries iterate(Inspector value) {
            for (Inspector itr: value.entries()) {
                entries.add(itr);
            }
            return this;
        }
        public Entries add(Inspector value) {
            entries.add(value);
            return this;
        }
    }

    public static class Fields implements ObjectTraverser {
        Map<String,Inspector> fields = new HashMap<>();
        public void field(String name, Inspector inspector) {
            fields.put(name, inspector);
        }
        public Fields traverse(Inspector value) {
            value.traverse(this);
            return this;
        }
        public Fields iterate(Inspector value) {
            for (Map.Entry<String,Inspector> itr: value.fields()) {
                fields.put(itr.getKey(), itr.getValue());
            }
            return this;
        }
        public Fields add(String name, Inspector value) {
            fields.put(name, value);
            return this;
        }
    }

    // This method must be implemented by all tests of concrete
    // implementations to return an inspector to a structured object
    // on the following form (for an example, take a look at
    // com.yahoo.data.access.simple.InspectorConformanceTestCase):
    //
    // ARRAY {
    //   [0]: EMPTY
    //   [1]: BOOL: true
    //   [2]: LONG: 10
    //   [3]: DOUBLE: 5.75
    //   [4]: OBJECT {
    //     "foo": STRING: "foo_value"
    //     "bar": DATA: 0x04 0x02
    //     "nested": ARRAY {
    //       [0]: OBJECT {
    //         "hidden": STRING: "treasure"
    //       }
    //     }
    //   }
    // }
    public abstract Inspector getData();

    @Test
    public void testSelfInspectableInspector() throws Exception {
        final Inspector value = getData();
        final Inspector self = value.inspect();
        assertThat(self, is(value));
    }

    @Test
    public void testInvalidValue() throws Exception {
        final Inspector value = getData().entry(10).field("bogus").entry(0);
        assertThat(value.valid(), is(false));
        assertThat(value.type(), is(Type.EMPTY));
        assertThat(value.entryCount(), is(0));
        assertThat(value.fieldCount(), is(0));
        assertThat(new Try(){void f() { value.asBool(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asLong(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asDouble(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asString(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asUtf8(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asData(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(value.asBool(true), is(true));
        assertThat(value.asLong(50), is(50L));
        assertThat(value.asDouble(20.25), is(20.25));
        assertThat(value.asString("default"), is("default"));
        assertThat(value.asUtf8("utf8".getBytes("UTF-8")), is("utf8".getBytes("UTF-8")));
        assertThat(value.asData("data".getBytes("UTF-8")), is("data".getBytes("UTF-8")));
        assertThat(new Entries().traverse(value).entries.size(), is(0));
        assertThat(new Fields().traverse(value).fields.size(), is(0));
        assertThat(value.entry(0).valid(), is(false));
        assertThat(value.field("foo").valid(), is(false));
        assertThat(new Entries().iterate(value).entries.size(), is(0));
        assertThat(new Fields().iterate(value).fields.size(), is(0));
    }

    @Test
    public void testEmptyValue() throws Exception {
        final Inspector value = getData().entry(0);
        assertThat(value.valid(), is(true));
        assertThat(value.type(), is(Type.EMPTY));
        assertThat(value.entryCount(), is(0));
        assertThat(value.fieldCount(), is(0));
        assertThat(value.asBool(), is(false));
        assertThat(value.asLong(), is(0L));
        assertThat(value.asDouble(), is(0.0));
        assertThat(value.asString(), is(""));
        assertThat(value.asUtf8(), is(new byte[0]));
        assertThat(value.asData(), is(new byte[0]));
        assertThat(value.asBool(true), is(true));
        assertThat(value.asLong(50), is(50L));
        assertThat(value.asDouble(20.25), is(20.25));
        assertThat(value.asString("default"), is("default"));
        assertThat(value.asUtf8("utf8".getBytes("UTF-8")), is("utf8".getBytes("UTF-8")));
        assertThat(value.asData("data".getBytes("UTF-8")), is("data".getBytes("UTF-8")));
        assertThat(new Entries().traverse(value).entries.size(), is(0));
        assertThat(new Fields().traverse(value).fields.size(), is(0));
        assertThat(value.entry(0).valid(), is(false));
        assertThat(value.field("foo").valid(), is(false));
        assertThat(new Entries().iterate(value).entries.size(), is(0));
        assertThat(new Fields().iterate(value).fields.size(), is(0));
    }

    @Test
    public void testBoolValue() throws Exception {
        final Inspector value = getData().entry(1);
        assertThat(value.valid(), is(true));
        assertThat(value.type(), is(Type.BOOL));
        assertThat(value.entryCount(), is(0));
        assertThat(value.fieldCount(), is(0));
        assertThat(value.asBool(), is(true));
        assertThat(new Try(){void f() { value.asLong(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asDouble(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asString(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asUtf8(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asData(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(value.asBool(false), is(true));
        assertThat(value.asLong(50), is(50L));
        assertThat(value.asDouble(20.25), is(20.25));
        assertThat(value.asString("default"), is("default"));
        assertThat(value.asUtf8("utf8".getBytes("UTF-8")), is("utf8".getBytes("UTF-8")));
        assertThat(value.asData("data".getBytes("UTF-8")), is("data".getBytes("UTF-8")));
        assertThat(new Entries().traverse(value).entries.size(), is(0));
        assertThat(new Fields().traverse(value).fields.size(), is(0));
        assertThat(value.entry(0).valid(), is(false));
        assertThat(value.field("foo").valid(), is(false));
        assertThat(new Entries().iterate(value).entries.size(), is(0));
        assertThat(new Fields().iterate(value).fields.size(), is(0));
    }

    @Test
    public void testLongValue() throws Exception {
        final Inspector value = getData().entry(2);
        assertThat(value.valid(), is(true));
        assertThat(value.type(), is(Type.LONG));
        assertThat(value.entryCount(), is(0));
        assertThat(value.fieldCount(), is(0));
        assertThat(new Try(){void f() { value.asBool(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(value.asLong(), is(10L));
        assertThat(value.asDouble(), is(10.0));
        assertThat(new Try(){void f() { value.asString(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asUtf8(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asData(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(value.asBool(true), is(true));
        assertThat(value.asLong(50), is(10L));
        assertThat(value.asDouble(20.25), is(10.0));
        assertThat(value.asString("default"), is("default"));
        assertThat(value.asUtf8("utf8".getBytes("UTF-8")), is("utf8".getBytes("UTF-8")));
        assertThat(value.asData("data".getBytes("UTF-8")), is("data".getBytes("UTF-8")));
        assertThat(new Entries().traverse(value).entries.size(), is(0));
        assertThat(new Fields().traverse(value).fields.size(), is(0));
        assertThat(value.entry(0).valid(), is(false));
        assertThat(value.field("foo").valid(), is(false));
        assertThat(new Entries().iterate(value).entries.size(), is(0));
        assertThat(new Fields().iterate(value).fields.size(), is(0));
    }

    @Test
    public void testDoubleValue() throws Exception {
        final Inspector value = getData().entry(3);
        assertThat(value.valid(), is(true));
        assertThat(value.type(), is(Type.DOUBLE));
        assertThat(value.entryCount(), is(0));
        assertThat(value.fieldCount(), is(0));
        assertThat(new Try(){void f() { value.asBool(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(value.asLong(), is(5L));
        assertThat(value.asDouble(), is(5.75));
        assertThat(new Try(){void f() { value.asString(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asUtf8(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asData(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(value.asBool(true), is(true));
        assertThat(value.asLong(50), is(5L));
        assertThat(value.asDouble(20.25), is(5.75));
        assertThat(value.asString("default"), is("default"));
        assertThat(value.asUtf8("utf8".getBytes("UTF-8")), is("utf8".getBytes("UTF-8")));
        assertThat(value.asData("data".getBytes("UTF-8")), is("data".getBytes("UTF-8")));
        assertThat(new Entries().traverse(value).entries.size(), is(0));
        assertThat(new Fields().traverse(value).fields.size(), is(0));
        assertThat(value.entry(0).valid(), is(false));
        assertThat(value.field("foo").valid(), is(false));
        assertThat(new Entries().iterate(value).entries.size(), is(0));
        assertThat(new Fields().iterate(value).fields.size(), is(0));
    }

    @Test
    public void testStringValue() throws Exception {
        final Inspector value = getData().entry(4).field("foo");
        assertThat(value.valid(), is(true));
        assertThat(value.type(), is(Type.STRING));
        assertThat(value.entryCount(), is(0));
        assertThat(value.fieldCount(), is(0));
        assertThat(new Try(){void f() { value.asBool(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asLong(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asDouble(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(value.asString(), is("foo_value"));
        assertThat(value.asUtf8(), is("foo_value".getBytes("UTF-8")));
        assertThat(new Try(){void f() { value.asData(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(value.asBool(true), is(true));
        assertThat(value.asLong(50), is(50L));
        assertThat(value.asDouble(20.25), is(20.25));
        assertThat(value.asString("default"), is("foo_value"));
        assertThat(value.asUtf8("utf8".getBytes("UTF-8")), is("foo_value".getBytes("UTF-8")));
        assertThat(value.asData("data".getBytes("UTF-8")), is("data".getBytes("UTF-8")));
        assertThat(new Entries().traverse(value).entries.size(), is(0));
        assertThat(new Fields().traverse(value).fields.size(), is(0));
        assertThat(value.entry(0).valid(), is(false));
        assertThat(value.field("foo").valid(), is(false));
        assertThat(new Entries().iterate(value).entries.size(), is(0));
        assertThat(new Fields().iterate(value).fields.size(), is(0));
    }

    @Test
    public void testDataValue() throws Exception {
        final Inspector value = getData().entry(4).field("bar");
        assertThat(value.valid(), is(true));
        assertThat(value.type(), is(Type.DATA));
        assertThat(value.entryCount(), is(0));
        assertThat(value.fieldCount(), is(0));
        assertThat(new Try(){void f() { value.asBool(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asLong(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asDouble(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asString(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asUtf8(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(value.asData(), is(new byte[] { (byte)4, (byte)2 }));
        assertThat(value.asBool(true), is(true));
        assertThat(value.asLong(50), is(50L));
        assertThat(value.asDouble(20.25), is(20.25));
        assertThat(value.asString("default"), is("default"));
        assertThat(value.asUtf8("utf8".getBytes("UTF-8")), is("utf8".getBytes("UTF-8")));
        assertThat(value.asData("data".getBytes("UTF-8")), is(new byte[] { (byte)4, (byte)2 }));
        assertThat(new Entries().traverse(value).entries.size(), is(0));
        assertThat(new Fields().traverse(value).fields.size(), is(0));
        assertThat(value.entry(0).valid(), is(false));
        assertThat(value.field("foo").valid(), is(false));
        assertThat(new Entries().iterate(value).entries.size(), is(0));
        assertThat(new Fields().iterate(value).fields.size(), is(0));
    }

    @Test
    public void testArrayValue() throws Exception {
        final Inspector value = getData();
        List<Inspector> expected_entries = new Entries()
                                           .add(value.entry(0))
                                           .add(value.entry(1))
                                           .add(value.entry(2))
                                           .add(value.entry(3))
                                           .add(value.entry(4)).entries;
        assertThat(value.valid(), is(true));
        assertThat(value.type(), is(Type.ARRAY));
        assertThat(value.entryCount(), is(expected_entries.size()));
        assertThat(value.fieldCount(), is(0));
        assertThat(new Try(){void f() { value.asBool(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asLong(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asDouble(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asString(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asUtf8(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asData(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(value.asBool(true), is(true));
        assertThat(value.asLong(50), is(50L));
        assertThat(value.asDouble(20.25), is(20.25));
        assertThat(value.asString("default"), is("default"));
        assertThat(value.asUtf8("utf8".getBytes("UTF-8")), is("utf8".getBytes("UTF-8")));
        assertThat(value.asData("data".getBytes("UTF-8")), is("data".getBytes("UTF-8")));
        assertThat(new Entries().traverse(value).entries, is(expected_entries));
        assertThat(new Fields().traverse(value).fields.size(), is(0));
        assertThat(value.entry(10).valid(), is(false));
        assertThat(value.field("foo").valid(), is(false));
        assertThat(new Entries().iterate(value).entries, is(expected_entries));
        assertThat(new Fields().iterate(value).fields.size(), is(0));
    }

    @Test
    public void testObjectValue() throws Exception {
        final Inspector value = getData().entry(4);
        Map<String,Inspector> expected_fields = new Fields()
                                                .add("foo", value.field("foo"))
                                                .add("bar", value.field("bar"))
                                                .add("nested", value.field("nested")).fields;
        assertThat(value.valid(), is(true));
        assertThat(value.type(), is(Type.OBJECT));
        assertThat(value.entryCount(), is(0));
        assertThat(value.fieldCount(), is(expected_fields.size()));
        assertThat(new Try(){void f() { value.asBool(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asLong(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asDouble(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asString(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asUtf8(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(new Try(){void f() { value.asData(); }}.call(), instanceOf(IllegalStateException.class));
        assertThat(value.asBool(true), is(true));
        assertThat(value.asLong(50), is(50L));
        assertThat(value.asDouble(20.25), is(20.25));
        assertThat(value.asString("default"), is("default"));
        assertThat(value.asUtf8("utf8".getBytes("UTF-8")), is("utf8".getBytes("UTF-8")));
        assertThat(value.asData("data".getBytes("UTF-8")), is("data".getBytes("UTF-8")));
        assertThat(new Entries().traverse(value).entries.size(), is(0));
        assertThat(new Fields().traverse(value).fields, is(expected_fields));
        assertThat(value.entry(0).valid(), is(false));
        assertThat(value.field("bogus").valid(), is(false));
        assertThat(new Entries().iterate(value).entries.size(), is(0));
        assertThat(new Fields().iterate(value).fields, is(expected_fields));
    }

    @Test
    public void testNesting() throws Exception {
        Inspector value1 = getData().entry(4).field("nested");
        assertThat(value1.type(), is(Type.ARRAY));
        Inspector value2 = value1.entry(0);
        assertThat(value2.type(), is(Type.OBJECT));
        Inspector value3 = value2.field("hidden");
        assertThat(value3.type(), is(Type.STRING));
        assertThat(value3.asString(), is("treasure"));
    }
}
