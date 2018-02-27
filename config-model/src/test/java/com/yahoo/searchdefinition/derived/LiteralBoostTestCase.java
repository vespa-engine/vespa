// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.document.DataType;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.processing.Processing;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import org.junit.Test;

import java.util.Arrays;

import static com.yahoo.searchdefinition.processing.AssertIndexingScript.assertIndexing;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class LiteralBoostTestCase extends AbstractExportingTestCase {

    /**
     * Tests adding of literal boost constructs
     */
    @Test
    public void testLiteralBoost() {
        Search search=new Search("literalboost", null);
        RankProfileRegistry rankProfileRegistry = RankProfileRegistry.createRankProfileRegistryWithBuiltinRankProfiles(search);
        SDDocumentType document=new SDDocumentType("literalboost");
        search.addDocument(document);
        SDField field1= document.addField("a", DataType.STRING);
        field1.parseIndexingScript("{ index }");
        field1.setLiteralBoost(20);
        RankProfile other=new RankProfile("other", search, rankProfileRegistry);
        rankProfileRegistry.addRankProfile(other);
        other.addRankSetting(new RankProfile.RankSetting("a", RankProfile.RankSetting.Type.LITERALBOOST, 333));

        Processing.process(search, new BaseDeployLogger(), rankProfileRegistry, new QueryProfiles(), true);
        DerivedConfiguration derived=new DerivedConfiguration(search, rankProfileRegistry, new QueryProfileRegistry());

        // Check attribute fields
        derived.getAttributeFields(); // TODO: assert content

        // Check il script addition
        assertIndexing(Arrays.asList("clear_state | guard { input a | tokenize normalize stem:\"SHORTEST\" | index a; }",
                                     "clear_state | guard { input a | tokenize | index a_literal; }"),
                       search);

        // Check index info addition
        IndexInfo indexInfo=derived.getIndexInfo();
        assertTrue(indexInfo.hasCommand("a","literal-boost"));
    }

    /**
     * Tests adding a literal boost in a non-default rank profile only
     */
    @Test
    public void testNonDefaultRankLiteralBoost() {
        Search search=new Search("literalboost", null);
        RankProfileRegistry rankProfileRegistry = RankProfileRegistry.createRankProfileRegistryWithBuiltinRankProfiles(search);
        SDDocumentType document=new SDDocumentType("literalboost");
        search.addDocument(document);
        SDField field1= document.addField("a", DataType.STRING);
        field1.parseIndexingScript("{ index }");
        RankProfile other=new RankProfile("other", search, rankProfileRegistry);
        rankProfileRegistry.addRankProfile(other);
        other.addRankSetting(new RankProfile.RankSetting("a", RankProfile.RankSetting.Type.LITERALBOOST, 333));

        search = SearchBuilder.buildFromRawSearch(search, rankProfileRegistry, new QueryProfileRegistry());
        DerivedConfiguration derived = new DerivedConfiguration(search, rankProfileRegistry, new QueryProfileRegistry());

        // Check il script addition
        assertIndexing(Arrays.asList("clear_state | guard { input a | tokenize normalize stem:\"SHORTEST\" | index a; }",
                                     "clear_state | guard { input a | tokenize | index a_literal; }"),
                       search);

        // Check index info addition
        IndexInfo indexInfo=derived.getIndexInfo();
        assertTrue(indexInfo.hasCommand("a","literal-boost"));
    }

    /** Tests literal boosts in two fields going to the same index */
    @Test
    public void testTwoLiteralBoostFields() {
        Search search=new Search("msb", null);
        RankProfileRegistry rankProfileRegistry = RankProfileRegistry.createRankProfileRegistryWithBuiltinRankProfiles(search);
        SDDocumentType document=new SDDocumentType("msb");
        search.addDocument(document);
        SDField field1= document.addField("title", DataType.STRING);
        field1.parseIndexingScript("{ summary | index }");
        field1.setLiteralBoost(20);
        SDField field2= document.addField("body", DataType.STRING);
        field2.parseIndexingScript("{ summary | index }");
        field2.setLiteralBoost(20);

        search = SearchBuilder.buildFromRawSearch(search, rankProfileRegistry, new QueryProfileRegistry());
        new DerivedConfiguration(search, rankProfileRegistry, new QueryProfileRegistry());
        assertIndexing(Arrays.asList("clear_state | guard { input title | tokenize normalize stem:\"SHORTEST\" | summary title | index title; }",
                                     "clear_state | guard { input body | tokenize normalize stem:\"SHORTEST\" | summary body | index body; }",
                                     "clear_state | guard { input title | tokenize | index title_literal; }",
                                     "clear_state | guard { input body | tokenize | index body_literal; }"),
                       search);
    }

}
