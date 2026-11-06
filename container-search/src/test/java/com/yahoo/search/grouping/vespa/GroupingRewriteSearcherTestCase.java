// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

import com.yahoo.component.chain.Chain;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.search.Query;
import com.yahoo.search.Searcher;
import com.yahoo.search.config.ClusterConfig;
import com.yahoo.search.grouping.GroupingRequest;
import com.yahoo.search.grouping.GroupingValidator;
import com.yahoo.search.grouping.request.AttributeFunction;
import com.yahoo.search.grouping.request.AttributeValue;
import com.yahoo.search.grouping.request.FastSearchMapDecodeFunction;
import com.yahoo.search.grouping.request.GroupingOperation;
import com.yahoo.search.schema.Cluster;
import com.yahoo.search.schema.Field;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.vespa.config.search.AttributesConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author arnej27959
 */
public class GroupingRewriteSearcherTestCase {

    private static final String FAST_MAP_GROUPING = "all(group(fastsearchmap.decode(map.lookup)) each(output(count())))";

    @Test
    void fast_map_lookup_value_type_is_resolved() {
        for (String type : List.of("int", "long", "float", "double", "string")) {
            var schemaInfo = schemaInfo(schemaWithLookup("one", type));
            assertEquals(Field.Type.from(type), resolvedValueType(schemaInfo, createQuery(FAST_MAP_GROUPING)));
        }
        var query = createQuery("all(group(fastsearchmap.decode(map.lookup, \": \")) each(output(count())))");
        assertEquals(Field.Type.from("int"), resolvedValueType(schemaInfo(schemaWithLookup("one", "int")), query));
    }

    @Test
    void fast_map_lookup_value_type_is_resolved_whether_or_not_validation_is_enabled() {
        for (boolean validate : List.of(true, false)) {
            var query = createQuery(FAST_MAP_GROUPING);
            query.properties().set(GroupingValidator.PARAM_ENABLED, validate);
            runWithValidator(ClusterConfig.IndexMode.Enum.INDEX, schemaInfo(schemaWithLookup("one", "long")), query);
            assertEquals(Field.Type.from("long"), valueTypeOf(query));
        }
    }

    @Test
    void unavailable_fast_map_lookup_throws() {
        var schemaInfo = schemaInfo(schemaWithLookup("one", "int"));
        assertFastMapLookupRejected("Grouping request references fast map lookup 'map.other' which is not available in cluster 'myCluster'.",
                                    schemaInfo, createQuery("all(group(fastsearchmap.decode(map.other)) each(output(count())))"));
        // The lookup attribute is missing, as for an imported field
        var query = createQuery(FAST_MAP_GROUPING);
        var exception = assertThrows(IllegalArgumentException.class,
                                     () -> rewriteGrouping(ClusterConfig.IndexMode.Enum.INDEX,
                                                           new AttributesConfig(new AttributesConfig.Builder()),
                                                           schemaInfo, query));
        assertEquals("Grouping request references fast map lookup 'map.lookup' which is not available in cluster 'myCluster'.",
                     exception.getMessage());
        // No schema in the cluster has the lookup
        assertFastMapLookupRejected("Grouping request references fast map lookup 'map.lookup' which is not available in cluster 'myCluster'.",
                                    schemaInfo(new Schema.Builder("one").build()), createQuery(FAST_MAP_GROUPING));
    }

    @Test
    void fast_map_lookup_with_undecodable_value_type_throws() {
        assertFastMapLookupRejected("Grouping request references fast map lookup 'map.lookup', whose values of type BYTE cannot be decoded.",
                                    schemaInfo(schemaWithLookup("one", "byte")), createQuery(FAST_MAP_GROUPING));
    }

    @Test
    void fast_map_lookup_must_have_same_value_type_in_schemas_searched() {
        var schemaInfo = schemaInfo(schemaWithLookup("one", "int"), schemaWithLookup("two", "int"),
                                    schemaWithLookup("three", "long"), new Schema.Builder("four").build());
        assertFastMapLookupRejected("Grouping request references fast map lookup 'map.lookup', which has different value " +
                                    "types in the schemas searched in cluster 'myCluster': {one=INT, three=LONG, two=INT}. " +
                                    "Restrict the query to schemas with the same value type.",
                                    schemaInfo, createQuery(FAST_MAP_GROUPING));
        assertEquals(Field.Type.from("long"), resolvedValueType(schemaInfo, restricted(createQuery(FAST_MAP_GROUPING), "three")));
        assertEquals(Field.Type.from("int"), resolvedValueType(schemaInfo, restricted(createQuery(FAST_MAP_GROUPING), "one", "two", "four")));
    }

    @Test
    void fast_map_lookup_value_type_is_resolved_from_schemas_in_this_cluster_only() {
        var schemaInfo = new SchemaInfo(List.of(schemaWithLookup("one", "int"), schemaWithLookup("elsewhere", "long")),
                                        List.of(new Cluster.Builder("myCluster").addSchema("one").build(),
                                                new Cluster.Builder("otherCluster").addSchema("elsewhere").build()));
        assertEquals(Field.Type.from("int"), resolvedValueType(schemaInfo, createQuery(FAST_MAP_GROUPING)));
    }

    @Test
    void grouping_without_fast_map_lookup_is_passed_through() {
        for (var indexMode : List.of(ClusterConfig.IndexMode.Enum.INDEX, ClusterConfig.IndexMode.Enum.STREAMING)) {
            var query = createQuery("all(group(foo) each(output(max(bar))))");
            rewriteGrouping(indexMode, new AttributesConfig(new AttributesConfig.Builder()), SchemaInfo.empty(), query);
            assertEquals(1, query.getSelect().getGrouping().size());
        }
    }

    @Test
    void fast_map_lookup_throws_in_streaming_mode() {
        for (boolean validate : List.of(true, false)) {
            var query = createQuery(FAST_MAP_GROUPING);
            query.properties().set(GroupingValidator.PARAM_ENABLED, validate);
            var exception = assertThrows(IllegalArgumentException.class,
                                         () -> runWithValidator(ClusterConfig.IndexMode.Enum.STREAMING,
                                                                schemaInfo(schemaWithLookup("one", "int")), query));
            assertEquals("Grouping request references fast map lookup 'map.lookup', but fastsearchmap.decode " +
                         "is not supported in streaming mode (cluster 'myCluster').",
                         exception.getMessage());
        }
    }

    @Test
    void field_aliases_are_resolved_to_attribute_names() {
        for (var indexMode : ClusterConfig.IndexMode.Enum.values()) {
            String grouping = "all(group(bar) filter(regex(\"x\", baz)) each(output(max(attribute(bar)), sum(baz))))";
            Query query = createQuery(grouping);
            resolveAliases(indexMode, query);
            assertEquals(List.of("foo", "baz", "foo", "baz"), attributeNames(query), indexMode.toString());
            assertEquals(grouping, query.getSelect().getGrouping().get(0).getRootOperation().toString(),
                         "Labels are kept as written");
        }
    }

    @Test
    void resolved_field_alias_survives_copy() {
        Query query = createQuery("all(group(bar) each(output(count())))");
        resolveAliases(ClusterConfig.IndexMode.Enum.INDEX, query);
        var copy = query.getSelect().getGrouping().get(0).getRootOperation().copy(null);
        assertEquals("foo", ((AttributeValue)copy.getGroupBy()).getAttributeName());
        assertEquals("bar", copy.getGroupBy().toString());
    }

    @Test
    void field_aliases_pass_validation_and_are_resolved() {
        var attributesConfig = new AttributesConfig.Builder()
                .attribute(new AttributesConfig.Attribute.Builder().name("foo")
                                   .datatype(AttributesConfig.Attribute.Datatype.INT64))
                .build();
        var clusterConfig = clusterConfig(ClusterConfig.IndexMode.Enum.INDEX);
        Query query = createQuery("all(group(bar) each(output(count())))");
        new Execution(new Chain<Searcher>(new GroupingValidator(clusterConfig, attributesConfig),
                                          new GroupingRewriteSearcher(clusterConfig, attributesConfig)),
                      Execution.Context.createContextStub(aliasIndexFacts()))
                .search(query);
        assertEquals(List.of("foo"), attributeNames(query));
    }

    private static IndexFacts aliasIndexFacts() {
        SearchDefinition sd = new SearchDefinition("test");
        sd.addIndex(new Index("foo"));
        sd.addIndex(new Index("baz"));
        sd.addAlias("bar", "foo");
        return new IndexFacts(new IndexModel(sd));
    }

    private static void resolveAliases(ClusterConfig.IndexMode.Enum indexMode, Query query) {
        new Execution(new GroupingRewriteSearcher(clusterConfig(indexMode), new AttributesConfig.Builder().build()),
                      Execution.Context.createContextStub(aliasIndexFacts()))
                .search(query);
    }

    private static List<String> attributeNames(Query query) {
        List<String> names = new ArrayList<>();
        query.getSelect().getGrouping().get(0).getRootOperation().visitExpressions(exp -> {
            if (exp instanceof AttributeValue value) names.add(value.getAttributeName());
            else if (exp instanceof AttributeFunction function) names.add(function.getAttributeName());
        });
        return names;
    }

    private static AttributesConfig createFastMapLookupConfig() {
        var builder = new AttributesConfig.Builder();
        builder.attribute(new AttributesConfig.Attribute.Builder().name("map$lookup")
                                  .datatype(AttributesConfig.Attribute.Datatype.STRING)
                                  .collectiontype(AttributesConfig.Attribute.Collectiontype.ARRAY));
        return new AttributesConfig(builder);
    }

    private static Schema schemaWithLookup(String name, String valueType) {
        return new Schema.Builder(name)
                .add(new Field.Builder("map", "map<string," + valueType + ">").addFastMapSearch("lookup").build())
                .build();
    }

    private static SchemaInfo schemaInfo(Schema ... schemas) {
        var cluster = new Cluster.Builder("myCluster");
        for (var schema : schemas)
            cluster.addSchema(schema.name());
        return new SchemaInfo(List.of(schemas), List.of(cluster.build()));
    }

    private static Query restricted(Query query, String ... schemas) {
        query.getModel().setRestrict(String.join(",", schemas));
        return query;
    }

    private static Field.Type resolvedValueType(SchemaInfo schemaInfo, Query query) {
        rewriteGrouping(ClusterConfig.IndexMode.Enum.INDEX, createFastMapLookupConfig(), schemaInfo, query);
        return valueTypeOf(query);
    }

    private static Field.Type valueTypeOf(Query query) {
        var function = (FastSearchMapDecodeFunction)query.getSelect().getGrouping().get(0).getRootOperation().getGroupBy();
        return function.getValueType().orElseThrow();
    }

    private static void assertFastMapLookupRejected(String expectedMessage, SchemaInfo schemaInfo, Query query) {
        var exception = assertThrows(IllegalArgumentException.class,
                                     () -> rewriteGrouping(ClusterConfig.IndexMode.Enum.INDEX,
                                                           createFastMapLookupConfig(), schemaInfo, query));
        assertEquals(expectedMessage, exception.getMessage());
    }

    private static Query createQuery(String groupingExpression) {
        Query query = new Query();
        GroupingRequest req = GroupingRequest.newInstance(query);
        req.setRootOperation(GroupingOperation.fromString(groupingExpression));
        return query;
    }

    private static ClusterConfig clusterConfig(ClusterConfig.IndexMode.Enum indexMode) {
        return new ClusterConfig.Builder().clusterName("myCluster").indexMode(indexMode).build();
    }

    /** Runs GroupingValidator followed by this searcher, in the order they have in the search chain. */
    private static void runWithValidator(ClusterConfig.IndexMode.Enum indexMode, SchemaInfo schemaInfo, Query query) {
        var attributesConfig = createFastMapLookupConfig();
        new Execution(new Chain<Searcher>(new GroupingValidator(clusterConfig(indexMode), attributesConfig),
                                          new GroupingRewriteSearcher(clusterConfig(indexMode), attributesConfig)),
                      Execution.Context.createContextStub(schemaInfo))
                .search(query);
    }

    private static void rewriteGrouping(ClusterConfig.IndexMode.Enum indexMode, AttributesConfig attributesConfig,
                                        SchemaInfo schemaInfo, Query query) {
        new Execution(new GroupingRewriteSearcher(clusterConfig(indexMode), attributesConfig),
                      Execution.Context.createContextStub(schemaInfo))
                .search(query);
    }

}
