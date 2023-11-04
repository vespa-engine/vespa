// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Simon Thoresen Hult
 */
public class IndexingOutputsTestCase {

    @Test
    void requireThatOutputOtherFieldThrows() throws ParseException {
        try {
            var schema = """
                    search indexing_output_other_field {
                        document indexing_output_other_field {
                            field foo type string {
                                indexing: index bar
                            }
                        }
                        field bar type string {
                        }
                    }
                    """;
            ApplicationBuilder.createFromString(schema);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("For schema 'indexing_output_other_field', field 'foo': Indexing expression 'index bar' " +
                         "attempts to write to a field other than 'foo'.",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    void requireThatOutputConflictThrows() throws ParseException {
        try {
            var schema = """
                    search indexing_output_confict {
                        document indexing_output_confict {
                            field foo type string {
                            }
                        }
                        field bar type string {
                            indexing: input foo | attribute | lowercase | index
                        }
                    }
                    """;
            ApplicationBuilder.createFromString(schema);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("For schema 'indexing_output_confict', field 'bar': For expression 'index bar': Attempting " +
                         "to assign conflicting values to field 'bar'.",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    void requireThatSummaryFieldSourceIsPopulated() throws ParseException {
        var sd = """
                search renamed {
                  document renamed {
                    field foo type string { }
                  }
                  field bar type string {
                    indexing: input foo | summary
                    summary baz { }
                    summary dyn_baz { dynamic }
                  }
                }
                """;
        var builder = ApplicationBuilder.createFromString(sd);
        var schema = builder.getSchema();
        assertEquals("{ input foo | summary baz | summary bar; }",
                schema.getConcreteField("bar").getIndexingScript().toString());
    }
}
