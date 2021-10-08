// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.document.DataType;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.processing.Processing;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Tests that documents ids are treated as they should
 *
 * @author bratseth
 */
public class IdTestCase extends AbstractExportingTestCase {

    @Test
    public void testExplicitUpperCaseIdField() {
        Search search = new Search("test");
        SDDocumentType document = new SDDocumentType("test");
        search.addDocument(document);
        SDField uri = new SDField("URI", DataType.URI);
        uri.parseIndexingScript("{ summary | index }");
        document.addField(uri);

        new Processing().process(search, new BaseDeployLogger(), new RankProfileRegistry(), new QueryProfiles(), true, false);

        assertNull(document.getField("uri"));
        assertNull(document.getField("Uri"));
        assertNotNull(document.getField("URI"));
    }

    @Test
    public void testCompleteDeriving() throws Exception {
        assertCorrectDeriving("id");
    }

}
