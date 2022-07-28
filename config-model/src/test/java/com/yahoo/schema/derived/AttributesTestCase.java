// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Tests attribute settings
 *
 * @author bratseth
 */
public class AttributesTestCase extends AbstractExportingTestCase {

    @Test
    void testDocumentDeriving() throws IOException, ParseException {
        assertCorrectDeriving("attributes");
    }

    @Test
    void testArrayOfStructAttribute() throws IOException, ParseException {
        assertCorrectDeriving("array_of_struct_attribute");
    }

    @Test
    void testMapOfStructAttribute() throws IOException, ParseException {
        assertCorrectDeriving("map_of_struct_attribute");
    }

    @Test
    void testMapOfPrimitiveAttribute() throws IOException, ParseException {
        assertCorrectDeriving("map_attribute");
    }

}
