// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping;

import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.search.Query;
import com.yahoo.search.config.ClusterConfig;
import com.yahoo.search.grouping.request.DecodeFastMapLookupFunction;
import com.yahoo.search.grouping.request.GroupingOperation;
import com.yahoo.search.schema.Cluster;
import com.yahoo.search.schema.Field;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Simon Thoresen Hult
 */
public class GroupingValidatorTestCase {

    @Test
    void requireThatAvailableAttributesDoNotThrow() {
        validateGrouping(List.of("foo", "bar"),
                "all(group(foo) each(output(max(bar))))");
    }

    @Test
    void requireThatUnavailableAttributesThrow() {
        try {
            validateGrouping(List.of("foo"), "all(group(foo) each(output(max(bar))))");
            fail("Excpected exception");
        }
        catch (UnavailableAttributeException e) {
            assertEquals(createMessage("bar"), e.getMessage());
        }
    }

    @Test
    void requireThatEnableFlagPreventsThrow() {
        Query query = createQuery("all(group(foo) each(output(max(bar))))");
        query.properties().set(GroupingValidator.PARAM_ENABLED, "false");
        validateGrouping(List.of("foo"), query);
    }

    @Test
    void available_primitive_map_attribute_does_not_throw() {
        validateGrouping(List.of("map.key", "map.value"),
                "all(group(map{\"foo\"}) each(output(count())))");
    }

    @Test
    void unavailable_primitive_map_key_attribute_throws() {
        try {
            validateGrouping(List.of("null"), "all(group(map{\"foo\"}) each(output(count())))");
            fail("Expected exception");
        }
        catch (UnavailableAttributeException e) {
            assertEquals(createMessage("map.key"), e.getMessage());
        }
    }

    @Test
    void unavailable_primitive_map_value_attribute_throws() {
        try {
            validateGrouping(List.of("map.key"), "all(group(map{\"foo\"}) each(output(count())))");
            fail("Expected exception");
        }
        catch (UnavailableAttributeException e) {
            assertEquals(createMessage("map.value"), e.getMessage());
        }
    }

    @Test
    void available_struct_map_attribute_does_not_throw() {
        validateGrouping(List.of("map.key", "map.value.name"),
                "all(group(map{\"foo\"}.name) each(output(count())))");
    }

    @Test
    void unavailable_struct_map_key_attribute_throws() {
        try {
            validateGrouping(List.of("null"), "all(group(map{\"foo\"}.name) each(output(count())))");
            fail("Expected exception");
        }
        catch (UnavailableAttributeException e) {
            assertEquals(createMessage("map.key"), e.getMessage());
        }
    }

    @Test
    void unavailable_struct_map_value_attribute_throws() {
        try {
            validateGrouping(List.of("map.key"), "all(group(map{\"foo\"}.name) each(output(count())))");
            fail("Expected exception");
        }
        catch (UnavailableAttributeException e) {
            assertEquals(createMessage("map.value.name"), e.getMessage());
        }
    }

    @Test
    void available_key_source_attribute_does_not_throw() {
        validateGrouping(List.of("map.key", "map.value", "key_source"),
                "all(group(map{attribute(key_source)}) each(output(count())))");
    }

    @Test
    void unavailable_key_source_attribute_throws() {
        try {
            validateGrouping(List.of("map.key", "map.value"),
                    "all(group(map{attribute(key_source)}) each(output(count())))");
            fail("Expected exception");
        }
        catch (UnavailableAttributeException e) {
            assertEquals(createMessage("key_source"), e.getMessage());
        }
    }

    @Test
    void key_source_attribute_with_mismatching_data_type_throws() {
        try {
            validateGrouping(setupMismatchingKeySourceAttribute(false),
                    "all(group(map{attribute(key_source)}) each(output(count())))");
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Grouping request references key source attribute 'key_source' with data type 'int' " +
                    "that is different than data type 'string' of key attribute 'map.key'",
                    e.getMessage());
        }
    }

    @Test
    void key_source_attribute_with_multi_value_collection_type_throws() {
        try {
            validateGrouping(setupMismatchingKeySourceAttribute(true),
                    "all(group(map{attribute(key_source)}) each(output(count())))");
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Grouping request references key source attribute 'key_source' with type 'array<string>' which is not of single value type",
                    e.getMessage());
        }
    }

    private static void unsupported_attribute_type_throws(String name,
                                                          AttributesConfig.Attribute.Datatype.Enum datatype,
                                                          String typeName) {
        try {
            validate_attribute_type(name, datatype);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Grouping request references attribute '" + name + "' " +
                            "with unsupported type '" + typeName + "'",
                    e.getMessage());
        }
    }

    private static void validate_attribute_type(String name, AttributesConfig.Attribute.Datatype.Enum datatype) {
        var builder = new AttributesConfig.Builder();
        builder.attribute(new AttributesConfig.Attribute.Builder().name(name).datatype(datatype)
                .tensortype(datatype == AttributesConfig.Attribute.Datatype.TENSOR ? "tensor(x[2])" : ""));
        validateGrouping(new AttributesConfig(builder),
                "all(group(" + name + ") each(output(count())))");

    }

    @Test
    void tensor_attribute_throws() {
        unsupported_attribute_type_throws("tensor", AttributesConfig.Attribute.Datatype.TENSOR, "tensor(x[2])");
    }

    @Test
    void predicate_attribute_throws() {
        unsupported_attribute_type_throws("predicate", AttributesConfig.Attribute.Datatype.PREDICATE, "predicate");
    }

    @Test
    void reference_attribute_is_ok() {
        validate_attribute_type("reference", AttributesConfig.Attribute.Datatype.REFERENCE);
    }

    @Test
    void raw_attribute_is_ok() {
        validate_attribute_type("raw", AttributesConfig.Attribute.Datatype.RAW);
    }

    @Test
    void bool_attribute_is_ok() {
        validate_attribute_type("mybool", AttributesConfig.Attribute.Datatype.BOOL);
    }

    @Test
    void raw_attribute_cannot_be_used_for_lookup() {
        try {
            var builder = new AttributesConfig.Builder();
            builder.attribute(new AttributesConfig.Attribute.Builder().name("map.key")
                    .datatype(AttributesConfig.Attribute.Datatype.RAW));
            builder.attribute(new AttributesConfig.Attribute.Builder().name("map.value")
                            .datatype(AttributesConfig.Attribute.Datatype.STRING));
            builder.attribute(new AttributesConfig.Attribute.Builder().name("key_source")
                            .datatype(AttributesConfig.Attribute.Datatype.RAW));
            validateGrouping(new AttributesConfig(builder),
                    "all(group(map{attribute(key_source)}) each(output(count())))");
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Grouping request references attribute 'map.key' " +
                            "with unsupported type 'raw' for map lookup",
                    e.getMessage());
        }
    }

    @Test
    void fast_map_lookup_value_type_is_resolved() {
        for (String type : List.of("int", "long", "float", "double", "string")) {
            var schemaInfo = schemaInfo(schemaWithLookup("one", type));
            assertEquals(Field.Type.from(type), resolvedValueType(schemaInfo, createQuery(FAST_MAP_GROUPING)));
        }
        var query = createQuery("all(group(decodefastmaplookup(map.lookup, \": \")) each(output(count())))");
        assertEquals(Field.Type.from("int"), resolvedValueType(schemaInfo(schemaWithLookup("one", "int")), query));
    }

    @Test
    void fast_map_lookup_value_type_is_resolved_when_validation_is_disabled() {
        var query = createQuery(FAST_MAP_GROUPING);
        query.properties().set(GroupingValidator.PARAM_ENABLED, false);
        assertEquals(Field.Type.from("long"), resolvedValueType(schemaInfo(schemaWithLookup("one", "long")), query));
    }

    @Test
    void unavailable_fast_map_lookup_throws() {
        var schemaInfo = schemaInfo(schemaWithLookup("one", "int"));
        assertFastMapLookupRejected("Grouping request references fast map lookup 'map.other' which is not available in cluster 'myCluster'.",
                                    schemaInfo, createQuery("all(group(decodefastmaplookup(map.other)) each(output(count())))"));
        // The lookup attribute is missing, as for an imported field
        var query = createQuery(FAST_MAP_GROUPING);
        var exception = assertThrows(IllegalArgumentException.class,
                                     () -> validateGrouping("myCluster", ClusterConfig.IndexMode.Enum.INDEX,
                                                            createAttributesConfig(List.of()), schemaInfo, query));
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
    void fast_map_lookup_throws_in_streaming_mode() {
        for (boolean validate : List.of(true, false)) {
            var query = createQuery(FAST_MAP_GROUPING);
            query.properties().set(GroupingValidator.PARAM_ENABLED, validate);
            var exception = assertThrows(IllegalArgumentException.class,
                                         () -> validateGrouping("myCluster", ClusterConfig.IndexMode.Enum.STREAMING,
                                                                createFastMapLookupConfig(),
                                                                schemaInfo(schemaWithLookup("one", "int")), query));
            assertEquals("Grouping request references fast map lookup 'map.lookup', but decodefastmaplookup " +
                         "is not supported in streaming mode (cluster 'myCluster').",
                         exception.getMessage());
        }
    }

    @Test
    void other_grouping_is_not_validated_in_streaming_mode() {
        validateGrouping("myCluster", ClusterConfig.IndexMode.Enum.STREAMING, createAttributesConfig(List.of()),
                         SchemaInfo.empty(), createQuery("all(group(foo) each(output(max(bar))))"));
    }

    private static final String FAST_MAP_GROUPING = "all(group(decodefastmaplookup(map.lookup)) each(output(count())))";

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
        validateGrouping("myCluster", ClusterConfig.IndexMode.Enum.INDEX, createFastMapLookupConfig(), schemaInfo, query);
        var function = (DecodeFastMapLookupFunction)query.getSelect().getGrouping().get(0).getRootOperation().getGroupBy();
        return function.getValueType().orElseThrow();
    }

    private static void assertFastMapLookupRejected(String expectedMessage, SchemaInfo schemaInfo, Query query) {
        var exception = assertThrows(IllegalArgumentException.class,
                                     () -> validateGrouping("myCluster", ClusterConfig.IndexMode.Enum.INDEX,
                                                            createFastMapLookupConfig(), schemaInfo, query));
        assertEquals(expectedMessage, exception.getMessage());
    }

    private static AttributesConfig setupMismatchingKeySourceAttribute(boolean matchingDataType) {
        AttributesConfig.Builder builder = new AttributesConfig.Builder();
        builder.attribute(new AttributesConfig.Attribute.Builder().name("map.key")
                .datatype(AttributesConfig.Attribute.Datatype.Enum.STRING));
        builder.attribute(new AttributesConfig.Attribute.Builder().name("map.value")
                .datatype(AttributesConfig.Attribute.Datatype.STRING));
        builder.attribute(new AttributesConfig.Attribute.Builder().name("key_source")
                .datatype(matchingDataType ? AttributesConfig.Attribute.Datatype.Enum.STRING :
                        AttributesConfig.Attribute.Datatype.Enum.INT32)
                .collectiontype(AttributesConfig.Attribute.Collectiontype.Enum.ARRAY));
        return new AttributesConfig(builder);
    }

    private static String createMessage(String attributeName) {
        return "Grouping request references attribute '" + attributeName + "' which is not available in cluster 'myCluster'.";
    }

    private static Query createQuery(String groupingExpression) {
        Query query = new Query();
        GroupingRequest req = GroupingRequest.newInstance(query);
        req.setRootOperation(GroupingOperation.fromString(groupingExpression));
        return query;
    }

    private static AttributesConfig createAttributesConfig(Collection<String> attributeNames) {
        AttributesConfig.Builder builder = new AttributesConfig.Builder();
        for (String attributeName : attributeNames) {
            builder.attribute(new AttributesConfig.Attribute.Builder()
                    .name(attributeName).datatype(AttributesConfig.Attribute.Datatype.INT32));
        }
        return new AttributesConfig(builder);
    }

    private static void validateGrouping(Collection<String> attributeNames, String groupingExpression) {
        validateGrouping("myCluster", createAttributesConfig(attributeNames), createQuery(groupingExpression));
    }

    private static void validateGrouping(AttributesConfig attributesCfg, String groupingExpression) {
        validateGrouping("myCluster", attributesCfg, createQuery(groupingExpression));
    }

    private static void validateGrouping(Collection<String> attributeNames, Query query) {
        validateGrouping("myCluster", createAttributesConfig(attributeNames), query);
    }

    private static void validateGrouping(String clusterName, AttributesConfig attributesConfig, Query query) {
        validateGrouping(clusterName, ClusterConfig.IndexMode.Enum.INDEX, attributesConfig, SchemaInfo.empty(), query);
    }

    private static void validateGrouping(String clusterName, ClusterConfig.IndexMode.Enum indexMode,
                                         AttributesConfig attributesConfig, SchemaInfo schemaInfo, Query query) {
        ClusterConfig.Builder clusterConfig = new ClusterConfig.Builder()
                .clusterName(clusterName)
                .indexMode(indexMode);
        new Execution(new GroupingValidator(clusterConfig.build(), attributesConfig),
                      Execution.Context.createContextStub(schemaInfo))
                .search(query);
    }
}
