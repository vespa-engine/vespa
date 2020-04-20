// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author Ulf Lilleengen
 */
public class ConfigPayloadBuilderTest {

    private ConfigPayloadBuilder builderWithDef;

    private Cursor createSlime(ConfigPayloadBuilder builder) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        builder.resolve(root);
        return root;
    }

    @Before
    public void setupBuilder() {
        ConfigDefinition def = new ConfigDefinition("foo", "1", "bar");
        def.addBoolDef("boolval");
        ConfigDefinition mystruct = def.structDef("mystruct");
        mystruct.addIntDef("foofield");
        def.arrayDef("myarray").setTypeSpec(new ConfigDefinition.TypeSpec("myarray", "int", null, null, null, null));
        ConfigDefinition myinnerarray = def.innerArrayDef("myinnerarray");
        myinnerarray.addIntDef("foo");
        builderWithDef = new ConfigPayloadBuilder(def);
    }

    @Test
    public void require_that_simple_fields_are_set() {
        ConfigPayloadBuilder builder = new ConfigPayloadBuilder();
        builder.setField("foo", "bar");
        builder.setField("bar", "barz");
        builder.getObject("bar").setValue("baz");
        Cursor root = createSlime(builder);
        assertEquals("bar", root.field("foo").asString());
        assertEquals("baz", root.field("bar").asString());
    }

    @Test
    public void require_that_simple_fields_can_be_overwritten() {
        ConfigPayloadBuilder builder = new ConfigPayloadBuilder();
        builder.setField("foo", "bar");
        builder.setField("foo", "baz");
        Cursor root = createSlime(builder);
        // XXX: Not sure if this is the _right_ behavior.
        assertEquals("baz", root.field("foo").asString());
    }

    @Test
    public void require_that_struct_values_are_created() {
        ConfigPayloadBuilder builder = new ConfigPayloadBuilder();
        ConfigPayloadBuilder struct = builder.getObject("foo");
        struct.setField("bar", "baz");

        Cursor root = createSlime(builder);
        Cursor s = root.field("foo");
        assertEquals("baz", s.field("bar").asString());
    }


    @Test
    public void require_that_maps_are_created() {
        ConfigPayloadBuilder builder = new ConfigPayloadBuilder();
        ConfigPayloadBuilder.MapBuilder map = builder.getMap("foo");
        assertNotNull(map);
    }

    @Test
    public void require_that_maps_support_simple_values() {
        ConfigPayloadBuilder builder = new ConfigPayloadBuilder();
        ConfigPayloadBuilder.MapBuilder map = builder.getMap("foo");
        map.put("fookey", "foovalue");
        map.put("barkey", "barvalue");
        map.put("bazkey", "bazvalue");
        map.put("fookey", "lolvalue");
        assertThat(map.getElements().size(), is(3));
        Cursor root = createSlime(builder);
        Cursor a = root.field("foo");
        assertThat(a.field("barkey").asString(), is("barvalue"));
        assertThat(a.field("bazkey").asString(), is("bazvalue"));
        assertThat(a.field("fookey").asString(), is("lolvalue"));
    }

    @Test
    public void require_that_arrays_are_created() {
        ConfigPayloadBuilder builder = new ConfigPayloadBuilder();
        ConfigPayloadBuilder.Array array = builder.getArray("foo");
        assertNotNull(array);
    }

    @Test
    public void require_that_arrays_can_be_appended_simple_values() {
        ConfigPayloadBuilder builder = new ConfigPayloadBuilder();
        ConfigPayloadBuilder.Array array = builder.getArray("foo");
        array.append("bar");
        array.append("baz");
        array.append("bim");
        assertThat(array.getElements().size(), is(3));
        Cursor root = createSlime(builder);
        Cursor a = root.field("foo");
        assertEquals("bar", a.entry(0).asString());
        assertEquals("baz", a.entry(1).asString());
        assertEquals("bim", a.entry(2).asString());
    }

    @Test
    public void require_that_arrays_can_be_indexed_simple_values() {
        ConfigPayloadBuilder builder = new ConfigPayloadBuilder();
        ConfigPayloadBuilder.Array array = builder.getArray("foo");
        array.set(3, "bar");
        array.set(2, "baz");
        array.set(6, "bim");
        array.set(4, "bum");

        Cursor root = createSlime(builder);
        Cursor a = root.field("foo");
        assertEquals("bar", a.entry(0).asString());
        assertEquals("baz", a.entry(1).asString());
        assertEquals("bim", a.entry(2).asString());
        assertEquals("bum", a.entry(3).asString());
    }

    @Test
    public void require_that_arrays_can_be_appended_structs() {
        ConfigPayloadBuilder builder = new ConfigPayloadBuilder();
        ConfigPayloadBuilder.Array array = builder.getArray("foo");
        ConfigPayloadBuilder elem1 = array.append();
        elem1.setField("bar", "baz");
        ConfigPayloadBuilder elem2 = array.append();
        elem2.setField("foo", "bar");
        Cursor root = createSlime(builder);
        Cursor a = root.field("foo");
        assertEquals("baz", a.entry(0).field("bar").asString());
        assertEquals("bar", a.entry(1).field("foo").asString());
    }

    @Test
    public void require_that_arrays_can_be_indexed_structs() {
        ConfigPayloadBuilder builder = new ConfigPayloadBuilder();
        ConfigPayloadBuilder.Array array = builder.getArray("foo");
        ConfigPayloadBuilder elem1 = array.set(4);
        elem1.setField("bar", "baz");
        ConfigPayloadBuilder elem2 = array.set(2);
        elem2.setField("foo", "bar");

        Cursor root = createSlime(builder);
        Cursor a = root.field("foo");
        assertEquals("baz", a.entry(0).field("bar").asString());
        assertEquals("bar", a.entry(1).field("foo").asString());
    }

    @Test
    public void require_that_get_can_be_used_instead() {
        ConfigPayloadBuilder builder = new ConfigPayloadBuilder();
        ConfigPayloadBuilder.Array array = builder.getArray("foo");
        // Causes append to be used
        ConfigPayloadBuilder b1 = array.get(0);
        ConfigPayloadBuilder b2 = array.get(1);
        ConfigPayloadBuilder b3 = array.get(0);
        ConfigPayloadBuilder b4 = array.get(1);
        assertThat(b1, is(b3));
        assertThat(b2, is(b4));

        ConfigPayloadBuilder.Array array_indexed = builder.getArray("bar");
        ConfigPayloadBuilder bi3 = array_indexed.set(3);
        ConfigPayloadBuilder bi1 = array_indexed.set(1);
        ConfigPayloadBuilder bi32 = array_indexed.get(3);
        ConfigPayloadBuilder bi12 = array_indexed.get(1);
        assertThat(bi12, is(bi1));
        assertThat(bi32, is(bi3));
    }

    @Test
    public void require_that_builders_can_be_merged() {
        ConfigPayloadBuilder b1 = new ConfigPayloadBuilder();
        ConfigPayloadBuilder b2 = new ConfigPayloadBuilder();
        ConfigPayloadBuilder b3 = new ConfigPayloadBuilder();
        b1.setField("aaa", "a");
        b1.getObject("bbb").setField("ccc", "ddd");

        b2.setField("aaa", "b");
        b2.getObject("bbb").setField("ccc", "eee");
        b2.getArray("eee").append("kkk");
        b2.setField("uuu", "ttt");

        b3.setField("aaa", "c");
        b3.getObject("bbb").setField("ccc", "fff");
        b3.getArray("eee").append("lll");
        b3.setField("uuu", "vvv");

        assertThat(b1.override(b2), is(b1));
        assertThat(b1.override(b3), is(b1));

        Cursor b1root = createSlime(b1);
        Cursor b2root = createSlime(b2);
        Cursor b3root = createSlime(b3);
        assertThat(b3root.field("aaa").asString(), is("c"));
        assertThat(b3root.field("bbb").field("ccc").asString(), is("fff"));
        assertThat(b3root.field("eee").children(), is(1));
        assertThat(b3root.field("eee").entry(0).asString(), is("lll"));
        assertThat(b3root.field("uuu").asString(), is("vvv"));

        assertThat(b2root.field("aaa").asString(), is("b"));
        assertThat(b2root.field("bbb").field("ccc").asString(), is("eee"));
        assertThat(b2root.field("eee").children(), is(1));
        assertThat(b2root.field("eee").entry(0).asString(), is("kkk"));
        assertThat(b2root.field("uuu").asString(), is("ttt"));

        assertThat(b1root.field("aaa").asString(), is("c"));
        assertThat(b1root.field("bbb").field("ccc").asString(), is("fff"));
        assertThat(b1root.field("eee").children(), is(2));
        assertThat(b1root.field("eee").entry(0).asString(), is("kkk"));
        assertThat(b1root.field("eee").entry(1).asString(), is("lll"));
        assertThat(b1root.field("uuu").asString(), is("vvv"));
    }

    @Test(expected=IllegalStateException.class)
    public void require_that_append_conflicts_with_index() {
        ConfigPayloadBuilder builder = new ConfigPayloadBuilder();
        ConfigPayloadBuilder.Array array = builder.getArray("foo");
        array.set(0, "bar");
        array.append("baz");
    }

    @Test(expected=IllegalStateException.class)
    public void require_that_index_conflicts_with_append() {
        ConfigPayloadBuilder builder = new ConfigPayloadBuilder();
        ConfigPayloadBuilder.Array array = builder.getArray("foo");
        array.append("baz");
        array.set(0, "bar");
    }

    @Test
    public void require_that_builder_can_be_created_from_payload() throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("foo", "bar");
        Cursor obj = root.setObject("foorio");
        obj.setString("bar", "bam");
        Cursor obj2 = obj.setObject("bario");
        obj2.setString("bim", "bul");
        Cursor a2 = obj.setArray("blim");
        Cursor arrayobj = a2.addObject();
        arrayobj.setString("fim", "fam");
        Cursor arrayobj2 = a2.addObject();
        arrayobj2.setString("blim", "blam");
        Cursor a1 = root.setArray("arrio");
        a1.addString("himbio");

        ConfigPayloadBuilder builder = new ConfigPayloadBuilder(new ConfigPayload(slime));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ConfigPayload.fromBuilder(builder).serialize(baos, new JsonFormat(true));
        assertThat(baos.toString(), is("{\"foo\":\"bar\",\"foorio\":{\"bar\":\"bam\",\"bario\":{\"bim\":\"bul\"},\"blim\":[{\"fim\":\"fam\"},{\"blim\":\"blam\"}]},\"arrio\":[\"himbio\"]}"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void require_that_values_are_verified_against_def() {
        builderWithDef.setField("boolval", "true");
        builderWithDef.setField("boolval", "invalid");
    }

    @Test(expected=IllegalArgumentException.class)
    public void require_that_arrays_must_exist() {
        builderWithDef.getArray("arraydoesnotexist");
    }

    @Test(expected=IllegalArgumentException.class)
    public void require_that_structs_must_exist() {
        builderWithDef.getObject("structdoesnotexist");
    }

    @Test(expected=IllegalArgumentException.class)
    public void require_that_definition_is_passed_to_childstruct() {
        ConfigPayloadBuilder nestedStruct = builderWithDef.getObject("mystruct");
        nestedStruct.setField("doesnotexit", "foo");
    }

    @Test(expected=IllegalArgumentException.class)
    public void require_that_definition_is_passed_to_childstruct_but_invalid_field_will_throw() {
        ConfigPayloadBuilder nestedStruct = builderWithDef.getObject("mystruct");
        nestedStruct.setField("foofield", "invalid");
    }

    @Test
    public void require_that_definition_is_passed_to_childarray() {
        ConfigPayloadBuilder.Array nestedArray = builderWithDef.getArray("myarray");
        nestedArray.append("1337");
    }

    @Test(expected=IllegalArgumentException.class)
    public void require_that_definition_is_passed_to_childarray_but_invalid_field_will_throw() {
        ConfigPayloadBuilder.Array nestedArray = builderWithDef.getArray("myarray");
        nestedArray.append("invalid");
    }

    @Test
    public void require_that_definition_is_passed_to_inner_array_with_append() {
        ConfigPayloadBuilder.Array innerArray = builderWithDef.getArray("myinnerarray");
        ConfigPayloadBuilder innerStruct = innerArray.append();
        assertNotNull(innerStruct.getConfigDefinition());
        innerStruct.setField("foo", "1337");
    }

    @Test(expected=IllegalArgumentException.class)
    public void require_that_definition_is_passed_to_inner_array_with_append_but_invalid_field_will_throw() {
        ConfigPayloadBuilder.Array innerArray = builderWithDef.getArray("myinnerarray");
        ConfigPayloadBuilder innerStruct = innerArray.append();
        innerStruct.setField("foo", "invalid");
    }

    @Test
    public void require_that_definition_is_passed_to_inner_array_with_index() {
        ConfigPayloadBuilder.Array innerArray = builderWithDef.getArray("myinnerarray");
        ConfigPayloadBuilder innerStruct = innerArray.set(1);
        assertNotNull(innerStruct.getConfigDefinition());
        innerStruct.setField("foo", "1337");
    }

    @Test(expected=IllegalArgumentException.class)
    public void require_that_definition_is_passed_to_inner_array_with_index_but_invalid_field_will_throw() {
        ConfigPayloadBuilder.Array innerArray = builderWithDef.getArray("myinnerarray");
        ConfigPayloadBuilder innerStruct = innerArray.set(1);
        innerStruct.setField("foo", "invalid");
    }

}
