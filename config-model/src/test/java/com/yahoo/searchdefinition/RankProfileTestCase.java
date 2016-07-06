// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.component.ComponentId;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
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
import com.yahoo.vespa.model.container.search.QueryProfiles;
import org.junit.Test;

import java.util.Iterator;

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
        rankProfileRegistry.addRankProfile(child);

        Iterator<RankProfile.RankSetting> i = child.rankSettingIterator();

        RankProfile.RankSetting setting = i.next();
        assertEquals(RankType.IDENTITY, setting.getValue());
        assertEquals("a", setting.getFieldName());
        assertEquals(RankProfile.RankSetting.Type.RANKTYPE, setting.getType());

        setting = i.next();
        assertEquals(RankType.DEFAULT, setting.getValue());
    }

    @Test
    public void testTermwiseLimitAndSomeMore() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        SearchBuilder builder = new SearchBuilder(rankProfileRegistry);
        builder.importString(
                "search test {\n" +
                        "    document test { \n" +
                        "        field a type string { \n" +
                        "            indexing: index \n" +
                        "        }\n" +
                        "    }\n" +
                        "    \n" +
                        "    rank-profile parent {\n" +
                        "        termwise-limit:0.78\n" +
                        "        num-threads-per-search:8\n" +
                        "        min-hits-per-thread:70\n" +
                        "        num-search-partitions:1200\n" +
                        "    }\n" +
                        "\n" +
                        "}\n");
        builder.build();
        Search search = builder.getSearch();
        RankProfile rankProfile = rankProfileRegistry.getRankProfile(search, "parent");
        assertEquals(0.78, rankProfile.getTermwiseLimit(), 0.000001);
        assertEquals(8, rankProfile.getNumThreadsPerSearch());
        assertEquals(70, rankProfile.getMinHitsPerThread());
        assertEquals(1200, rankProfile.getNumSearchPartitions());
        AttributeFields attributeFields = new AttributeFields(search);
        RawRankProfile rawRankProfile = new RawRankProfile(rankProfile, attributeFields);
        assertTrue(rawRankProfile.configProperties().containsKey("vespa.matching.termwise_limit"));
        assertEquals("0.78", rawRankProfile.configProperties().get("vespa.matching.termwise_limit"));
        assertTrue(rawRankProfile.configProperties().containsKey("vespa.matching.numthreadspersearch"));
        assertEquals("8", rawRankProfile.configProperties().get("vespa.matching.numthreadspersearch"));
        assertTrue(rawRankProfile.configProperties().containsKey("vespa.matching.minhitsperthread"));
        assertEquals("70", rawRankProfile.configProperties().get("vespa.matching.minhitsperthread"));
        assertTrue(rawRankProfile.configProperties().containsKey("vespa.matching.numsearchpartitions"));
        assertEquals("1200", rawRankProfile.configProperties().get("vespa.matching.numsearchpartitions"));
    }

    @Test
    public void requireThatConfigIsDerivedForAttributeTypeSettings() throws ParseException {
        RankProfileRegistry registry = new RankProfileRegistry();
        SearchBuilder builder = new SearchBuilder(registry);
        builder.importString("search test {\n" +
                "  document test { \n" +
                "    field a type tensor { indexing: attribute \n attribute: tensor(x[10]) }\n" +
                "    field b type tensor { indexing: attribute \n attribute: tensor(y{}) }\n" +
                "    field c type tensor { indexing: attribute }\n" +
                "  }\n" +
                "  rank-profile p1 {}\n" +
                "  rank-profile p2 {}\n" +
                "}");
        builder.build();
        Search search = builder.getSearch();

        assertEquals(4, registry.allRankProfiles().size());
        assertAttributeTypeSettings(registry.getRankProfile(search, "default"), search);
        assertAttributeTypeSettings(registry.getRankProfile(search, "unranked"), search);
        assertAttributeTypeSettings(registry.getRankProfile(search, "p1"), search);
        assertAttributeTypeSettings(registry.getRankProfile(search, "p2"), search);
    }

    private static void assertAttributeTypeSettings(RankProfile profile, Search search) {
        RawRankProfile rawProfile = new RawRankProfile(profile, new AttributeFields(search));
        assertEquals("tensor(x[10])", rawProfile.configProperties().get("vespa.type.attribute.a"));
        assertEquals("tensor(y{})", rawProfile.configProperties().get("vespa.type.attribute.b"));
        assertFalse(rawProfile.configProperties().containsKey("vespa.type.attribute.c"));
    }

    @Test
    public void requireThatConfigIsDerivedForQueryFeatureTypeSettings() throws ParseException {
        RankProfileRegistry registry = new RankProfileRegistry();
        SearchBuilder builder = new SearchBuilder(registry);
        builder.importString("search test {\n" +
                "  document test { } \n" +
                "  rank-profile p1 {}\n" +
                "  rank-profile p2 {}\n" +
                "}");
        builder.build(new BaseDeployLogger(), setupQueryProfileTypes());
        Search search = builder.getSearch();

        assertEquals(4, registry.allRankProfiles().size());
        assertQueryFeatureTypeSettings(registry.getRankProfile(search, "default"), search);
        assertQueryFeatureTypeSettings(registry.getRankProfile(search, "unranked"), search);
        assertQueryFeatureTypeSettings(registry.getRankProfile(search, "p1"), search);
        assertQueryFeatureTypeSettings(registry.getRankProfile(search, "p2"), search);
    }

    private static QueryProfiles setupQueryProfileTypes() {
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
        return new QueryProfiles(registry);
    }

    private static void assertQueryFeatureTypeSettings(RankProfile profile, Search search) {
        RawRankProfile rawProfile = new RawRankProfile(profile, new AttributeFields(search));
        assertEquals("tensor(x[10])", rawProfile.configProperties().get("vespa.type.query.tensor1"));
        assertEquals("tensor(y{})", rawProfile.configProperties().get("vespa.type.query.tensor2"));
        assertFalse(rawProfile.configProperties().containsKey("vespa.type.query.tensor3"));
        assertFalse(rawProfile.configProperties().containsKey("vespa.type.query.numeric"));
    }

}
