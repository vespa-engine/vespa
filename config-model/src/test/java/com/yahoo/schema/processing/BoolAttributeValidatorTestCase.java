// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import static com.yahoo.schema.ApplicationBuilder.createFromString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author geirst
 */
public class BoolAttributeValidatorTestCase {

    @Test
    void array_of_bool_attribute_is_not_supported() throws ParseException {
        try {
            createFromString(getSd("field b type array<bool> { indexing: attribute }"));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("For schema 'test', field 'b': Only single value bool attribute fields are supported",
                    e.getMessage());
        }
    }

    @Test
    void weigtedset_of_bool_attribute_is_not_supported() throws ParseException {
        try {
            createFromString(getSd("field b type weightedset<bool> { indexing: attribute }"));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("For schema 'test', field 'b': Only single value bool attribute fields are supported",
                    e.getMessage());
        }
    }

    private String getSd(String field) {
        return joinLines(
                "schema test {",
                "  document test {",
                "    " + field,
                "  }",
                "}");
    }

}
