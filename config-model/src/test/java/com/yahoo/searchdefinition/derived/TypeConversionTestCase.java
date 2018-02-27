// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.document.DataType;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchDefinitionTestCase;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.processing.Processing;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
/**
 * Tests automatic type conversion using multifield indices
 *
 * @author bratseth
 */
public class TypeConversionTestCase extends SearchDefinitionTestCase {

    /** Tests that exact-string stuff is not spilled over to the default index */
    @Test
    public void testExactStringToStringTypeConversion() {
        Search search = new Search("test", null);
        RankProfileRegistry rankProfileRegistry = RankProfileRegistry.createRankProfileRegistryWithBuiltinRankProfiles(search);
        SDDocumentType document = new SDDocumentType("test");
        search.addDocument(document);
        SDField a = new SDField("a", DataType.STRING);
        a.parseIndexingScript("{ index }");
        document.addField(a);

        Processing.process(search, new BaseDeployLogger(), rankProfileRegistry, new QueryProfiles(), true);
        DerivedConfiguration derived = new DerivedConfiguration(search, rankProfileRegistry, new QueryProfileRegistry());
        IndexInfo indexInfo = derived.getIndexInfo();
        assertFalse(indexInfo.hasCommand("default", "compact-to-term"));
    }

}
