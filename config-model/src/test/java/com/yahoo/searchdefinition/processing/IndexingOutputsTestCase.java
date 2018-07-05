// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

import static com.yahoo.searchdefinition.processing.AssertSearchBuilder.assertBuildFails;


/**
 * @author Simon Thoresen Hult
 */
public class IndexingOutputsTestCase {

    @Test
    public void requireThatOutputOtherFieldThrows() throws IOException, ParseException {
        assertBuildFails("src/test/examples/indexing_output_other_field.sd",
                         "For search 'indexing_output_other_field', field 'foo': Indexing expression 'index bar' " +
                         "attempts to write to a field other than 'foo'.");
    }

    @Test
    public void requireThatOutputConflictThrows() throws IOException, ParseException {
        assertBuildFails("src/test/examples/indexing_output_conflict.sd",
                         "For search 'indexing_output_confict', field 'bar': For expression 'index bar': Attempting " +
                         "to assign conflicting values to field 'bar'.");
    }
}
