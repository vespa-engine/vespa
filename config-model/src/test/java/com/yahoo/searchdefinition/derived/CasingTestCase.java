// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.SchemaTestCase;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * Correct casing for derived attributes
 *
 * @author vegardh
 */
public class CasingTestCase extends SchemaTestCase {

    @Test
    public void testCasing() throws IOException, ParseException {
        Search search = SearchBuilder.buildFromFile("src/test/examples/casing.sd");
        assertEquals(search.getIndex("color").getName(), "color");
        assertEquals(search.getIndex("Foo").getName(), "Foo");
        assertEquals(search.getIndex("Price").getName(), "Price");
        assertEquals(search.getAttribute("artist").getName(), "artist");
        assertEquals(search.getAttribute("Drummer").getName(), "Drummer");
        assertEquals(search.getAttribute("guitarist").getName(), "guitarist");
        assertEquals(search.getAttribute("title").getName(), "title");
        assertEquals(search.getAttribute("Trumpetist").getName(), "Trumpetist");
        assertEquals(search.getAttribute("Saxophonist").getName(), "Saxophonist");
        assertEquals(search.getAttribute("TenorSaxophonist").getName(), "TenorSaxophonist");
        assertEquals(search.getAttribute("Flutist").getName(), "Flutist");
    }
}
