// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.document;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author johsol
 */
public class FastMapSearchTest {

    @Test
    public void testKeyValueSeparatorIsDel() {
        assertEquals(String.valueOf((char) 0x7F), FastMapSearch.keyValueSeparator());
    }

    @Test
    public void testToKeyValueFieldName() {
        assertEquals("myMap$keyvalue", FastMapSearch.toKeyValueFieldName("myMap"));
    }

    @Test
    public void testToKeyValueTerm() {
        assertEquals("key" + FastMapSearch.keyValueSeparator() + "value",
                     FastMapSearch.toKeyValueTerm("key", "value"));
        assertEquals("key" + FastMapSearch.keyValueSeparator() + "8000002a",
                     FastMapSearch.toKeyValue8Term("key", 42));
        assertEquals("key" + FastMapSearch.keyValueSeparator() + "800000000000002a",
                     FastMapSearch.toKeyValue16Term("key", (long) 42));
    }

}
