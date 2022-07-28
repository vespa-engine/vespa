// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.Schema;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.AbstractSchemaTestCase;
import com.yahoo.schema.derived.DerivedConfiguration;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ImplicitSchemaFieldsTestCase extends AbstractSchemaTestCase {

    @Test
    void testRequireThatExtraFieldsAreIncluded() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/nextgen/extrafield.sd");
        assertNotNull(schema);

        SDDocumentType docType = schema.getDocument();
        assertNotNull(docType);
        assertNotNull(docType.getField("foo"));
        assertNotNull(docType.getField("bar"));
        assertEquals(2, docType.getFieldCount());
    }

    @Test
    void testRequireThatSummaryFieldsAreIncluded() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/nextgen/summaryfield.sd");
        assertNotNull(schema);

        SDDocumentType docType = schema.getDocument();
        assertNotNull(docType);
        assertNotNull(docType.getField("foo"));
        assertNotNull(docType.getField("bar"));
        assertNotNull(docType.getField("cox"));
        assertNotNull(docType.getField("mytags"));
        assertNotNull(docType.getField("alltags"));
        assertEquals(5, docType.getFieldCount());
    }

    @Test
    void testRequireThatBoldedSummaryFieldsAreIncluded() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/nextgen/boldedsummaryfields.sd");
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
    void testRequireThatUntransformedSummaryFieldsAreIgnored() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/nextgen/untransformedsummaryfields.sd");
        assertNotNull(schema);

        SDDocumentType docType = schema.getDocument();
        assertNotNull(docType);
        assertNotNull(docType.getField("foo"));
        assertNotNull(docType.getField("bar"));
        assertNotNull(docType.getField("baz"));
        assertEquals(3, docType.getFieldCount());
    }

    @Test
    void testRequireThatDynamicSummaryFieldsAreIgnored() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/nextgen/dynamicsummaryfields.sd");
        assertNotNull(schema);

        SDDocumentType docType = schema.getDocument();
        assertNotNull(docType);
        assertNotNull(docType.getField("foo"));
        assertNotNull(docType.getField("bar"));
        assertEquals(2, docType.getFieldCount());
    }

    @Test
    void testRequireThatDerivedConfigurationWorks() throws IOException, ParseException {
        ApplicationBuilder sb = new ApplicationBuilder();
        sb.addSchemaFile("src/test/examples/nextgen/simple.sd");
        sb.build(true);
        assertNotNull(sb.getSchema());
        new DerivedConfiguration(sb.getSchema(), sb.getRankProfileRegistry());
    }

}
