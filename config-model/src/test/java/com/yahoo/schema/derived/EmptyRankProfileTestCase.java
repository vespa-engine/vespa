// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.document.DataType;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.AbstractSchemaTestCase;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import org.junit.jupiter.api.Test;

/**
 * Tests deriving rank for files from search definitions
 *
 * @author bratseth
 */
public class EmptyRankProfileTestCase extends AbstractSchemaTestCase {

    @Test
    void testDeriving() {
        Schema schema = new Schema("test", MockApplicationPackage.createEmpty());
        RankProfileRegistry rankProfileRegistry = RankProfileRegistry.createRankProfileRegistryWithBuiltinRankProfiles(schema);
        SDDocumentType doc = new SDDocumentType("test");
        schema.addDocument(doc);
        doc.addField(new SDField(doc, "a", DataType.STRING));
        SDField field = new SDField(doc, "b", DataType.STRING);
        field.setLiteralBoost(500);
        doc.addField(field);
        doc.addField(new SDField(doc, "c", DataType.STRING));

        schema = ApplicationBuilder.buildFromRawSchema(schema, rankProfileRegistry, new QueryProfileRegistry());
        new DerivedConfiguration(schema, rankProfileRegistry);
    }

}
