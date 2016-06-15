// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.derived.AbstractExportingTestCase;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class ReservedDocumentNamesTestCase extends AbstractExportingTestCase {

    @Test
    public void requireThatPositionIsAReservedDocumentName() throws IOException, ParseException {
        try {
            assertCorrectDeriving("reserved_position");
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("For search 'position': Document name 'position' is reserved.", e.getMessage());
        }
    }
}
