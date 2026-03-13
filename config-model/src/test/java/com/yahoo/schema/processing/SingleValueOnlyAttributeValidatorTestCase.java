// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import static com.yahoo.schema.ApplicationBuilder.createFromString;
import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author geirst
 */
public class SingleValueOnlyAttributeValidatorTestCase {

    private static void array_attribute_is_not_supported(String type) throws ParseException {
        try {
            createFromString(getSd("field b type array<" + type + "> { indexing: attribute }"));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("For schema 'test', field 'b': Only single value " + type + " attribute fields are supported",
                    e.getMessage());
        }
    }

    private static void weightedset_attribute_is_not_supported(String type) throws ParseException {
        try {
            createFromString(getSd("field b type weightedset<" + type + "> { indexing: attribute }"));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            if (type.equals("bool")) {
                assertEquals("weightedset of trivial type '[type BUILTIN] {bool}' is not supported",
                        e.getMessage());
            } else if (type.equals("raw")) {
                assertEquals("weightedset of complex type '[type BUILTIN] {raw}' is not supported",
                        e.getMessage());
            } else {
                assertEquals("For schema 'test', field 'b': Only single value " + type + " attribute fields are supported",
                        e.getMessage());
            }
        }
    }

    @Test
    void array_of_bool_attribute_is_supported() {
        assertDoesNotThrow(() -> createFromString(getSd("field b type array<bool> { indexing: attribute }")));
    }

    @Test
    void weightedset_of_bool_attribute_is_not_supported() throws ParseException {
        weightedset_attribute_is_not_supported("bool");
    }

    @Test
    void array_of_raw_attribute_is_not_supported() throws ParseException {
        array_attribute_is_not_supported("raw");
    }

    @Test
    void weightedset_of_raw_attribute_is_not_supported() throws ParseException {
        weightedset_attribute_is_not_supported("raw");
    }

    private static String getSd(String field) {
        return joinLines(
                "schema test {",
                "  document test {",
                "    " + field,
                "  }",
                "}");
    }

}
