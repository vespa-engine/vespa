// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.Schema;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.AbstractSchemaTestCase;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** @author bratseth */
public class WeightedSetSummaryToTestCase extends AbstractSchemaTestCase {

    @Test
    void testRequireThatImplicitFieldsAreCreated() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/weightedset-summaryto.sd");
        assertNotNull(schema);
    }

}
