// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Tests deriving of struct fields with 'indexing: index' in array of struct and map fields.
 *
 * @author arnej
 */
public class StructFieldIndexTestCase extends AbstractExportingTestCase {

    @Test
    void testArrayOfStructIndex() throws IOException, ParseException {
        assertCorrectDeriving("array_of_struct_index");
    }

}
