// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.derived.AbstractExportingTestCase;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

import static com.yahoo.schema.processing.AssertIndexingScript.assertIndexing;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Simon Thoresen Hult
 */
public class IndexingValidationTestCase extends AbstractExportingTestCase {

    @Test
    void testAttributeChanged() throws ParseException {
        try {
            var schema = """
                    search indexing_attribute_changed {
                        document indexing_attribute_changed {
                            field foo type string {
                                indexing: summary | lowercase | attribute
                            }
                        }
                    }
                    """;
            ApplicationBuilder.createFromString(schema);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("For schema 'indexing_attribute_changed', field 'foo': For expression 'attribute foo': " +
                         "Attempting to assign conflicting values to field 'foo'.",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    void testAttributeOther() throws ParseException {
        try {
            var schema = """
                    search indexing_attribute_other {
                        document indexing_attribute_other {
                            field foo type string {
                                indexing: attribute bar
                            }
                        }
                    }
                    """;
            ApplicationBuilder.createFromString(schema);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("For schema 'indexing_attribute_other', field 'foo': Indexing expression 'attribute bar' " +
                         "attempts to write to a field other than 'foo'.",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    void testIndexChanged() throws ParseException {
        try {
            var schema = """
                    search indexing_index_changed {
                        document indexing_index_changed {
                            field foo type string {
                                indexing: attribute | lowercase | index
                            }
                        }
                    }
                    """;
            ApplicationBuilder.createFromString(schema);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("For schema 'indexing_index_changed', field 'foo': For expression 'index foo': " +
                         "Attempting to assign conflicting values to field 'foo'.",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    void testIndexOther() throws ParseException {
        try {
            var schema = """
                    search indexing_index_other {
                        document indexing_index_other {
                            field foo type string {
                                indexing: index bar\s
                            }
                        }
                    }
                    """;
            ApplicationBuilder.createFromString(schema);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("For schema 'indexing_index_other', field 'foo': Indexing expression 'index bar' " +
                         "attempts to write to a field other than 'foo'.",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    void testSummaryChanged() throws ParseException {
        try {
            var schema = """
                    search indexing_summary_fail {
                        document indexing_summary_fail {
                            field foo type string {
                                indexing: index | lowercase | summary\s
                            }
                        }
                    }
                    """;
            ApplicationBuilder.createFromString(schema);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("For schema 'indexing_summary_fail', field 'foo': For expression 'summary foo': Attempting " +
                         "to assign conflicting values to field 'foo'.",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    void testSummaryOther() throws ParseException {
        try {
            var schema = """
                    search indexing_summary_other {
                        document indexing_summary_other {
                            field foo type string {
                                indexing: summary bar
                            }
                        }
                    }
                    """;
            ApplicationBuilder.createFromString(schema);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("For schema 'indexing_summary_other', field 'foo': Indexing expression 'summary bar' " +
                         "attempts to write to a field other than 'foo'.",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    void testExtraField() throws IOException, ParseException {
        assertIndexing(
                Arrays.asList("clear_state | guard { input my_index | tokenize normalize stem:\"BEST\" | index my_index | summary my_index }",
                        "clear_state | guard { input my_input | tokenize normalize stem:\"BEST\" | index my_extra | summary my_extra }"),
                ApplicationBuilder.buildFromFile("src/test/examples/indexing_extra.sd"));
    }

    @Test
    void requireThatMultilineOutputConflictThrows() throws ParseException {
        try {
            var schema = """
                    search indexing_multiline_output_confict {
                        document indexing_multiline_output_confict {
                            field foo type string {
                            }
                            field bar type string {
                            }
                            field baz type string {
                            }
                        }
                        field cox type string {
                            indexing {
                                input foo | attribute;
                                input bar | index;
                                input baz | summary;
                            }
                        }
                    }
                    """;
            ApplicationBuilder.createFromString(schema);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("For schema 'indexing_multiline_output_confict', field 'cox': For expression 'index cox': " +
                         "Attempting to assign conflicting values to field 'cox'.",
                         Exceptions.toMessageString(e));
        }
    }

}
