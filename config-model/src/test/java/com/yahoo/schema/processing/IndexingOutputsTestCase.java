// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.yahoo.schema.processing.AssertSearchBuilder.assertBuildFails;


/**
 * @author Simon Thoresen Hult
 */
public class IndexingOutputsTestCase {

    @Test
    void requireThatOutputOtherFieldThrows() throws IOException, ParseException {
        assertBuildFails("src/test/examples/indexing_output_other_field.sd",
                "For schema 'indexing_output_other_field', field 'foo': Indexing expression 'index bar' " +
                        "attempts to write to a field other than 'foo'.");
    }

    @Test
    void requireThatOutputConflictThrows() throws IOException, ParseException {
        assertBuildFails("src/test/examples/indexing_output_conflict.sd",
                "For schema 'indexing_output_confict', field 'bar': For expression 'index bar': Attempting " +
                        "to assign conflicting values to field 'bar'.");
    }
}
