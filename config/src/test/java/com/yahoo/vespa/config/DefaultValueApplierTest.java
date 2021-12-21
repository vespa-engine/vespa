// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.codegen.DefParser;
import com.yahoo.config.codegen.InnerCNode;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.Type;
import org.junit.Test;

import java.io.StringReader;

import static org.junit.Assert.assertEquals;
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
        assertEquals("myvalue", slime.get().field("str").asString());
        assertTrue(slime.get().field("strdef").valid());
        assertEquals("foo", slime.get().field("strdef").asString());

    }

    @Test
    public void require_that_struct_fields_defaults_are_applied() {
        Slime slime = apply("nested.str string default=\"bar\"");
        assertTrue(slime.get().field("nested").valid());
        assertTrue(slime.get().field("nested").field("str").valid());
        assertEquals("bar", slime.get().field("nested").field("str").asString());
    }

    @Test
    public void require_that_arrays_of_struct_fields_defaults_are_applied() {
        Slime payload = new Slime();
        Cursor cursor = payload.setObject();
        cursor.setArray("nestedarr").addObject().setString("foo", "myfoo");
        Slime slime = apply(payload, "nestedarr[].foo string", "nestedarr[].bar string default=\"bim\"");

        assertTrue(slime.get().field("nestedarr").valid());
        assertEquals(1, slime.get().field("nestedarr").entries());
        assertTrue(slime.get().field("nestedarr").entry(0).field("foo").valid());
        assertEquals("myfoo", slime.get().field("nestedarr").entry(0).field("foo").asString());
        assertTrue(slime.get().field("nestedarr").entry(0).field("bar").valid());
        assertEquals("bim", slime.get().field("nestedarr").entry(0).field("bar").asString());
    }

    @Test
    public void require_that_arrays_of_struct_fields_defaults_when_empty() {
        Slime payload = new Slime();
        payload.setObject();
        Slime slime = apply(payload, "nestedarr[].foo string", "nestedarr[].bar string default=\"bim\"");

        assertTrue(slime.get().field("nestedarr").valid());
        assertEquals(0, slime.get().field("nestedarr").entries());
        assertEquals(Type.ARRAY, slime.get().field("nestedarr").type());
    }

    @Test
    public void require_that_maps_of_struct_fields_defaults_are_applied() {
        Slime payload = new Slime();
        Cursor cursor = payload.setObject();
        cursor.setObject("nestedmap").setObject("mykey").setString("foo", "myfoo");
        Slime slime = apply(payload, "nestedmap{}.foo string", "nestedmap{}.bar string default=\"bim\"");

        assertTrue(slime.get().field("nestedmap").valid());
        assertEquals(1, slime.get().field("nestedmap").fields());
        assertTrue(slime.get().field("nestedmap").field("mykey").field("foo").valid());
        assertEquals("myfoo", slime.get().field("nestedmap").field("mykey").field("foo").asString());
        assertTrue(slime.get().field("nestedmap").field("mykey").field("bar").valid());
        assertEquals("bim", slime.get().field("nestedmap").field("mykey").field("bar").asString());
    }
}
