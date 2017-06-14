// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests that "name" is not allowed as name for a field.
 * 
 * And that duplicate names are not allowed. 
 *
 * @author Lars Christian Jensen
 */
public class NameFieldCheckTestCase extends SearchDefinitionTestCase {

    @Test
    public void testNameField() throws IOException, ParseException {
        try {
            SearchBuilder.buildFromFile("src/test/examples/name-check.sd");
            fail("Should throw exception.");
        } catch (Exception expected) {
            // Success
        }
    }

    @Test
    public void testDuplicateNamesInSearchDifferentType() {
        try {
            SearchBuilder.buildFromFile("src/test/examples/duplicatenamesinsearchdifferenttype.sd");
            fail("Should throw exception.");
        } catch (Exception e) {
            assertEquals("For search 'duplicatenamesinsearch', field 'grpphotoids64': Incompatible types. Expected Array<long> for index field 'grpphotoids64', got string.", e.getMessage());
        }
    }

    @Test
    public void testDuplicateNamesInDoc() {
        try {
            SearchBuilder.buildFromFile("src/test/examples/duplicatenamesindoc.sd");
            fail("Should throw exception.");
        } catch (Exception e) {
            assertTrue(e.getMessage().matches(".*Duplicate.*"));
        }
    }

}
