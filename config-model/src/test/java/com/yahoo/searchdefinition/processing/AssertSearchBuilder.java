// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.parser.ParseException;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public abstract class AssertSearchBuilder {

    public static void assertBuilds(String searchDefinitionFileName) throws IOException, ParseException {
        assertNotNull(SearchBuilder.buildFromFile(searchDefinitionFileName));
    }

    public static void assertBuildFails(String searchDefinitionFileName, String expectedException)
            throws IOException, ParseException {
        try {
            SearchBuilder.buildFromFile(searchDefinitionFileName);
            fail(searchDefinitionFileName);
        } catch (IllegalArgumentException e) {
            assertEquals(expectedException, e.getMessage());
        }
    }
}
