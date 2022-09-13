// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.AbstractSchemaTestCase;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bratseth
 */
public class BoldingTestCase extends AbstractSchemaTestCase {

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
    void testBoldOnNonString() throws ParseException {
        try {
            ApplicationBuilder.createFromString(boldonnonstring);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("'bolding: on' for non-text field 'year4' (datatype int (code: 0)) is not allowed",
                    e.getMessage());
        }
    }

    private final String boldonwset =
            "search test {\n" +
            "    document test {\n" +
            "        field mywset type weightedset<string> {\n" +
            "            indexing: summary | index\n" +
            "            bolding: on\n" +
            "        }\n" +
            "    }\n" +
            "}\n";

    @Test
    void testBoldOnWsetThrowsException() throws ParseException {
        try {
            ApplicationBuilder.createFromString(boldonwset);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("'bolding: on' for non-text field 'mywset' (datatype WeightedSet<string> (code: 1328286588)) is not allowed",
                    e.getMessage());
        }
    }

}


