// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.parser.ParseException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public abstract class AssertSearchBuilder {

    public static void assertBuilds(String searchDefinitionFileName) throws IOException, ParseException {
        assertNotNull(ApplicationBuilder.buildFromFile(searchDefinitionFileName));
    }

    public static void assertBuildFails(String searchDefinitionFileName, String expectedException)
            throws IOException, ParseException {
        try {
            ApplicationBuilder.buildFromFile(searchDefinitionFileName);
            fail(searchDefinitionFileName);
        } catch (IllegalArgumentException e) {
            assertEquals(expectedException, e.getMessage());
        }
    }
}
