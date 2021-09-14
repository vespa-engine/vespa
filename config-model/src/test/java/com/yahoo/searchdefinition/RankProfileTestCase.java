// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.collections.Pair;
import com.yahoo.component.ComponentId;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.application.provider.MockFileRegistry;
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

import static com.yahoo.config.model.test.TestUtil.joinLines;

import org.junit.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests rank profiles
 *
 * @author bratseth
 */
public class RankProfileTestCase extends SchemaTestCase {

    @Test
    public void testRankProfileInheritance() {
        Search search = new Search("test");
        RankProfileRegistry rankProfileRegistry = RankProfileRegistry.createRankProfileRegistryWithBuiltinRankProfiles(search);
        SDDocumentType document = new SDDocumentType("test");
        SDField a = document.addField("a", DataType.STRING);
        a.setRankType(RankType.IDENTITY);
        document.addField("b", DataType.STRING);
        search.addDocument(document);
        RankProfile child = new RankProfile("child", search, rankProfileRegistry, search.rankingConstants());
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

    @Test
    public void requireThatIllegalInheritanceIsChecked() throws ParseException {
        try {
            RankProfileRegistry registry = new RankProfileRegistry();
            SearchBuilder builder = new SearchBuilder(registry, setupQueryProfileTypes());
            builder.importString(joinLines(
                    "search test {",
                    "  document test { } ",
                    "  rank-profile p1 inherits notexist {}",
                    "}"));
            builder.build(true);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("rank-profile 'p1' inherits 'notexist', but it does not exist anywhere in the inheritance of search 'test'.", e.getMessage());
        }
    }

    @Test
    public void requireThatSelfInheritanceIsIllegal() throws ParseException {
        try {
            RankProfileRegistry registry = new RankProfileRegistry();
            SearchBuilder builder = new SearchBuilder(registry, setupQueryProfileTypes());
            builder.importString(joinLines(
                    "schema test {",
                    "  document test { } ",
                    "  rank-profile self inherits self {}",
                    "}"));
            builder.build(true);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("There is a cycle in the inheritance for rank-profile 'test.self' = [test.self, test.self]", e.getMessage());
        }
    }

    @Test
    public void requireThatSelfInheritanceIsLegalWhenOverloading() throws ParseException {
        RankProfileRegistry registry = new RankProfileRegistry();
        SearchBuilder builder = new SearchBuilder(registry, setupQueryProfileTypes());
        builder.importString(joinLines(
                "schema base {",
                "  document base { } ",
                "  rank-profile self inherits default {}",
                "}"));
        builder.importString(joinLines(
                "schema test {",
                "  document test inherits base { } ",
                "  rank-profile self inherits self {}",
                "}"));
        builder.build(true);
    }

    @Test
    public void requireThatSidewaysInheritanceIsImpossible() throws ParseException {
        verifySidewaysInheritance(false);
        verifySidewaysInheritance(true);
    }
    private void verifySidewaysInheritance(boolean enforce) throws ParseException {
        RankProfileRegistry registry = new RankProfileRegistry();
        SearchBuilder builder = new SearchBuilder(registry, setupQueryProfileTypes(),
                                                  new TestProperties().enforceRankProfileInheritance(enforce));
        builder.importString(joinLines(
                "schema child1 {",
                "  document child1 {",
                "    field field1 type int {",
                "      indexing: attribute",
                "    }",
                "  }",
                "  rank-profile child inherits parent {",
                "    function function2() {",
                "      expression: attribute(field1) + 5",
                "    }",
                "    first-phase {",
                "      expression: function2() * function1()",
                "    }",
                "    summary-features {",
                "      function1",
                "      function2",
                "      attribute(field1)",
                "    }",
                "  }",
                "}\n"));
        builder.importString(joinLines(
                "schema child2 {",
                "  document child2 {",
                "    field field1 type int {",
                "      indexing: attribute",
                "    }",
                "  }",
                "  rank-profile parent {",
                "    first-phase {",
                "      expression: function1()",
                "    }",
                "    function function1() {",
                "      expression: attribute(field1) + 7",
                "    }",
                "    summary-features {",
                "      function1",
                "      attribute(field1)",
                "    }",
                "  }",
                "}"));
        try {
            builder.build(true);
            if (enforce) {
                fail("Sideways inheritance should have been enforced");
            } else {
                assertNotNull(builder.getSearch("child2"));
                assertNotNull(builder.getSearch("child1"));
                assertTrue(registry.get("child1", "child").inherits("parent"));
            }
        } catch (IllegalArgumentException e) {
            if (!enforce) fail("Sideways inheritance should have been allowed");
            assertEquals("rank-profile 'child' inherits 'parent', but it does not exist anywhere in the inheritance of search 'child1'.", e.getMessage());
        }
    }

    @Test
    public void requireThatDefaultCanAlwaysBeInherited() throws ParseException {
        RankProfileRegistry registry = new RankProfileRegistry();
        SearchBuilder builder = new SearchBuilder(registry, setupQueryProfileTypes());
        builder.importString(joinLines(
                "schema test {",
                "  document test { } ",
                "  rank-profile default inherits default {}",
                "}"));
        builder.build(true);
    }

    @Test
    public void requireThatCyclicInheritanceIsIllegal() throws ParseException {
        try {
            RankProfileRegistry registry = new RankProfileRegistry();
            SearchBuilder builder = new SearchBuilder(registry, setupQueryProfileTypes());
            builder.importString(joinLines(
                    "search test {",
                    "  document test { } ",
                    "  rank-profile a inherits b {}",
                    "  rank-profile b inherits c {}",
                    "  rank-profile c inherits a {}",
                    "}"));
            builder.build(true);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("There is a cycle in the inheritance for rank-profile 'test.c' = [test.c, test.a, test.b, test.c]", e.getMessage());
        }
    }

    @Test
    public void requireThatRankProfilesCanInheritNotYetSeenProfiles() throws ParseException
    {
        RankProfileRegistry registry = new RankProfileRegistry();
        SearchBuilder builder = new SearchBuilder(registry, setupQueryProfileTypes());
        builder.importString(joinLines(
                "search test {",
                "  document test { } ",
                "  rank-profile p1 inherits not_yet_defined {}",
                "  rank-profile not_yet_defined {}",
                "}"));
        builder.build(true);
        assertNotNull(registry.get("test","p1"));
        assertTrue(registry.get("test","p1").inherits("not_yet_defined"));
        assertNotNull(registry.get("test","not_yet_defined"));
    }

    private String createSD(Double termwiseLimit) {
        return joinLines(
                "search test {",
                "    document test { ",
                "        field a type string { ",
                "            indexing: index ",
                "        }",
                "    }",
                "    ",
                "    rank-profile parent {",
                (termwiseLimit != null ? ("        termwise-limit:" + termwiseLimit + "\n") : ""),
                "        num-threads-per-search:8",
                "        min-hits-per-thread:70",
                "        num-search-partitions:1200",
                "    }",
                "    rank-profile child inherits parent { }",
                "}");
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
        RawRankProfile rawRankProfile = new RawRankProfile(rankProfile, new LargeRankExpressions(new MockFileRegistry()), new QueryProfileRegistry(),
                                                           new ImportedMlModels(), attributeFields, deployProperties);
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
        builder.importString(joinLines(
                "search test {",
                "  document test { ",
                "    field a type tensor(x[10]) { indexing: attribute }",
                "    field b type tensor(y{}) { indexing: attribute }",
                "    field c type tensor(x[5]) { indexing: attribute }",
                "  }",
                "  rank-profile p1 {}",
                "  rank-profile p2 {}",
                "}"));
        builder.build();
        Search search = builder.getSearch();

        assertEquals(4, registry.all().size());
        assertAttributeTypeSettings(registry.get(search, "default"), search);
        assertAttributeTypeSettings(registry.get(search, "unranked"), search);
        assertAttributeTypeSettings(registry.get(search, "p1"), search);
        assertAttributeTypeSettings(registry.get(search, "p2"), search);
    }

    @Test
    public void requireThatDenseDimensionsMustBeBound() throws ParseException {
        try {
            SearchBuilder builder = new SearchBuilder(new RankProfileRegistry());
            builder.importString(joinLines(
                    "search test {",
                    "  document test { ",
                    "    field a type tensor(x[]) { indexing: attribute }",
                    "  }",
                    "}"));
            builder.build();
        }
        catch (IllegalArgumentException e) {
            assertEquals("Illegal type in field a type tensor(x[]): Dense tensor dimensions must have a size",
                         e.getMessage());
        }
    }

    private static RawRankProfile createRawRankProfile(RankProfile profile, Search search) {
        return new RawRankProfile(profile, new LargeRankExpressions(new MockFileRegistry()), new QueryProfileRegistry(), new ImportedMlModels(), new AttributeFields(search), new TestProperties());
    }

    private static void assertAttributeTypeSettings(RankProfile profile, Search search) {
        RawRankProfile rawProfile = createRawRankProfile(profile, search);
        assertEquals("tensor(x[10])", findProperty(rawProfile.configProperties(), "vespa.type.attribute.a").get());
        assertEquals("tensor(y{})", findProperty(rawProfile.configProperties(), "vespa.type.attribute.b").get());
        assertEquals("tensor(x[5])", findProperty(rawProfile.configProperties(), "vespa.type.attribute.c").get());
    }

    @Test
    public void requireThatConfigIsDerivedForQueryFeatureTypeSettings() throws ParseException {
        RankProfileRegistry registry = new RankProfileRegistry();
        SearchBuilder builder = new SearchBuilder(registry, setupQueryProfileTypes());
        builder.importString(joinLines(
                "search test {",
                "  document test { } ",
                "  rank-profile p1 {}",
                "  rank-profile p2 {}",
                "}"));
        builder.build(true);
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
        RawRankProfile rawProfile =createRawRankProfile(profile, search);
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
