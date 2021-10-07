// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import static com.yahoo.searchdefinition.SearchBuilder.createFromString;
import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author geirst
 */
public class BoolAttributeValidatorTestCase {

    @Test
    public void array_of_bool_attribute_is_not_supported() throws ParseException {
        try {
            createFromString(getSd("field b type array<bool> { indexing: attribute }"));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("For search 'test', field 'b': Only single value bool attribute fields are supported",
                         e.getMessage());
        }
    }

    @Test
    public void weigtedset_of_bool_attribute_is_not_supported() throws ParseException {
        try {
            createFromString(getSd("field b type weightedset<bool> { indexing: attribute }"));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("For search 'test', field 'b': Only single value bool attribute fields are supported",
                    e.getMessage());
        }
    }

    private String getSd(String field) {
        return joinLines("search test {",
                "  document test {",
                "    " + field,
                "  }",
                "}");
    }

}
