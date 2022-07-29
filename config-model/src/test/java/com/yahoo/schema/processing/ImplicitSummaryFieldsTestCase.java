// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.Schema;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.AbstractSchemaTestCase;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ImplicitSummaryFieldsTestCase extends AbstractSchemaTestCase {

    @Test
    void testRequireThatImplicitFieldsAreCreated() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/implicitsummaryfields.sd");
        assertNotNull(schema);

        DocumentSummary docsum = schema.getSummary("default");
        assertNotNull(docsum);
        assertNotNull(docsum.getSummaryField("rankfeatures"));
        assertNotNull(docsum.getSummaryField("summaryfeatures"));
        assertEquals(2, docsum.getSummaryFields().size());
    }
}
