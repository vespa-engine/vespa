// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.derived.AbstractExportingTestCase;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Simon Thoresen Hult
 */
public class ReservedDocumentNamesTestCase extends AbstractExportingTestCase {

    @Test
    void requireThatPositionIsAReservedDocumentName() throws IOException, ParseException {
        try {
            assertCorrectDeriving("reserved_position");
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("For schema 'position': Document name 'position' is reserved.", e.getMessage());
        }
    }
}
