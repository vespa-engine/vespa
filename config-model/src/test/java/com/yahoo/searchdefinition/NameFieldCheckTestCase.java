// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
/**
 * Tests that "name" is not allowed as name for a field.
 * 
 * And that duplicate names are not allowed. 
 *
 * @author <a href="mailto:larschr@yahoo-inc.com">Lars Christian Jensen</a>
 */
public class NameFieldCheckTestCase extends SearchDefinitionTestCase {

    @Test
    public void testNameField() throws IOException, ParseException {
        try {
            SearchBuilder.buildFromFile("src/test/examples/name-check.sd");
            fail("Should throw exception.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testDuplicateNamesInSearchDifferentType() {
        try {
            SearchBuilder.buildFromFile("src/test/examples/duplicatenamesinsearchdifferenttype.sd");
            fail("Should throw exception.");
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(e.getMessage().matches(".*Duplicate.*different type.*"));
        }
    }

    @Test
    public void testDuplicateNamesInDoc() {
        try {
            SearchBuilder.buildFromFile("src/test/examples/duplicatenamesindoc.sd");
            fail("Should throw exception.");
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(e.getMessage().matches(".*Duplicate.*"));
        }
    }

}
