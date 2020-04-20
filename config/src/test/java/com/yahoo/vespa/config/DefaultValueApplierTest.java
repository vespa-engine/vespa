// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.codegen.DefParser;
import com.yahoo.config.codegen.InnerCNode;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.Type;
import org.junit.Test;

import java.io.StringReader;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class DefaultValueApplierTest {

    public Slime apply(Slime slime, String ... extraFields) {
        StringBuilder defBuilder = new StringBuilder();
        defBuilder.append("namespace=test").append("\n");
        defBuilder.append("str string").append("\n");
        for (String field : extraFields) {
            defBuilder.append(field).append("\n");
        }
        Cursor cursor = slime.get();
        cursor.setString("str", "myvalue");
        InnerCNode def = new DefParser("simpletypes", new StringReader(defBuilder.toString())).getTree();
        DefaultValueApplier applier = new DefaultValueApplier();
        return applier.applyDefaults(slime, def);
    }

    public Slime apply(String ... extraFields) {
        Slime slime = new Slime();
        slime.setObject();
        return apply(slime, extraFields);
    }

    @Test
    public void require_that_simple_defaults_are_applied() {
        Slime slime = apply("strdef string default=\"foo\"");
        assertTrue(slime.get().field("str").valid());
        assertThat(slime.get().field("str").asString(), is("myvalue"));
        assertTrue(slime.get().field("strdef").valid());
        assertThat(slime.get().field("strdef").asString(), is("foo"));

    }

    @Test
    public void require_that_struct_fields_defaults_are_applied() {
        Slime slime = apply("nested.str string default=\"bar\"");
        assertTrue(slime.get().field("nested").valid());
        assertTrue(slime.get().field("nested").field("str").valid());
        assertThat(slime.get().field("nested").field("str").asString(), is("bar"));
    }

    @Test
    public void require_that_arrays_of_struct_fields_defaults_are_applied() {
        Slime payload = new Slime();
        Cursor cursor = payload.setObject();
        cursor.setArray("nestedarr").addObject().setString("foo", "myfoo");
        Slime slime = apply(payload, "nestedarr[].foo string", "nestedarr[].bar string default=\"bim\"");

        assertTrue(slime.get().field("nestedarr").valid());
        assertThat(slime.get().field("nestedarr").entries(), is(1));
        assertTrue(slime.get().field("nestedarr").entry(0).field("foo").valid());
        assertThat(slime.get().field("nestedarr").entry(0).field("foo").asString(), is("myfoo"));
        assertTrue(slime.get().field("nestedarr").entry(0).field("bar").valid());
        assertThat(slime.get().field("nestedarr").entry(0).field("bar").asString(), is("bim"));
    }

    @Test
    public void require_that_arrays_of_struct_fields_defaults_when_empty() {
        Slime payload = new Slime();
        payload.setObject();
        Slime slime = apply(payload, "nestedarr[].foo string", "nestedarr[].bar string default=\"bim\"");

        assertTrue(slime.get().field("nestedarr").valid());
        assertThat(slime.get().field("nestedarr").entries(), is(0));
        assertThat(slime.get().field("nestedarr").type(), is(Type.ARRAY));
    }

    @Test
    public void require_that_maps_of_struct_fields_defaults_are_applied() {
        Slime payload = new Slime();
        Cursor cursor = payload.setObject();
        cursor.setObject("nestedmap").setObject("mykey").setString("foo", "myfoo");
        Slime slime = apply(payload, "nestedmap{}.foo string", "nestedmap{}.bar string default=\"bim\"");

        assertTrue(slime.get().field("nestedmap").valid());
        assertThat(slime.get().field("nestedmap").fields(), is(1));
        assertTrue(slime.get().field("nestedmap").field("mykey").field("foo").valid());
        assertThat(slime.get().field("nestedmap").field("mykey").field("foo").asString(), is("myfoo"));
        assertTrue(slime.get().field("nestedmap").field("mykey").field("bar").valid());
        assertThat(slime.get().field("nestedmap").field("mykey").field("bar").asString(), is("bim"));
    }
}
