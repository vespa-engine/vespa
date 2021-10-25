// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.Schema;
import com.yahoo.searchdefinition.SchemaBuilder;
import com.yahoo.searchdefinition.AbstractSchemaTestCase;
import com.yahoo.searchdefinition.derived.DerivedConfiguration;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ImplicitSchemaFieldsTestCase extends AbstractSchemaTestCase {

    @Test
    public void testRequireThatExtraFieldsAreIncluded() throws IOException, ParseException {
        Schema schema = SchemaBuilder.buildFromFile("src/test/examples/nextgen/extrafield.sd");
        assertNotNull(schema);

        SDDocumentType docType = schema.getDocument();
        assertNotNull(docType);
        assertNotNull(docType.getField("foo"));
        assertNotNull(docType.getField("bar"));
        assertEquals(2, docType.getFieldCount());
    }

    @Test
    public void testRequireThatSummaryFieldsAreIncluded() throws IOException, ParseException {
        Schema schema = SchemaBuilder.buildFromFile("src/test/examples/nextgen/summaryfield.sd");
        assertNotNull(schema);

        SDDocumentType docType = schema.getDocument();
        assertNotNull(docType);
        assertNotNull(docType.getField("foo"));
        assertNotNull(docType.getField("bar"));
        assertNotNull(docType.getField("cox"));
        assertEquals(3, docType.getFieldCount());
    }

    @Test
    public void testRequireThatBoldedSummaryFieldsAreIncluded() throws IOException, ParseException {
        Schema schema = SchemaBuilder.buildFromFile("src/test/examples/nextgen/boldedsummaryfields.sd");
        assertNotNull(schema);

        SDDocumentType docType = schema.getDocument();
        assertNotNull(docType);
        assertNotNull(docType.getField("foo"));
        assertNotNull(docType.getField("bar"));
        assertNotNull(docType.getField("baz"));
        assertNotNull(docType.getField("cox"));
        assertEquals(4, docType.getFieldCount());
    }

    @Test
    public void testRequireThatUntransformedSummaryFieldsAreIgnored() throws IOException, ParseException {
        Schema schema = SchemaBuilder.buildFromFile("src/test/examples/nextgen/untransformedsummaryfields.sd");
        assertNotNull(schema);

        SDDocumentType docType = schema.getDocument();
        assertNotNull(docType);
        assertNotNull(docType.getField("foo"));
        assertNotNull(docType.getField("bar"));
        assertNotNull(docType.getField("baz"));
        assertEquals(3, docType.getFieldCount());
    }

    @Test
    public void testRequireThatDynamicSummaryFieldsAreIgnored() throws IOException, ParseException {
        Schema schema = SchemaBuilder.buildFromFile("src/test/examples/nextgen/dynamicsummaryfields.sd");
        assertNotNull(schema);

        SDDocumentType docType = schema.getDocument();
        assertNotNull(docType);
        assertNotNull(docType.getField("foo"));
        assertNotNull(docType.getField("bar"));
        assertEquals(2, docType.getFieldCount());
    }

    @Test
    public void testRequireThatDerivedConfigurationWorks() throws IOException, ParseException {
        SchemaBuilder sb = new SchemaBuilder();
        sb.importFile("src/test/examples/nextgen/simple.sd");
        sb.build();
        assertNotNull(sb.getSchema());
        new DerivedConfiguration(sb.getSchema(), sb.getRankProfileRegistry());
    }

}
