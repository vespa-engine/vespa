// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.schema.derived.TestableDeployLogger;
import com.yahoo.schema.parser.ParseException;
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
                    """
                    search simple {
                      document name-check {
                        field title type string {
                          indexing: summary | index
                        }
                        # reserved name, should trigger error
                        field sddocname type string {
                          indexing: index
                        }
                      }
                    }""");
            fail("Should throw exception.");
        } catch (Exception expected) {
            // Success
        }
    }

    @Test
    void testCaseSensitiveDuplicate() {
        try {
            ApplicationBuilder.createFromString(
                    """
                    search myDoc {
                      document {
                        field foo type int {
                          indexing: attribute
                        }
                        field fOo type string {
                          indexing: index
                        }
                      }
                    }""");
            fail("Should throw exception.");
        } catch (Exception e) {
            assertEquals("document 'myDoc' error: Duplicate (case insensitively) field 'fOo' in document type 'myDoc'",
                         e.getMessage());
        }
    }

    @Test
    void testDuplicateNameButDifferentDocumentTypes() throws ParseException {
         // Based on example from https://github.com/vespa-engine/vespa/issues/33088
        var parent = """
                schema cities {
                    document cities {
                        field id type int {
                            indexing: summary | attribute
                        }
                    }
                }
                """;
        var child = """
                schema users {
                    document users {
                        field id type long {
                            indexing: summary | attribute
                        }
                        field city_ref type reference<cities> {
                            indexing: attribute
                        }
                    }
                    import field city_ref.id as city_id {}
                }
                """;
        var logger = new TestableDeployLogger();
        ApplicationBuilder.createFromStrings(logger, child, parent);
        assertEquals(0, logger.warnings.size());
    }

}
