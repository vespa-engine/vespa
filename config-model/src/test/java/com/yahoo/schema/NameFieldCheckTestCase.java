// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that "name" is not allowed as name for a field.
 * 
 * And that duplicate names are not allowed. 
 *
 * @author Lars Christian Jensen
 */
public class NameFieldCheckTestCase extends AbstractSchemaTestCase {

    @Test
    void testNameField() {
        try {
            ApplicationBuilder.createFromString(
                    "search simple {\n" +
                            "  document name-check {\n" +
                            "    field title type string {\n" +
                            "      indexing: summary | index\n" +
                            "    }\n" +
                            "    # reserved name, should trigger error\n" +
                            "    field sddocname type string {\n" +
                            "      indexing: index\n" +
                            "    }\n" +
                            "  }\n" +
                            "}");
            fail("Should throw exception.");
        } catch (Exception expected) {
            // Success
        }
    }

    @Test
    void testDuplicateNamesInSearchDifferentType() {
        try {
            ApplicationBuilder.createFromString(
                    "search duplicatenamesinsearch {\n" +
                            "  document {\n" +
                            "    field grpphotoids64 type string { }\n" +
                            "  }\n" +
                            "  field grpphotoids64 type array<long> {\n" +
                            "    indexing: input grpphotoids64 | split \" \" | for_each {\n" +
                            "      base64decode } | attribute\n" +
                            "  }\n" +
                            "}");
            fail("Should throw exception.");
        } catch (Exception e) {
            assertEquals("For schema 'duplicatenamesinsearch', field 'grpphotoids64': " +
                    "Incompatible types. Expected Array<long> for index field 'grpphotoids64', got string.", e.getMessage());
        }
    }

    @Test
    void testDuplicateNamesInDoc() {
        try {
            ApplicationBuilder.createFromString(
                    "search duplicatenamesindoc {\n" +
                            "  document {\n" +
                            "    field foo type int {\n" +
                            "      indexing: attribute\n" +
                            "    }\n" +
                            "    field fOo type string {\n" +
                            "      indexing: index\n" +
                            "    }\n" +
                            "  }\n" +
                            "}");
            fail("Should throw exception.");
        } catch (Exception e) {
            assertTrue(e.getMessage().matches(".*Duplicate.*"));
        }
    }

}
