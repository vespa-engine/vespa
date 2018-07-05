// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

import static com.yahoo.searchdefinition.processing.AssertSearchBuilder.assertBuildFails;

/**
 * @author Simon Thoresen Hult
 */
public class IndexingInputsTestCase {

    @Test
    public void requireThatExtraFieldInputExtraFieldThrows() throws IOException, ParseException {
        assertBuildFails("src/test/examples/indexing_extra_field_input_extra_field.sd",
                         "For search 'indexing_extra_field_input_extra_field', field 'bar': Indexing script refers " +
                         "to field 'bar' which does not exist in document type " +
                         "'indexing_extra_field_input_extra_field'.");
    }

    @Test
    public void requireThatExtraFieldInputImplicitThrows() throws IOException, ParseException {
        assertBuildFails("src/test/examples/indexing_extra_field_input_implicit.sd",
                         "For search 'indexing_extra_field_input_implicit', field 'foo': Indexing script refers to " +
                         "field 'foo' which does not exist in document type 'indexing_extra_field_input_implicit'.");
    }

    @Test
    public void requireThatExtraFieldInputNullThrows() throws IOException, ParseException {
        assertBuildFails("src/test/examples/indexing_extra_field_input_null.sd",
                         "For search 'indexing_extra_field_input_null', field 'foo': Indexing script refers to field " +
                         "'foo' which does not exist in document type 'indexing_extra_field_input_null'.");
    }

    @Test
    public void requireThatExtraFieldInputSelfThrows() throws IOException, ParseException {
        assertBuildFails("src/test/examples/indexing_extra_field_input_self.sd",
                         "For search 'indexing_extra_field_input_self', field 'foo': Indexing script refers to field " +
                         "'foo' which does not exist in document type 'indexing_extra_field_input_self'.");
    }
}
