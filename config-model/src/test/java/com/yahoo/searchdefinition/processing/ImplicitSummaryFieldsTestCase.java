// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.Schema;
import com.yahoo.searchdefinition.SchemaBuilder;
import com.yahoo.searchdefinition.AbstractSchemaTestCase;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ImplicitSummaryFieldsTestCase extends AbstractSchemaTestCase {

    @Test
    public void testRequireThatImplicitFieldsAreCreated() throws IOException, ParseException {
        Schema schema = SchemaBuilder.buildFromFile("src/test/examples/implicitsummaryfields.sd");
        assertNotNull(schema);

        DocumentSummary docsum = schema.getSummary("default");
        assertNotNull(docsum);
        assertNotNull(docsum.getSummaryField("rankfeatures"));
        assertNotNull(docsum.getSummaryField("summaryfeatures"));
        assertEquals(2, docsum.getSummaryFields().size());
    }
}
