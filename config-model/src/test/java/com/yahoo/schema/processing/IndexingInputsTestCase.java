// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.yahoo.schema.processing.AssertSearchBuilder.assertBuildFails;

/**
 * @author Simon Thoresen Hult
 */
public class IndexingInputsTestCase {

    @Test
    void requireThatExtraFieldInputExtraFieldThrows() throws IOException, ParseException {
        assertBuildFails("src/test/examples/indexing_extra_field_input_extra_field.sd",
                "For schema 'indexing_extra_field_input_extra_field', field 'bar': Indexing script refers " +
                        "to field 'bar' which does not exist in document type " +
                        "'indexing_extra_field_input_extra_field', and is not a mutable attribute.");
    }

    @Test
    void requireThatExtraFieldInputImplicitThrows() throws IOException, ParseException {
        assertBuildFails("src/test/examples/indexing_extra_field_input_implicit.sd",
                "For schema 'indexing_extra_field_input_implicit', field 'foo': Indexing script refers to " +
                        "field 'foo' which does not exist in document type 'indexing_extra_field_input_implicit', and is not a mutable attribute.");
    }

    @Test
    void requireThatExtraFieldInputNullThrows() throws IOException, ParseException {
        assertBuildFails("src/test/examples/indexing_extra_field_input_null.sd",
                "For schema 'indexing_extra_field_input_null', field 'foo': Indexing script refers to field " +
                        "'foo' which does not exist in document type 'indexing_extra_field_input_null', and is not a mutable attribute.");
    }

    @Test
    void requireThatExtraFieldInputSelfThrows() throws IOException, ParseException {
        assertBuildFails("src/test/examples/indexing_extra_field_input_self.sd",
                "For schema 'indexing_extra_field_input_self', field 'foo': Indexing script refers to field " +
                        "'foo' which does not exist in document type 'indexing_extra_field_input_self', and is not a mutable attribute.");
    }

}
