// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.Schema;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.AbstractSchemaTestCase;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;

/** @author bratseth */
public class WeightedSetSummaryToTestCase extends AbstractSchemaTestCase {

    @Test
    public void testRequireThatImplicitFieldsAreCreated() throws IOException, ParseException {
        Schema schema = SearchBuilder.buildFromFile("src/test/examples/weightedset-summaryto.sd");
        assertNotNull(schema);
    }

}
