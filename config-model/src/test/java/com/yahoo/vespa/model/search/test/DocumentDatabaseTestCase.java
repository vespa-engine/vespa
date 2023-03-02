// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search.test;

import com.google.common.collect.ImmutableMap;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.path.Path;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.vespa.config.search.IndexschemaConfig;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.content.ContentSearchCluster;
import com.yahoo.vespa.model.content.utils.DocType;
import com.yahoo.vespa.model.search.IndexedSearchCluster;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author geirst
 */
public class DocumentDatabaseTestCase {

    private static final double SMALL = 0.00000000000001;

    @Test
    void requireThatWeCanHaveOneSDForIndexedMode() {
        new SchemaTester().assertSingleSD("index");
    }

    @Test
    void requireThatConcurrencyIsReflectedCorrectlyForDefault() {
        verifyConcurrency("index", "", 0.50);
        verifyConcurrency("streaming", "", 1.0);
        verifyConcurrency("store-only", "", 1.0);
    }

    @Test
    void requireThatFeatureFlagConcurrencyIsReflectedCorrectlyForDefault() {
        verifyConcurrency("index", "", 0.30, 0.3);
        verifyConcurrency("streaming", "", 0.6, 0.3);
        verifyConcurrency("store-only", "", 0.8, 0.4);
    }

    @Test
    void requireThatMixedModeConcurrencyIsReflectedCorrectlyForDefault() {
        verifyConcurrency(List.of(DocType.create("a", "index"), DocType.create("b", "streaming")), "", 1.0);
    }

    @Test
    void requireThatMixedModeConcurrencyIsReflected() {
        String feedTuning = "<feeding>" +
                "  <concurrency>0.7</concurrency>" +
                "</feeding>\n";
        verifyConcurrency(List.of(DocType.create("a", "index"), DocType.create("b", "streaming")), feedTuning, 0.7);
    }

    @Test
    void requireThatConcurrencyIsReflected() {
        String feedTuning = "<feeding>" +
                "  <concurrency>0.7</concurrency>" +
                "</feeding>\n";
        verifyConcurrency("index", feedTuning, 0.7);
        verifyConcurrency("streaming", feedTuning, 0.7);
        verifyConcurrency("store-only", feedTuning, 0.7);
    }

    private void verifyConcurrency(String mode, String xmlTuning, double expectedConcurrency, double featureFlagConcurrency) {
        verifyConcurrency(List.of(DocType.create("a", mode)), xmlTuning, expectedConcurrency, featureFlagConcurrency);
    }

    private void verifyConcurrency(String mode, String xmlTuning, double expectedConcurrency) {
        verifyConcurrency(List.of(DocType.create("a", mode)), xmlTuning, expectedConcurrency, null);
    }

    private void verifyConcurrency(List<DocType> nameAndModes, String xmlTuning, double expectedConcurrency) {
        verifyConcurrency(nameAndModes, xmlTuning, expectedConcurrency, null);
    }

    private ProtonConfig getConfig(List<DocType> nameAndModes, String xmlTuning, Double featureFlagConcurrency) {
        TestProperties properties = new TestProperties();
        if (featureFlagConcurrency != null) {
            properties.setFeedConcurrency(featureFlagConcurrency);
        }
        var tester = new SchemaTester();
        VespaModel model = tester.createModel(nameAndModes, xmlTuning, new DeployState.Builder().properties(properties));
        ContentSearchCluster contentSearchCluster = model.getContentClusters().get("test").getSearch();
        return tester.getProtonConfig(contentSearchCluster);
    }

    private void verifyConcurrency(List<DocType> nameAndModes, String xmlTuning, double expectedConcurrency, Double featureFlagConcurrency) {
        ProtonConfig proton = getConfig(nameAndModes, xmlTuning, featureFlagConcurrency);
        assertEquals(expectedConcurrency, proton.feeding().concurrency(), SMALL);
    }

    private void verifyMaxflushedFollowsConcurrency(double concurrency, int maxFlushed) {
        String feedTuning = "<feeding>  <concurrency>" + concurrency +"</concurrency>" + "</feeding>\n";
        ProtonConfig proton = getConfig(List.of(DocType.create("a", "index")), feedTuning, null);
        assertEquals(maxFlushed, proton.index().maxflushed());
    }

    @Test
    public void verifyThatMaxFlushedFollowsConcurrency() {
        verifyMaxflushedFollowsConcurrency(0.1, 2);
        verifyMaxflushedFollowsConcurrency(0.50, 2);
        verifyMaxflushedFollowsConcurrency(0.51, 3);
        verifyMaxflushedFollowsConcurrency(0.75, 3);
        verifyMaxflushedFollowsConcurrency(0.76, 4);
        verifyMaxflushedFollowsConcurrency(1.0, 4);
    }

    private void verifyFeedNiceness(List<DocType> nameAndModes, Double expectedNiceness, Double featureFlagNiceness) {
        TestProperties properties = new TestProperties();
        if (featureFlagNiceness != null) {
            properties.setFeedNiceness(featureFlagNiceness);
        }
        var tester = new SchemaTester();
        VespaModel model = tester.createModel(nameAndModes, "", new DeployState.Builder().properties(properties));
        ContentSearchCluster contentSearchCluster = model.getContentClusters().get("test").getSearch();
        ProtonConfig proton = tester.getProtonConfig(contentSearchCluster);
        assertEquals(expectedNiceness, proton.feeding().niceness(), SMALL);
    }

    @Test
    void requireFeedNicenessIsReflected() {
        verifyFeedNiceness(List.of(DocType.create("a", "index")), 0.0, null);
        verifyFeedNiceness(List.of(DocType.create("a", "index")), 0.32, 0.32);
    }

    @Test
    void requireThatModeIsSet() {
        var tester = new SchemaTester();
        VespaModel model = tester.createModel(List.of(DocType.create("a", "index"),
                DocType.create("b", "streaming"),
                DocType.create("c", "store-only")), "");
        ContentSearchCluster contentSearchCluster = model.getContentClusters().get("test").getSearch();
        ProtonConfig proton = tester.getProtonConfig(contentSearchCluster);
        assertEquals(3, proton.documentdb().size());
        assertEquals(ProtonConfig.Documentdb.Mode.Enum.INDEX, proton.documentdb(0).mode());
        assertEquals("a", proton.documentdb(0).inputdoctypename());
        assertEquals(ProtonConfig.Documentdb.Mode.Enum.STREAMING, proton.documentdb(1).mode());
        assertEquals("b", proton.documentdb(1).inputdoctypename());
        assertEquals(ProtonConfig.Documentdb.Mode.Enum.STORE_ONLY, proton.documentdb(2).mode());
        assertEquals("c", proton.documentdb(2).inputdoctypename());
    }

    private void verifyInitialDocumentCount(List<DocType> nameAndModes, String xmlTuning, List<Long> local) {
        var tester = new SchemaTester();
        assertEquals(nameAndModes.size(), local.size());
        VespaModel model = tester.createModel(nameAndModes, xmlTuning);
        ContentSearchCluster contentSearchCluster = model.getContentClusters().get("test").getSearch();
        ProtonConfig proton = tester.getProtonConfig(contentSearchCluster);
        assertEquals(local.size(), proton.documentdb().size());
        for (int i = 0; i < local.size(); i++) {
            assertEquals(local.get(i).longValue(), proton.documentdb(i).allocation().initialnumdocs());
        }
    }

    @Test
    void requireThatMixedModeInitialDocumentCountIsReflectedCorrectlyForDefault() {
        final long DEFAULT = 1024L;
        verifyInitialDocumentCount(List.of(DocType.create("a", "index"), DocType.create("b", "streaming")),
                "", List.of(DEFAULT, DEFAULT));
    }

    @Test
    void requireThatMixedModeInitialDocumentCountIsReflected() {
        final long INITIAL = 1000000000L;
        String feedTuning = "<resizing>" +
                "  <initialdocumentcount>1000000000</initialdocumentcount>" +
                "</resizing>\n";
        verifyInitialDocumentCount(List.of(DocType.create("a", "index"), DocType.create("b", "streaming")),
                feedTuning, List.of(INITIAL, INITIAL));
    }

    private void assertDocTypeConfig(VespaModel model, String configId, String indexField, String attributeField) {
        IndexschemaConfig icfg = model.getConfig(IndexschemaConfig.class, configId);
        assertEquals(1, icfg.indexfield().size());
        assertEquals(indexField, icfg.indexfield(0).name());
        AttributesConfig acfg = model.getConfig(AttributesConfig.class, configId);
        assertEquals(2, acfg.attribute().size());
        assertEquals(attributeField, acfg.attribute(0).name());
        assertEquals(attributeField+"_nfa", acfg.attribute(1).name());
        RankProfilesConfig rcfg = model.getConfig(RankProfilesConfig.class, configId);
        assertEquals(6, rcfg.rankprofile().size());
    }

    @Test
    void testMultipleSchemas() {
        List<String> sds = List.of("type1", "type2", "type3");
        var tester = new SchemaTester();
        var model = tester.createModel(sds);
        IndexedSearchCluster indexedSearchCluster = (IndexedSearchCluster) model.getSearchClusters().get(0);
        ContentSearchCluster contentSearchCluster = model.getContentClusters().get("test").getSearch();
        String type1Id = "test/search/cluster.test/type1";
        String type2Id = "test/search/cluster.test/type2";
        String type3Id = "test/search/cluster.test/type3";
        {
            assertEquals(3, indexedSearchCluster.getDocumentDbs().size());
            ProtonConfig proton = tester.getProtonConfig(contentSearchCluster);
            assertEquals(3, proton.documentdb().size());
            assertEquals("type1", proton.documentdb(0).inputdoctypename());
            assertEquals(type1Id, proton.documentdb(0).configid());
            assertEquals("type2", proton.documentdb(1).inputdoctypename());
            assertEquals(type2Id, proton.documentdb(1).configid());
            assertEquals("type3", proton.documentdb(2).inputdoctypename());
            assertEquals(type3Id, proton.documentdb(2).configid());
        }
        assertDocTypeConfig(model, type1Id, "f1", "f2");
        assertDocTypeConfig(model, type2Id, "f3", "f4");
        assertDocTypeConfig(model, type3Id, "f5", "f6");
        {
            IndexInfoConfig iicfg = model.getConfig(IndexInfoConfig.class, "test/search/cluster.test");
            assertEquals(3, iicfg.indexinfo().size());
            assertEquals("type1", iicfg.indexinfo().get(0).name());
            assertEquals("type2", iicfg.indexinfo().get(1).name());
            assertEquals("type3", iicfg.indexinfo().get(2).name());
        }
        {
            AttributesConfig rac1 = model.getConfig(AttributesConfig.class, type1Id);
            assertEquals(2, rac1.attribute().size());
            assertEquals("f2", rac1.attribute(0).name());
            assertEquals("f2_nfa", rac1.attribute(1).name());
            AttributesConfig rac2 = model.getConfig(AttributesConfig.class, type2Id);
            assertEquals(2, rac2.attribute().size());
            assertEquals("f4", rac2.attribute(0).name());
            assertEquals("f4_nfa", rac2.attribute(1).name());
        }
        {
            IlscriptsConfig icfg = model.getConfig(IlscriptsConfig.class, "test/search/cluster.test");
            assertEquals(3, icfg.ilscript().size());
            assertEquals("type1", icfg.ilscript(0).doctype());
            assertEquals("type2", icfg.ilscript(1).doctype());
            assertEquals("type3", icfg.ilscript(2).doctype());
        }
    }

    @Test
    void testRankingConstants() {
        List<String> schemas = List.of("type1");
        var tester = new SchemaTester();

        // Use lz4 endings to avoid having to provide file content to be validated
        String schemaConstants =
                "  constant constant_1 {" +
                        "    file: constants/my_constant_1.json.lz4" +
                        "    type: tensor<float>(x{},y{})" +
                        "  }" +
                        "  constant constant_2 {" +
                        "    file: constants/my_constant_2.json.lz4" +
                        "    type: tensor(x[1000])" +
                        "  }";

        Map<Path, String> constants = new HashMap<>();
        constants.put(Path.fromString("constants/my_constant_1.json.lz4"), "");
        constants.put(Path.fromString("constants/my_constant_2.json.lz4"), "");
        var model = tester.createModel(schemaConstants, "", schemas, constants);
        IndexedSearchCluster indexedSearchCluster = (IndexedSearchCluster) model.getSearchClusters().get(0);
        RankingConstantsConfig.Builder b = new RankingConstantsConfig.Builder();
        indexedSearchCluster.getDocumentDbs().get(0).getConfig(b);
        RankingConstantsConfig config = b.build();
        assertEquals(2, config.constant().size());

        var constant1Config = config.constant().get(0);
        assertEquals("constant_1", constant1Config.name());
        assertEquals("constants/my_constant_1.json.lz4", constant1Config.fileref().value());
        assertEquals("tensor<float>(x{},y{})", constant1Config.type());

        var constant2Config = config.constant().get(1);
        assertEquals("constant_2", constant2Config.name());
        assertEquals("constants/my_constant_2.json.lz4", constant2Config.fileref().value());
        assertEquals("tensor(x[1000])", constant2Config.type());
    }

    @Test
    void requireThatRelevantConfigIsAvailableForClusterSearcher() {
        String inputsProfile =
                "  rank-profile inputs {" +
                        "    inputs {" +
                        "      query(foo) tensor<float>(x[10])" +
                        "      query(bar) tensor(key{},x[1000])" +
                        "    }" +
                        "  }";
        List<String> schemas = List.of("type1", "type2");
        var tester = new SchemaTester();
        VespaModel model = tester.createModelWithRankProfile(inputsProfile, schemas);
        String searcherId = "container/searchchains/chain/test/component/com.yahoo.prelude.cluster.ClusterSearcher";

        { // documentdb-info config
            DocumentdbInfoConfig dcfg = model.getConfig(DocumentdbInfoConfig.class, searcherId);
            assertEquals(2, dcfg.documentdb().size());
            assertEquals("type1", dcfg.documentdb(0).name());
            assertEquals("type2", dcfg.documentdb(1).name());
        }
        { // attributes config
            AttributesConfig acfg = model.getConfig(AttributesConfig.class, searcherId);
            assertEquals(4, acfg.attribute().size());
            assertEquals("f2", acfg.attribute(0).name());
            assertEquals("f2_nfa", acfg.attribute(1).name());
            assertEquals("f4", acfg.attribute(2).name());
            assertEquals("f4_nfa", acfg.attribute(3).name());

        }
    }

    private void assertDocumentDBConfigAvailableForStreaming(String mode) {
        List<String> sds = List.of("type");
        var tester = new SchemaTester();
        var model = tester.createModelWithMode(mode, sds);

        DocumentdbInfoConfig dcfg = model.getConfig(DocumentdbInfoConfig.class, "test/search/cluster.test.type");
        assertEquals(1, dcfg.documentdb().size());
        DocumentdbInfoConfig.Documentdb db = dcfg.documentdb(0);
        assertEquals("type", db.name());
    }

    @Test
    void requireThatDocumentDBConfigIsAvailableForStreaming() {
        assertDocumentDBConfigAvailableForStreaming("streaming");
    }

    private void assertAttributesConfigIndependentOfMode(String mode, List<String> sds,
                                                         List<String> documentDBConfigIds,
                                                         Map<String, List<String>> expectedAttributesMap)
    {
        assertAttributesConfigIndependentOfMode(mode, sds, documentDBConfigIds, expectedAttributesMap, new DeployState.Builder(), 123456);
    }

    private void assertAttributesConfigIndependentOfMode(String mode, List<String> sds,
                                                         List<String> documentDBConfigIds,
                                                         Map<String, List<String>> expectedAttributesMap,
                                                         DeployState.Builder builder,
                                                         long expectedMaxUnCommittedMemory) {
        var tester = new SchemaTester();
        var model = tester.createModelWithMode(mode, sds, builder);
        ContentSearchCluster contentSearchCluster = model.getContentClusters().get("test").getSearch();

        ProtonConfig proton = tester.getProtonConfig(contentSearchCluster);
        assertEquals(sds.size(), proton.documentdb().size());
        for (int i = 0; i < sds.size(); i++) {
            assertEquals(sds.get(i), proton.documentdb(i).inputdoctypename());
            assertEquals(documentDBConfigIds.get(i), proton.documentdb(i).configid());
            List<String> expectedAttributes = expectedAttributesMap.get(sds.get(i));
            if (expectedAttributes != null) {
                AttributesConfig rac1 = model.getConfig(AttributesConfig.class, proton.documentdb(i).configid());
                assertEquals(expectedAttributes.size(), rac1.attribute().size());
                for (int j = 0; j < expectedAttributes.size(); j++) {
                    assertEquals(expectedAttributes.get(j), rac1.attribute(j).name());
                    assertEquals(expectedMaxUnCommittedMemory, rac1.attribute(j).maxuncommittedmemory());
                }
            }
        }
    }

    @Test
    void testThatAttributesMaxUnCommittedMemoryIsControlledByFeatureFlag() {
        assertAttributesConfigIndependentOfMode("index", List.of("type1"),
                List.of("test/search/cluster.test/type1"),
                ImmutableMap.of("type1", List.of("f2", "f2_nfa")),
                new DeployState.Builder().properties(new TestProperties().maxUnCommittedMemory(193452)), 193452);
    }

    @Test
    void testThatAttributesConfigIsProducedForIndexed() {
        assertAttributesConfigIndependentOfMode("index", List.of("type1"),
                List.of("test/search/cluster.test/type1"),
                ImmutableMap.of("type1", List.of("f2", "f2_nfa")));
    }

    @Test
    void testThatAttributesConfigIsProducedForStreamingForFastAccessFields() {
        assertAttributesConfigIndependentOfMode("streaming", List.of("type1"),
                List.of("test/search/type1"),
                ImmutableMap.of("type1", List.of("f2")));
    }

    @Test
    void testThatAttributesConfigIsNotProducedForStoreOnlyEvenForFastAccessFields() {
        assertAttributesConfigIndependentOfMode("store-only", List.of("type1"),
                List.of("test/search"), Collections.emptyMap());
    }

}
