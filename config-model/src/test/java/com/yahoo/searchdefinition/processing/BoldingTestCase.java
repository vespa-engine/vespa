// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.SchemaTestCase;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class BoldingTestCase extends SchemaTestCase {

    private final String boldonnonstring =
            "search boldnonstring {\n" +
            "    document boldnonstring {\n" +
            "        field title type string {\n" +
            "            indexing: summary | index\n" +
            "        }\n" +
            "\n" +
            "        field year4 type int {\n" +
            "            indexing: summary | attribute\n" +
            "            bolding: on\n" +
            "        }\n" +
            "    }\n" +
            "}\n";

    @Test
    public void testBoldOnNonString() throws ParseException {
        try {
            SearchBuilder.createFromString(boldonnonstring);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("'bolding: on' for non-text field 'year4' (datatype int (code: 0)) is not allowed",
                         e.getMessage());
        }
    }

    private final String boldonarray =
            "search boldonarray {\n" +
            "    document boldonarray {\n" +
            "        field myarray type array<string> {\n" +
            "            indexing: summary | index\n" +
            "            bolding: on\n" +
            "        }\n" +
            "    }\n" +
            "}\n";

    @Test
    public void testBoldOnArray() throws ParseException {
        try {
            SearchBuilder.createFromString(boldonarray);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("'bolding: on' for non-text field 'myarray' (datatype Array<string> (code: -1486737430)) is not allowed",
                         e.getMessage());
        }
    }

}


