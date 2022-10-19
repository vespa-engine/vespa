// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.collections.Pair;
import com.yahoo.component.ComponentId;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.document.DataType;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.FieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.query.profile.types.QueryProfileTypeRegistry;
import com.yahoo.schema.derived.AttributeFields;
import com.yahoo.schema.derived.RawRankProfile;
import com.yahoo.schema.document.RankType;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.parser.ParseException;
import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModels;

import static com.yahoo.config.model.test.TestUtil.joinLines;

import com.yahoo.searchlib.rankingexpression.Reference;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests rank profiles
 *
 * @author bratseth
 */
public class RankProfileTestCase extends AbstractSchemaTestCase {

    @Test
    void testRankProfileInheritance() {
        Schema schema = new Schema("test", MockApplicationPackage.createEmpty());
        RankProfileRegistry rankProfileRegistry = RankProfileRegistry.createRankProfileRegistryWithBuiltinRankProfiles(schema);
        SDDocumentType document = new SDDocumentType("test");
        SDField a = document.addField("a", DataType.STRING);
        a.setRankType(RankType.IDENTITY);
        document.addField("b", DataType.STRING);
        schema.addDocument(document);
        RankProfile child = new RankProfile("child", schema, rankProfileRegistry);
        child.inherit("default");
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
    void requireThatIllegalInheritanceIsChecked() throws ParseException {
        try {
            RankProfileRegistry registry = new RankProfileRegistry();
            ApplicationBuilder builder = new ApplicationBuilder(registry, setupQueryProfileTypes());
            builder.addSchema(joinLines(
                    "search test {",
                    "  document test { } ",
                    "  rank-profile p1 inherits notexist {}",
                    "}"));
            builder.build(true);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("rank-profile 'p1' inherits 'notexist', but this is not found in schema 'test'", e.getMessage());
        }
    }

    @Test
    void requireThatSelfInheritanceIsIllegal() throws ParseException {
        try {
            RankProfileRegistry registry = new RankProfileRegistry();
            ApplicationBuilder builder = new ApplicationBuilder(registry, setupQueryProfileTypes());
            builder.addSchema(joinLines(
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
    void requireThatSelfInheritanceIsLegalWhenOverloading() throws ParseException {
        RankProfileRegistry registry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(registry, setupQueryProfileTypes());
        builder.addSchema(joinLines(
                "schema base {",
                "  document base { } ",
                "  rank-profile self inherits default {}",
                "}"));
        builder.addSchema(joinLines(
                "schema test {",
                "  document test inherits base { } ",
                "  rank-profile self inherits self {}",
                "}"));
        builder.build(true);
    }

    @Test
    void requireThatSidewaysInheritanceIsImpossible() throws ParseException {
        RankProfileRegistry registry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(registry, setupQueryProfileTypes());
        builder.addSchema(joinLines(
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
        builder.addSchema(joinLines(
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
            fail("Sideways inheritance should have been enforced");
        } catch (IllegalArgumentException e) {
            assertEquals("rank-profile 'child' inherits 'parent', but this is not found in schema 'child1'", e.getMessage());
        }
    }

    @Test
    void inputsAreInheritedAndValuesAreOverridable() throws ParseException {
        RankProfileRegistry registry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(registry, new QueryProfileRegistry());
        builder.addSchema(joinLines(
                "schema test {",
                "  document test { } ",
                "  rank-profile parent1 {",
                "    inputs {",
                "      input1 double: 1",
                "      input2 double: 2",
                "    }",
                "  }",
                "  rank-profile parent2 {",
                "    inputs {",
                "      input4 double: 4",
                "    }",
                "  }",
                "  rank-profile child inherits parent1, parent2 {",
                "    inputs {",
                "      input2 double: 2.5",
                "      input3 double: 3",
                "    }" +
                "  }",
                "}"));
        var application = builder.build(true);
        RankProfile child = registry.get(application.schemas().get("test"), "child");
        assertEquals(4, child.inputs().size());
        assertEquals(Set.of(Reference.simple("query", "input1"),
                            Reference.simple("query", "input2"),
                            Reference.simple("query", "input3"),
                            Reference.simple("query", "input4")),
                            child.inputs().keySet());
        var input2 = child.inputs().get(Reference.simple("query", "input2"));
        assertEquals(2.5, input2.defaultValue().get().asDouble(), 0.000000001);
    }

    @Test
    void inputConflictsAreDetected() throws ParseException {
        try {
            RankProfileRegistry registry = new RankProfileRegistry();
            ApplicationBuilder builder = new ApplicationBuilder(registry, new QueryProfileRegistry());
            builder.addSchema(joinLines(
                    "schema test {",
                    "  document test { } ",
                    "  rank-profile parent1 {",
                    "    inputs {",
                    "      input1 double: 1",
                    "    }",
                    "  }",
                    "  rank-profile parent2 {",
                    "    inputs {",
                    "      input1 tensor(x[100])",
                    "    }",
                    "  }",
                    "  rank-profile child inherits parent1, parent2 {",
                    "  }",
                    "}"));
            builder.build(true);
            fail("Should have failed");
        }
        catch (IllegalArgumentException e) {
            assertEquals("rank profile 'child' inherits rank profile 'parent2' which contains input query(input1) tensor(x[100])" +
                         ", but this is already defined as input query(input1) tensor():{1.0} in another profile this inherits",
                         e.getCause().getMessage());
        }
    }

    @Test
    void requireThatDefaultInheritingDefaultIsIgnored() throws ParseException {
        RankProfileRegistry registry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(registry, setupQueryProfileTypes());
        builder.addSchema(joinLines(
                "schema test {",
                "  document test { } ",
                "  rank-profile default inherits default {}",
                "}"));
        builder.build(true);
    }

    @Test
    void requireThatCyclicInheritanceIsIllegal() throws ParseException {
        try {
            RankProfileRegistry registry = new RankProfileRegistry();
            ApplicationBuilder builder = new ApplicationBuilder(registry, setupQueryProfileTypes());
            builder.addSchema(joinLines(
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
    void requireThatRankProfilesCanInheritNotYetSeenProfiles() throws ParseException
    {
        RankProfileRegistry registry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(registry, setupQueryProfileTypes());
        builder.addSchema(joinLines(
                "search test {",
                "  document test { } ",
                "  rank-profile p1 inherits not_yet_defined {}",
                "  rank-profile not_yet_defined {}",
                "}"));
        builder.build(true);
        assertNotNull(registry.get("test", "p1"));
        assertTrue(registry.get("test", "p1").inherits("not_yet_defined"));
        assertNotNull(registry.get("test", "not_yet_defined"));
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
    void testTermwiseLimitWithDeployOverride() throws ParseException {
        verifyTermwiseLimitAndSomeMoreIncludingInheritance(new TestProperties(), createSD(null), null);
        verifyTermwiseLimitAndSomeMoreIncludingInheritance(new TestProperties(), createSD(0.78), 0.78);
        verifyTermwiseLimitAndSomeMoreIncludingInheritance(new TestProperties().setDefaultTermwiseLimit(0.09), createSD(null), 0.09);
        verifyTermwiseLimitAndSomeMoreIncludingInheritance(new TestProperties().setDefaultTermwiseLimit(0.09), createSD(0.37), 0.37);
    }

    private void verifyTermwiseLimitAndSomeMoreIncludingInheritance(ModelContext.Properties deployProperties, String sd, Double termwiseLimit) throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(rankProfileRegistry);
        builder.addSchema(sd);
        builder.build(true);
        Schema schema = builder.getSchema();
        AttributeFields attributeFields = new AttributeFields(schema);
        verifyRankProfile(rankProfileRegistry.get(schema, "parent"), attributeFields, deployProperties, termwiseLimit);
        verifyRankProfile(rankProfileRegistry.get(schema, "child"), attributeFields, deployProperties, termwiseLimit);
    }

    private void verifyRankProfile(RankProfile rankProfile, AttributeFields attributeFields, ModelContext.Properties deployProperties,
                                   Double expectedTermwiseLimit) {
        assertEquals(8, rankProfile.getNumThreadsPerSearch());
        assertEquals(70, rankProfile.getMinHitsPerThread());
        assertEquals(1200, rankProfile.getNumSearchPartitions());
        RawRankProfile rawRankProfile = new RawRankProfile(rankProfile, new LargeRankingExpressions(new MockFileRegistry()), new QueryProfileRegistry(),
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
    void requireThatConfigIsDerivedForAttributeTypeSettings() throws ParseException {
        RankProfileRegistry registry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(registry);
        builder.addSchema(joinLines(
                "search test {",
                "  document test { ",
                "    field a type tensor(x[10]) { indexing: attribute }",
                "    field b type tensor(y{}) { indexing: attribute }",
                "    field c type tensor(x[5]) { indexing: attribute }",
                "  }",
                "  rank-profile p1 {}",
                "  rank-profile p2 {}",
                "}"));
        builder.build(true);
        Schema schema = builder.getSchema();

        assertEquals(4, registry.all().size());
        assertAttributeTypeSettings(registry.get(schema, "default"), schema);
        assertAttributeTypeSettings(registry.get(schema, "unranked"), schema);
        assertAttributeTypeSettings(registry.get(schema, "p1"), schema);
        assertAttributeTypeSettings(registry.get(schema, "p2"), schema);
    }

    @Test
    void requireThatDenseDimensionsMustBeBound() throws ParseException {
        try {
            ApplicationBuilder builder = new ApplicationBuilder(new RankProfileRegistry());
            builder.addSchema(joinLines(
                    "search test {",
                    "  document test { ",
                    "    field a type tensor(x[]) { indexing: attribute }",
                    "  }",
                    "}"));
            builder.build(true);
        }
        catch (IllegalArgumentException e) {
            assertEquals("Illegal type in field a type tensor(x[]): Dense tensor dimensions must have a size",
                    e.getMessage());
        }
    }

    private static RawRankProfile createRawRankProfile(RankProfile profile, Schema schema) {
        return new RawRankProfile(profile, new LargeRankingExpressions(new MockFileRegistry()), new QueryProfileRegistry(), new ImportedMlModels(), new AttributeFields(schema), new TestProperties());
    }

    private static void assertAttributeTypeSettings(RankProfile profile, Schema schema) {
        RawRankProfile rawProfile = createRawRankProfile(profile, schema);
        assertEquals("tensor(x[10])", findProperty(rawProfile.configProperties(), "vespa.type.attribute.a").get());
        assertEquals("tensor(y{})", findProperty(rawProfile.configProperties(), "vespa.type.attribute.b").get());
        assertEquals("tensor(x[5])", findProperty(rawProfile.configProperties(), "vespa.type.attribute.c").get());
    }

    @Test
    void requireThatConfigIsDerivedForQueryFeatureTypeSettings() throws ParseException {
        RankProfileRegistry registry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(registry, setupQueryProfileTypes());
        builder.addSchema(joinLines(
                "search test {",
                "  document test { } ",
                "  rank-profile p1 {}",
                "  rank-profile p2 {}",
                "}"));
        builder.build(true);
        Schema schema = builder.getSchema();

        assertEquals(4, registry.all().size());
        assertQueryFeatureTypeSettings(registry.get(schema, "default"), schema);
        assertQueryFeatureTypeSettings(registry.get(schema, "unranked"), schema);
        assertQueryFeatureTypeSettings(registry.get(schema, "p1"), schema);
        assertQueryFeatureTypeSettings(registry.get(schema, "p2"), schema);
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
        var profile = new QueryProfile(new ComponentId("testprofile"));
        profile.setType(type);
        registry.register(profile);
        return registry;
    }

    private static void assertQueryFeatureTypeSettings(RankProfile profile, Schema schema) {
        RawRankProfile rawProfile =createRawRankProfile(profile, schema);
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

    @Test
    void approximate_nearest_neighbor_threshold_settings_are_configurable() throws ParseException {
        verifyApproximateNearestNeighborThresholdSettings(0.7, null);
        verifyApproximateNearestNeighborThresholdSettings(null, 0.3);
        verifyApproximateNearestNeighborThresholdSettings(0.7, 0.3);
    }

    private void verifyApproximateNearestNeighborThresholdSettings(Double postFilterThreshold, Double approximateThreshold) throws ParseException {
        var rankProfileRegistry = new RankProfileRegistry();
        var props = new TestProperties();
        var queryProfileRegistry = new QueryProfileRegistry();
        var builder = new ApplicationBuilder(rankProfileRegistry, queryProfileRegistry, props);
        builder.addSchema(createSDWithRankProfileThresholds(postFilterThreshold, approximateThreshold));
        builder.build(true);

        var schema = builder.getSchema();
        var rankProfile = rankProfileRegistry.get(schema, "my_profile");
        var rawRankProfile = new RawRankProfile(rankProfile, new LargeRankingExpressions(new MockFileRegistry()), queryProfileRegistry,
                                                new ImportedMlModels(), new AttributeFields(schema), props);

        if (postFilterThreshold != null) {
            assertEquals((double)postFilterThreshold, rankProfile.getPostFilterThreshold().getAsDouble(), 0.000001);
            assertEquals(String.valueOf(postFilterThreshold), findProperty(rawRankProfile.configProperties(), "vespa.matching.global_filter.upper_limit").get());
        } else {
            assertTrue(rankProfile.getPostFilterThreshold().isEmpty());
            assertFalse(findProperty(rawRankProfile.configProperties(), "vespa.matching.global_filter.upper_limit").isPresent());
        }

        if (approximateThreshold != null) {
            assertEquals((double)approximateThreshold, rankProfile.getApproximateThreshold().getAsDouble(), 0.000001);
            assertEquals(String.valueOf(approximateThreshold), findProperty(rawRankProfile.configProperties(), "vespa.matching.global_filter.lower_limit").get());
        } else {
            assertTrue(rankProfile.getApproximateThreshold().isEmpty());
            assertFalse(findProperty(rawRankProfile.configProperties(), "vespa.matching.global_filter.lower_limit").isPresent());
        }
    }

    private String createSDWithRankProfileThresholds(Double postFilterThreshold, Double approximateThreshold) {
        return joinLines(
                "search test {",
                "    document test {}",
                "    rank-profile my_profile {",
                (postFilterThreshold != null ? ("        post-filter-threshold: " + postFilterThreshold) : ""),
                (approximateThreshold != null ? ("        approximate-threshold: " + approximateThreshold) : ""),
                "    }",
                "}");
    }

}
