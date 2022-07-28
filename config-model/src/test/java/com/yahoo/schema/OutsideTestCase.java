// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests settings outside the document
 *
 * @author  bratseth
 */
public class OutsideTestCase extends AbstractSchemaTestCase {

    @Test
    void testOutsideIndex() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/outsidedoc.sd");

        Index defaultIndex = schema.getIndex("default");
        assertTrue(defaultIndex.isPrefix());
        assertEquals("default.default", defaultIndex.aliasIterator().next());
    }

    @Test
    void testOutsideSummary() throws IOException, ParseException {
        ApplicationBuilder.buildFromFile("src/test/examples/outsidesummary.sd");
    }

}
