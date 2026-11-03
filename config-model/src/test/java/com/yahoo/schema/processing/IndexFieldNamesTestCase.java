// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author arnej
 */
public class IndexFieldNamesTestCase {

    private static final String RESERVED_NAME_REASON =
            " Not a legal field name: starting with '_' is reserved.";

    @Test
    void testFieldNamesMustStartWithALetter() {
        assertFails("""
                    schema s {
                      document s {
                        field _ type string {
                          indexing: index
                        }
                      }
                    }
                    """,
                    "For schema 's', field '_':  Not a legal field name. Legal expression: [a-zA-Z]\\w*");
        assertFails("""
                    schema s {
                      document s {
                        field _foo type string {
                          indexing: index
                        }
                      }
                    }
                    """,
                    "For schema 's', field '_foo':  Not a legal field name. Legal expression: [a-zA-Z]\\w*");
    }

    @Test
    void testStructFieldNamesCannotBeginWithAnUnderscore() {
        // "_" is the name of the value of the element itself inside a sameElement, so a subfield by that
        // name could not be queried, and the names beginning with it are reserved for the same use.
        // Top level fields cannot have such a name by the expression above.
        assertFails("""
                    schema s {
                      document s {
                        struct e {
                          field _ type string {}
                        }
                        field arr type array<e> {
                          indexing: summary
                          struct-field _ { indexing: attribute }
                        }
                      }
                    }
                    """,
                    "For schema 's', field 'arr._': " + RESERVED_NAME_REASON);
        assertFails("""
                    schema s {
                      document s {
                        struct e {
                          field _name type string {}
                        }
                        field arr type array<e> {
                          indexing: summary
                          struct-field _name { indexing: attribute }
                        }
                      }
                    }
                    """,
                    "For schema 's', field 'arr._name': " + RESERVED_NAME_REASON);

        // Also in a struct nested below another one, such as the value of a map
        assertFails("""
                    schema s {
                      document s {
                        struct e {
                          field _ type string {}
                        }
                        field m type map<string, e> {
                          indexing: summary
                          struct-field value._ { indexing: attribute }
                        }
                      }
                    }
                    """,
                    "For schema 's', field 'm.value._': " + RESERVED_NAME_REASON);
    }

    @Test
    void testLegalFieldNamesAreAccepted() throws ParseException, IOException {
        // Struct fields with legal names, the implicit key and value fields of a map, and the internal
        // fields of a position field, are all unaffected
        ApplicationBuilder.createFromString("""
                                            schema s {
                                              document s {
                                                struct e {
                                                  field name type string {}
                                                  field with_underscore type string {}
                                                }
                                                field arr type array<e> {
                                                  indexing: summary
                                                  struct-field name { indexing: attribute }
                                                  struct-field with_underscore { indexing: attribute }
                                                }
                                                field m type map<string, e> {
                                                  indexing: summary
                                                  struct-field key { indexing: attribute }
                                                }
                                                field pos type position {
                                                  indexing: attribute
                                                }
                                              }
                                            }
                                            """);
    }

    private void assertFails(String schema, String expectedMessage) {
        var exception = assertThrows(IllegalArgumentException.class,
                                     () -> ApplicationBuilder.createFromString(schema));
        assertEquals(expectedMessage, exception.getMessage());
    }

}
