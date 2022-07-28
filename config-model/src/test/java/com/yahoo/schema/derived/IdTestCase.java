// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.document.DataType;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.processing.Processing;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests that documents ids are treated as they should
 *
 * @author bratseth
 */
public class IdTestCase extends AbstractExportingTestCase {

    @Test
    void testExplicitUpperCaseIdField() {
        Schema schema = new Schema("test", MockApplicationPackage.createEmpty());
        SDDocumentType document = new SDDocumentType("test");
        schema.addDocument(document);
        SDField uri = new SDField(document, "URI", DataType.URI);
        uri.parseIndexingScript("{ summary | index }");
        document.addField(uri);

        new Processing().process(schema, new BaseDeployLogger(), new RankProfileRegistry(), new QueryProfiles(),
                true, false, Set.of());

        assertNull(document.getField("uri"));
        assertNull(document.getField("Uri"));
        assertNotNull(document.getField("URI"));
    }

    @Test
    void testCompleteDeriving() throws Exception {
        assertCorrectDeriving("id");
    }

}
