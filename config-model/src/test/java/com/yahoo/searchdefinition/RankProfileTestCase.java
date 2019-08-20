// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.collections.Pair;
import com.yahoo.component.ComponentId;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.document.DataType;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.FieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.query.profile.types.QueryProfileTypeRegistry;
import com.yahoo.searchdefinition.derived.AttributeFields;
import com.yahoo.searchdefinition.derived.RawRankProfile;
import com.yahoo.searchdefinition.document.RankType;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.parser.ParseException;
import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModels;
import org.junit.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests rank profiles
 *
 * @author bratseth
 */
public class RankProfileTestCase extends SearchDefinitionTestCase {

    @Test
    public void testRankProfileInheritance() {
        Search search = new Search("test", null);
        RankProfileRegistry rankProfileRegistry = RankProfileRegistry.createRankProfileRegistryWithBuiltinRankProfiles(search);
        SDDocumentType document = new SDDocumentType("test");
        SDField a = document.addField("a", DataType.STRING);
        a.setRankType(RankType.IDENTITY);
        document.addField("b", DataType.STRING);
        search.addDocument(document);
        RankProfile child = new RankProfile("child", search, rankProfileRegistry);
        child.setInherited("default");
        rankProfileRegistry.add(child);

        Iterator<RankProfile.RankSetting> i = child.rankSettingIterator();

        RankProfile.RankSetting setting = i.next();
        assertEquals(RankType.IDENTITY, setting.getValue());
        assertEquals("a", setting.getFieldName());
        assertEquals(RankProfile.RankSetting.Type.RANKTYPE, setting.getType());

        setting = i.next();
        assertEquals(RankType.DEFAULT, setting.getValue());
    }

    private String createSD(Double termwiseLimit) {
        return "search test {\n" +
                "    document test { \n" +
                "        field a type string { \n" +
                "            indexing: index \n" +
                "        }\n" +
                "    }\n" +
                "    \n" +
                "    rank-profile parent {\n" +
                (termwiseLimit != null ? ("        termwise-limit:" + termwiseLimit + "\n") : "") +
                "        num-threads-per-search:8\n" +
                "        min-hits-per-thread:70\n" +
                "        num-search-partitions:1200\n" +
                "    }\n" +
                "    rank-profile child inherits parent { }\n" +
                "}\n";
    }

    @Test
    public void testTermwiseLimitWithDeployOverride() throws ParseException {
        verifyTermwiseLimitAndSomeMoreIncludingInheritance(new TestProperties(), createSD(null), null);
        verifyTermwiseLimitAndSomeMoreIncludingInheritance(new TestProperties(), createSD(0.78), 0.78);
        verifyTermwiseLimitAndSomeMoreIncludingInheritance(new TestProperties().setDefaultTermwiseLimit(0.09), createSD(null), 0.09);
        verifyTermwiseLimitAndSomeMoreIncludingInheritance(new TestProperties().setDefaultTermwiseLimit(0.09), createSD(0.37), 0.37);
    }

    private void verifyTermwiseLimitAndSomeMoreIncludingInheritance(ModelContext.Properties deployProperties, String sd, Double termwiseLimit) throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        SearchBuilder builder = new SearchBuilder(rankProfileRegistry);
        builder.importString(sd);
        builder.build();
        Search search = builder.getSearch();
        AttributeFields attributeFields = new AttributeFields(search);
        verifyRankProfile(rankProfileRegistry.get(search, "parent"), attributeFields, deployProperties, termwiseLimit);
        verifyRankProfile(rankProfileRegistry.get(search, "child"), attributeFields, deployProperties, termwiseLimit);
    }

    private void verifyRankProfile(RankProfile rankProfile, AttributeFields attributeFields, ModelContext.Properties deployProperties,
                                   Double expectedTermwiseLimit) {
        assertEquals(8, rankProfile.getNumThreadsPerSearch());
        assertEquals(70, rankProfile.getMinHitsPerThread());
        assertEquals(1200, rankProfile.getNumSearchPartitions());
        RawRankProfile rawRankProfile = new RawRankProfile(rankProfile, new QueryProfileRegistry(), new ImportedMlModels(), attributeFields, deployProperties);
        if (expectedTermwiseLimit != null) {
            assertTrue(findProperty(rawRankProfile.configProperties(), "vespa.matching.termwise_limit").isPresent());
            assertEquals(String.valueOf(expectedTermwiseLimit), findProperty(rawRankProfile.configProperties(), "vespa.matching.termwise_limit").get());
        } else {
            assertFalse(findProperty(rawRankProfile.configProperties(), "vespa.matching.termwise_limit").isPresent());
        }
        assertTrue(findProperty(rawRankProfile.configProperties(), "vespa.matching.numthreadspersearch").isPresent());
        assertEquals("8", findProperty(rawRankProfile.configProperties(), "vespa.matching.numthreadspersearch").get());
        assertTrue(findProperty(rawRankProfile.configProperties(), "vespa.matching.minhitsperthread").isPresent());
        assertEquals("70", findProperty(rawRankProfile.configProperties(), "vespa.matching.minhitsperthread").get());
        assertTrue(findProperty(rawRankProfile.configProperties(), "vespa.matching.numsearchpartitions").isPresent());
        assertEquals("1200", findProperty(rawRankProfile.configProperties(), "vespa.matching.numsearchpartitions").get());
    }

    @Test
    public void requireThatConfigIsDerivedForAttributeTypeSettings() throws ParseException {
        RankProfileRegistry registry = new RankProfileRegistry();
        SearchBuilder builder = new SearchBuilder(registry);
        builder.importString("search test {\n" +
                "  document test { \n" +
                "    field a type tensor(x[10]) { indexing: attribute }\n" +
                "    field b type tensor(y{}) { indexing: attribute }\n" +
                "    field c type tensor(x[]) { indexing: attribute }\n" +
                "  }\n" +
                "  rank-profile p1 {}\n" +
                "  rank-profile p2 {}\n" +
                "}");
        builder.build();
        Search search = builder.getSearch();

        assertEquals(4, registry.all().size());
        assertAttributeTypeSettings(registry.get(search, "default"), search);
        assertAttributeTypeSettings(registry.get(search, "unranked"), search);
        assertAttributeTypeSettings(registry.get(search, "p1"), search);
        assertAttributeTypeSettings(registry.get(search, "p2"), search);
    }

    private static void assertAttributeTypeSettings(RankProfile profile, Search search) {
        RawRankProfile rawProfile = new RawRankProfile(profile, new QueryProfileRegistry(), new ImportedMlModels(), new AttributeFields(search));
        assertEquals("tensor(x[10])", findProperty(rawProfile.configProperties(), "vespa.type.attribute.a").get());
        assertEquals("tensor(y{})", findProperty(rawProfile.configProperties(), "vespa.type.attribute.b").get());
        assertEquals("tensor(x[])", findProperty(rawProfile.configProperties(), "vespa.type.attribute.c").get());
    }

    @Test
    public void requireThatConfigIsDerivedForQueryFeatureTypeSettings() throws ParseException {
        RankProfileRegistry registry = new RankProfileRegistry();
        SearchBuilder builder = new SearchBuilder(registry, setupQueryProfileTypes());
        builder.importString("search test {\n" +
                "  document test { } \n" +
                "  rank-profile p1 {}\n" +
                "  rank-profile p2 {}\n" +
                "}");
        builder.build(true, new BaseDeployLogger());
        Search search = builder.getSearch();

        assertEquals(4, registry.all().size());
        assertQueryFeatureTypeSettings(registry.get(search, "default"), search);
        assertQueryFeatureTypeSettings(registry.get(search, "unranked"), search);
        assertQueryFeatureTypeSettings(registry.get(search, "p1"), search);
        assertQueryFeatureTypeSettings(registry.get(search, "p2"), search);
    }

    private static QueryProfileRegistry setupQueryProfileTypes() {
        QueryProfileRegistry registry = new QueryProfileRegistry();
        QueryProfileTypeRegistry typeRegistry = registry.getTypeRegistry();
        QueryProfileType type = new QueryProfileType(new ComponentId("testtype"));
        type.addField(new FieldDescription("ranking.features.query(tensor1)",
                FieldType.fromString("tensor(x[10])", typeRegistry)), typeRegistry);
        type.addField(new FieldDescription("ranking.features.query(tensor2)",
                FieldType.fromString("tensor(y{})", typeRegistry)), typeRegistry);
        type.addField(new FieldDescription("ranking.features.invalid(tensor3)",
                FieldType.fromString("tensor(x{})", typeRegistry)), typeRegistry);
        type.addField(new FieldDescription("ranking.features.query(numeric)",
                FieldType.fromString("integer", typeRegistry)), typeRegistry);
        typeRegistry.register(type);
        return registry;
    }

    private static void assertQueryFeatureTypeSettings(RankProfile profile, Search search) {
        RawRankProfile rawProfile = new RawRankProfile(profile, new QueryProfileRegistry(), new ImportedMlModels(), new AttributeFields(search));
        assertEquals("tensor(x[10])", findProperty(rawProfile.configProperties(), "vespa.type.query.tensor1").get());
        assertEquals("tensor(y{})", findProperty(rawProfile.configProperties(), "vespa.type.query.tensor2").get());
        assertFalse(findProperty(rawProfile.configProperties(), "vespa.type.query.tensor3").isPresent());
        assertFalse(findProperty(rawProfile.configProperties(), "vespa.type.query.numeric").isPresent());
    }

    private static Optional<String> findProperty(List<Pair<String, String>> properties, String key) {
        for (Pair<String, String> property : properties)
            if (property.getFirst().equals(key))
                return Optional.of(property.getSecond());
        return Optional.empty();
    }

}
