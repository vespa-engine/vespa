// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.collections.Pair;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.schema.LargeRankingExpressions;
import com.yahoo.schema.RankProfile;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.AbstractSchemaTestCase;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.derived.DerivedConfiguration;
import com.yahoo.schema.derived.AttributeFields;
import com.yahoo.schema.derived.RawRankProfile;
import com.yahoo.schema.derived.TestableDeployLogger;
import com.yahoo.schema.parser.ParseException;
import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModels;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RankingExpressionsTestCase extends AbstractSchemaTestCase {

    private static Schema createSearch(String dir, ModelContext.Properties deployProperties, RankProfileRegistry rankProfileRegistry) throws IOException, ParseException {
        return ApplicationBuilder.createFromDirectory(dir, new MockFileRegistry(), new TestableDeployLogger(), deployProperties, rankProfileRegistry).getSchema();
    }

    @Test
    void testFunctions() throws IOException, ParseException {
        ModelContext.Properties deployProperties = new TestProperties();
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        Schema schema = createSearch("src/test/examples/rankingexpressionfunction", deployProperties, rankProfileRegistry);
        RankProfile functionsRankProfile = rankProfileRegistry.get(schema, "macros");
        Map<String, RankProfile.RankingExpressionFunction> functions = functionsRankProfile.getFunctions();
        assertEquals(2, functions.get("titlematch").function().arguments().size());
        assertEquals("var1", functions.get("titlematch").function().arguments().get(0));
        assertEquals("var2", functions.get("titlematch").function().arguments().get(1));
        assertEquals("var1 * var2 + 890", functions.get("titlematch").function().getBody().getRoot().toString());
        assertEquals("0.8 + 0.2 * titlematch(4,5) + 0.8 * titlematch(7,8) * closeness(distance)",
                functionsRankProfile.getFirstPhaseRanking().getRoot().toString());
        assertEquals("78 + closeness(distance)",
                functions.get("artistmatch").function().getBody().getRoot().toString());
        assertEquals(0, functions.get("artistmatch").function().arguments().size());

        RawRankProfile rawRankProfile = new RawRankProfile(functionsRankProfile, new LargeRankingExpressions(new MockFileRegistry()), new QueryProfileRegistry(),
                                                           new ImportedMlModels(), new AttributeFields(schema), deployProperties);
        List<Pair<String, String>> rankProperties = rawRankProfile.configProperties();
        assertEquals(6, rankProperties.size());

        assertEquals("rankingExpression(titlematch).rankingScript", rankProperties.get(2).getFirst());
        assertEquals("var1 * var2 + 890", rankProperties.get(2).getSecond());

        assertEquals("rankingExpression(artistmatch).rankingScript", rankProperties.get(3).getFirst());
        assertEquals("78 + closeness(distance)", rankProperties.get(3).getSecond());

        assertEquals("rankingExpression(firstphase).rankingScript", rankProperties.get(5).getFirst());
        assertEquals("0.8 + 0.2 * rankingExpression(titlematch@126063073eb2deb.ab95cd69909927c) + 0.8 * rankingExpression(titlematch@c7e4c2d0e6d9f2a1.1d4ed08e56cce2e6) * closeness(distance)", rankProperties.get(5).getSecond());

        assertEquals("rankingExpression(titlematch@c7e4c2d0e6d9f2a1.1d4ed08e56cce2e6).rankingScript", rankProperties.get(1).getFirst());
        assertEquals("7 * 8 + 890", rankProperties.get(1).getSecond());

        assertEquals("rankingExpression(titlematch@126063073eb2deb.ab95cd69909927c).rankingScript", rankProperties.get(0).getFirst());
        assertEquals("4 * 5 + 890", rankProperties.get(0).getSecond());
    }

    @Test
    void testThatIncludingFileInSubdirFails() throws IOException, ParseException {
        assertThrows(IllegalArgumentException.class, () -> {
            RankProfileRegistry registry = new RankProfileRegistry();
            Schema schema = createSearch("src/test/examples/rankingexpressioninfile", new TestProperties(), registry);
            new DerivedConfiguration(schema, registry); // rank profile parsing happens during deriving
        }); // rank profile parsing happens during deriving
    }

    private void verifyProfile(RankProfile profile, List<String> expectedFunctions, List<Pair<String, String>> rankProperties,
                               LargeRankingExpressions largeExpressions, QueryProfileRegistry queryProfiles, ImportedMlModels models,
                               AttributeFields attributes, ModelContext.Properties properties) {
        var functions = profile.getFunctions();
        assertEquals(expectedFunctions.size(), functions.size());
        for (String func : expectedFunctions) {
            assertTrue(functions.containsKey(func));
        }

        RawRankProfile raw = new RawRankProfile(profile, largeExpressions, queryProfiles, models, attributes, properties);
        assertEquals(rankProperties.size(), raw.configProperties().size());
        for (int i = 0; i < rankProperties.size(); i++) {
            assertEquals(rankProperties.get(i).getFirst(), raw.configProperties().get(i).getFirst());
            assertEquals(rankProperties.get(i).getSecond(), raw.configProperties().get(i).getSecond());
        }
    }

    private void verifySearch(Schema schema, RankProfileRegistry rankProfileRegistry, LargeRankingExpressions largeExpressions,
                              QueryProfileRegistry queryProfiles, ImportedMlModels models, ModelContext.Properties properties)
    {
        AttributeFields attributes = new AttributeFields(schema);

        verifyProfile(rankProfileRegistry.get(schema, "base"), Arrays.asList("large_f", "large_m"),
                      Arrays.asList(new Pair<>("rankingExpression(large_f).expressionName", "base.large_f"), new Pair<>("rankingExpression(large_m).expressionName", "base.large_m")),
                      largeExpressions, queryProfiles, models, attributes, properties);
        for (String child : Arrays.asList("child_a", "child_b")) {
            verifyProfile(rankProfileRegistry.get(schema, child), Arrays.asList("large_f", "large_m", "large_local_f", "large_local_m"),
                          Arrays.asList(new Pair<>("rankingExpression(large_f).expressionName", child + ".large_f"), new Pair<>("rankingExpression(large_m).expressionName", child + ".large_m"),
                            new Pair<>("rankingExpression(large_local_f).expressionName", child + ".large_local_f"), new Pair<>("rankingExpression(large_local_m).expressionName", child + ".large_local_m"),
                            new Pair<>("vespa.rank.firstphase", "rankingExpression(firstphase)"), new Pair<>("rankingExpression(firstphase).expressionName", child + ".firstphase")),
                          largeExpressions, queryProfiles, models, attributes, properties);
        }
    }

    @Test
    void testLargeInheritedFunctions() throws IOException, ParseException {
        ModelContext.Properties properties = new TestProperties();
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        LargeRankingExpressions largeExpressions = new LargeRankingExpressions(new MockFileRegistry(), 50);
        QueryProfileRegistry queryProfiles = new QueryProfileRegistry();
        ImportedMlModels models = new ImportedMlModels();
        Schema schema = createSearch("src/test/examples/largerankingexpressions", properties, rankProfileRegistry);
        verifySearch(schema, rankProfileRegistry, largeExpressions, queryProfiles, models, properties);
        // Need to verify that second derivation works as that will happen if same sd is used in multiple content clusters
        verifySearch(schema, rankProfileRegistry, largeExpressions, queryProfiles, models, properties);
    }
}
