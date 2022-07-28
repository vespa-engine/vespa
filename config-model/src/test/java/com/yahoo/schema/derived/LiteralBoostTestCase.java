// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.document.DataType;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.schema.RankProfile;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.processing.Processing;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static com.yahoo.schema.processing.AssertIndexingScript.assertIndexing;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bratseth
 */
public class LiteralBoostTestCase extends AbstractExportingTestCase {

    /**
     * Tests adding of literal boost constructs
     */
    @Test
    void testLiteralBoost() {
        Schema schema = new Schema("literalboost", MockApplicationPackage.createEmpty());
        RankProfileRegistry rankProfileRegistry = RankProfileRegistry.createRankProfileRegistryWithBuiltinRankProfiles(schema);
        SDDocumentType document = new SDDocumentType("literalboost");
        schema.addDocument(document);
        SDField field1 = document.addField("a", DataType.STRING);
        field1.parseIndexingScript("{ index }");
        field1.setLiteralBoost(20);
        RankProfile other = new RankProfile("other", schema, rankProfileRegistry);
        rankProfileRegistry.add(other);
        other.addRankSetting(new RankProfile.RankSetting("a", RankProfile.RankSetting.Type.LITERALBOOST, 333));

        new Processing().process(schema, new BaseDeployLogger(), rankProfileRegistry, new QueryProfiles(),
                true, false, Set.of());
        DerivedConfiguration derived = new DerivedConfiguration(schema, rankProfileRegistry);

        // Check attribute fields
        derived.getAttributeFields(); // TODO: assert content

        // Check il script addition
        assertIndexing(Arrays.asList("clear_state | guard { input a | tokenize normalize stem:\"BEST\" | index a; }",
                        "clear_state | guard { input a | tokenize | index a_literal; }"),
                schema);

        // Check index info addition
        IndexInfo indexInfo = derived.getIndexInfo();
        assertTrue(indexInfo.hasCommand("a", "literal-boost"));
    }

    /**
     * Tests adding a literal boost in a non-default rank profile only
     */
    @Test
    void testNonDefaultRankLiteralBoost() {
        Schema schema = new Schema("literalboost", MockApplicationPackage.createEmpty());
        RankProfileRegistry rankProfileRegistry = RankProfileRegistry.createRankProfileRegistryWithBuiltinRankProfiles(schema);
        SDDocumentType document = new SDDocumentType("literalboost");
        schema.addDocument(document);
        SDField field1 = document.addField("a", DataType.STRING);
        field1.parseIndexingScript("{ index }");
        RankProfile other = new RankProfile("other", schema, rankProfileRegistry);
        rankProfileRegistry.add(other);
        other.addRankSetting(new RankProfile.RankSetting("a", RankProfile.RankSetting.Type.LITERALBOOST, 333));

        schema = ApplicationBuilder.buildFromRawSchema(schema, rankProfileRegistry, new QueryProfileRegistry());
        DerivedConfiguration derived = new DerivedConfiguration(schema, rankProfileRegistry);

        // Check il script addition
        assertIndexing(Arrays.asList("clear_state | guard { input a | tokenize normalize stem:\"BEST\" | index a; }",
                        "clear_state | guard { input a | tokenize | index a_literal; }"),
                schema);

        // Check index info addition
        IndexInfo indexInfo = derived.getIndexInfo();
        assertTrue(indexInfo.hasCommand("a", "literal-boost"));
    }

    /** Tests literal boosts in two fields going to the same index */
    @Test
    void testTwoLiteralBoostFields() {
        Schema schema = new Schema("msb", MockApplicationPackage.createEmpty());
        RankProfileRegistry rankProfileRegistry = RankProfileRegistry.createRankProfileRegistryWithBuiltinRankProfiles(schema);
        SDDocumentType document = new SDDocumentType("msb");
        schema.addDocument(document);
        SDField field1 = document.addField("title", DataType.STRING);
        field1.parseIndexingScript("{ summary | index }");
        field1.setLiteralBoost(20);
        SDField field2 = document.addField("body", DataType.STRING);
        field2.parseIndexingScript("{ summary | index }");
        field2.setLiteralBoost(20);

        schema = ApplicationBuilder.buildFromRawSchema(schema, rankProfileRegistry, new QueryProfileRegistry());
        new DerivedConfiguration(schema, rankProfileRegistry);
        assertIndexing(Arrays.asList("clear_state | guard { input title | tokenize normalize stem:\"BEST\" | summary title | index title; }",
                        "clear_state | guard { input body | tokenize normalize stem:\"BEST\" | summary body | index body; }",
                        "clear_state | guard { input title | tokenize | index title_literal; }",
                        "clear_state | guard { input body | tokenize | index body_literal; }"),
                schema);
    }

}
