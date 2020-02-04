// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.text.Utf8;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class SlimeUtilsTest {

    @Test
    public void test_copying_slime_types_into_cursor() {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("foo", "foobie");
        Cursor subobj = root.setObject("bar");

        Slime slime2 = new Slime();
        Cursor root2 = slime2.setObject();
        root2.setString("a", "a");
        root2.setLong("b", 2);
        root2.setBool("c", true);
        root2.setDouble("d", 3.14);
        root2.setData("e", new byte[]{0x64});
        root2.setNix("f");

        SlimeUtils.copyObject(slime2.get(), subobj);

        assertThat(root.toString(), is("{\"foo\":\"foobie\",\"bar\":{\"a\":\"a\",\"b\":2,\"c\":true,\"d\":3.14,\"e\":\"0x64\",\"f\":null}}"));
    }

    @Test
    public void test_copying_slime_arrays_into_cursor() {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("foo", "foobie");
        Cursor subobj = root.setObject("bar");

        Slime slime2 = new Slime();
        Cursor root2 = slime2.setObject();
        Cursor array = root2.setArray("a");
        array.addString("foo");
        array.addLong(4);
        array.addBool(true);
        array.addDouble(3.14);
        array.addNix();
        array.addData(new byte[]{0x64});
        Cursor objinner = array.addObject();
        objinner.setString("inner", "binner");

        SlimeUtils.copyObject(slime2.get(), subobj);

        assertThat(root.toString(), is("{\"foo\":\"foobie\",\"bar\":{\"a\":[\"foo\",4,true,3.14,null,\"0x64\",{\"inner\":\"binner\"}]}}"));
    }

    @Test
    public void test_slime_to_json() throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("foo", "foobie");
        root.setObject("bar");
        String json = Utf8.toString(SlimeUtils.toJsonBytes(slime));
        assertThat(json, is("{\"foo\":\"foobie\",\"bar\":{}}"));
    }

    @Test
    public void test_json_to_slime() {
        byte[] json = Utf8.toBytes("{\"foo\":\"foobie\",\"bar\":{}}");
        Slime slime = SlimeUtils.jsonToSlime(json);
        assertThat(slime.get().field("foo").asString(), is("foobie"));
        assertTrue(slime.get().field("bar").valid());
    }

}
