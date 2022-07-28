// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.document.DataType;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.AbstractSchemaTestCase;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.processing.Processing;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests automatic type conversion using multifield indices
 *
 * @author bratseth
 */
public class TypeConversionTestCase extends AbstractSchemaTestCase {

    /** Tests that exact-string stuff is not spilled over to the default index */
    @Test
    void testExactStringToStringTypeConversion() {
        Schema schema = new Schema("test", MockApplicationPackage.createEmpty());
        RankProfileRegistry rankProfileRegistry = RankProfileRegistry.createRankProfileRegistryWithBuiltinRankProfiles(schema);
        SDDocumentType document = new SDDocumentType("test");
        schema.addDocument(document);
        SDField a = new SDField(document, "a", DataType.STRING);
        a.parseIndexingScript("{ index }");
        document.addField(a);

        new Processing().process(schema, new BaseDeployLogger(), rankProfileRegistry, new QueryProfiles(),
                true, false, Set.of());
        DerivedConfiguration derived = new DerivedConfiguration(schema, rankProfileRegistry);
        IndexInfo indexInfo = derived.getIndexInfo();
        assertFalse(indexInfo.hasCommand("default", "compact-to-term"));
    }

}
