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
 * @author bratseth
 */
public class IndexingInputsTestCase {

    @Test
    void requireThatExtraFieldInputExtraFieldThrows() throws ParseException {
        try {
            var schema = """
                    search indexing_extra_field_input_extra_field {
                        document indexing_extra_field_input_extra_field {
                        }
                        field foo type string {
                        }
                        field bar type string {
                            indexing: input bar | index
                        }
                    }
                   """;
            ApplicationBuilder.createFromString(schema);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("For schema 'indexing_extra_field_input_extra_field', field 'bar': Indexing script refers " +
                         "to field 'bar' which is neither a field in document type " +
                         "'indexing_extra_field_input_extra_field' nor a mutable attribute",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    void requireThatExtraFieldInputImplicitThrows() throws ParseException {
        try {
            var schema = """
                    search indexing_extra_field_input_implicit {
                        document indexing_extra_field_input_implicit {
                        }
                        field foo type string {
                            indexing: index
                        }
                    }
                   """;
            ApplicationBuilder.createFromString(schema);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("For schema 'indexing_extra_field_input_implicit', field 'foo': " +
                         "For expression '{ tokenize normalize stem:\"BEST\" | index foo; }': Expected string input, but no input is specified",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    void requireThatExtraFieldInputNullThrows() throws ParseException {
        try {
            var schema = """
                    search indexing_extra_field_input_null {
                        document indexing_extra_field_input_null {
                        }
                        field foo type string {
                            indexing: input foo | index
                        }
                    }
                   """;
            ApplicationBuilder.createFromString(schema);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("For schema 'indexing_extra_field_input_null', field 'foo': Indexing script refers to field " +
                         "'foo' which is neither a field in document type 'indexing_extra_field_input_null' nor a mutable attribute",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    void requireThatExtraFieldInputSelfThrows() throws ParseException {
        try {
            var schema = """
                        search indexing_extra_field_input_self {
                            document indexing_extra_field_input_self {
                            }
                            field foo type string {
                                indexing: input foo | index
                            }
                        }
                    """;
            ApplicationBuilder.createFromString(schema);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("For schema 'indexing_extra_field_input_self', field 'foo': Indexing script refers to field " +
                         "'foo' which is neither a field in document type 'indexing_extra_field_input_self' nor a mutable attribute",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    void testPlainInputInDerivedField() throws ParseException {
        var schema = """
        schema test {
            document test {
                field field1 type int {
                }
            }
            field derived1 type int {
                indexing: input field1 | attribute
            }
        }
        """;
        ApplicationBuilder.createFromString(schema);
    }

    @Test
    void testWrappedInputInDerivedField() throws ParseException {
        var schema = """
        schema test {
            document test {
                field field1 type int {
                }
            }
            field derived1 type int {
                indexing: if (input field1 == 0) { 0 } else { 1 } | attribute
            }
        }
        """;
        ApplicationBuilder.createFromString(schema);
    }

    @Test
    void testNoInputInDerivedField() throws ParseException {
        try {
            var schema = """
                    schema test {
                        document test {
                            field field1 type int {
                            }
                    }
                        field derived1 type int {
                            indexing: attribute
                        }
                    }
                    """;
            ApplicationBuilder.createFromString(schema);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("For schema 'test', field 'derived1': For expression '{ attribute derived1; }': " +
                         "Expected any input, but no input is specified",
                         Exceptions.toMessageString(e));
        }
    }

}
