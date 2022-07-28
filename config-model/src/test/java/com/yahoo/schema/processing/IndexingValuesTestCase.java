// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.yahoo.schema.processing.AssertSearchBuilder.assertBuildFails;
import static com.yahoo.schema.processing.AssertSearchBuilder.assertBuilds;

/**
 * @author Simon Thoresen Hult
 */
public class IndexingValuesTestCase {

    @Test
    void requireThatModifyFieldNoOutputDoesNotThrow() throws IOException, ParseException {
        assertBuilds("src/test/examples/indexing_modify_field_no_output.sd");
    }

    @Test
    void requireThatInputOtherFieldThrows() throws IOException, ParseException {
        assertBuildFails("src/test/examples/indexing_input_other_field.sd",
                "For schema 'indexing_input_other_field', field 'bar': Indexing expression 'input foo' " +
                        "attempts to modify the value of the document field 'bar'. " +
                        "Use a field outside the document block instead.");
    }

}
