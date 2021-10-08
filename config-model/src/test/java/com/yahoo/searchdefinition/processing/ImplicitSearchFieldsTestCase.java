// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.SchemaTestCase;
import com.yahoo.searchdefinition.derived.DerivedConfiguration;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ImplicitSearchFieldsTestCase extends SchemaTestCase {

    @Test
    public void testRequireThatExtraFieldsAreIncluded() throws IOException, ParseException {
        Search search = SearchBuilder.buildFromFile("src/test/examples/nextgen/extrafield.sd");
        assertNotNull(search);

        SDDocumentType docType = search.getDocument();
        assertNotNull(docType);
        assertNotNull(docType.getField("foo"));
        assertNotNull(docType.getField("bar"));
        assertEquals(2, docType.getFieldCount());
    }

    @Test
    public void testRequireThatSummaryFieldsAreIncluded() throws IOException, ParseException {
        Search search = SearchBuilder.buildFromFile("src/test/examples/nextgen/summaryfield.sd");
        assertNotNull(search);

        SDDocumentType docType = search.getDocument();
        assertNotNull(docType);
        assertNotNull(docType.getField("foo"));
        assertNotNull(docType.getField("bar"));
        assertNotNull(docType.getField("cox"));
        assertEquals(3, docType.getFieldCount());
    }

    @Test
    public void testRequireThatBoldedSummaryFieldsAreIncluded() throws IOException, ParseException {
        Search search = SearchBuilder.buildFromFile("src/test/examples/nextgen/boldedsummaryfields.sd");
        assertNotNull(search);

        SDDocumentType docType = search.getDocument();
        assertNotNull(docType);
        assertNotNull(docType.getField("foo"));
        assertNotNull(docType.getField("bar"));
        assertNotNull(docType.getField("baz"));
        assertNotNull(docType.getField("cox"));
        assertEquals(4, docType.getFieldCount());
    }

    @Test
    public void testRequireThatUntransformedSummaryFieldsAreIgnored() throws IOException, ParseException {
        Search search = SearchBuilder.buildFromFile("src/test/examples/nextgen/untransformedsummaryfields.sd");
        assertNotNull(search);

        SDDocumentType docType = search.getDocument();
        assertNotNull(docType);
        assertNotNull(docType.getField("foo"));
        assertNotNull(docType.getField("bar"));
        assertNotNull(docType.getField("baz"));
        assertEquals(3, docType.getFieldCount());
    }

    @Test
    public void testRequireThatDynamicSummaryFieldsAreIgnored() throws IOException, ParseException {
        Search search = SearchBuilder.buildFromFile("src/test/examples/nextgen/dynamicsummaryfields.sd");
        assertNotNull(search);

        SDDocumentType docType = search.getDocument();
        assertNotNull(docType);
        assertNotNull(docType.getField("foo"));
        assertNotNull(docType.getField("bar"));
        assertEquals(2, docType.getFieldCount());
    }

    @Test
    public void testRequireThatDerivedConfigurationWorks() throws IOException, ParseException {
        SearchBuilder sb = new SearchBuilder();
        sb.importFile("src/test/examples/nextgen/simple.sd");
        sb.build();
        assertNotNull(sb.getSearch());
        new DerivedConfiguration(sb.getSearch(), sb.getRankProfileRegistry());
    }

}
