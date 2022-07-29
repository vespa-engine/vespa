// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping;

import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.search.Query;
import com.yahoo.search.config.ClusterConfig;
import com.yahoo.search.grouping.request.GroupingOperation;
import com.yahoo.search.searchchain.Execution;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Simon Thoresen Hult
 */
public class GroupingValidatorTestCase {

    @Test
    void requireThatAvailableAttributesDoNotThrow() {
        validateGrouping(List.of("foo", "bar"),
                "all(group(foo) each(output(max(bar))))");
        ;
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
            assertEquals("Grouping request references key source attribute 'key_source' with data type 'INT32' " +
                    "that is different than data type 'STRING' of key attribute 'map.key'",
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
            assertEquals("Grouping request references key source attribute 'key_source' which is not of single value type",
                    e.getMessage());
        }
    }

    private static AttributesConfig setupMismatchingKeySourceAttribute(boolean matchingDataType) {
        AttributesConfig.Builder builder = new AttributesConfig.Builder();
        builder.attribute(new AttributesConfig.Attribute.Builder().name("map.key")
                .datatype(AttributesConfig.Attribute.Datatype.Enum.STRING));
        builder.attribute(new AttributesConfig.Attribute.Builder().name("map.value"));
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
                    .name(attributeName));
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
        QrSearchersConfig.Builder qrsConfig = new QrSearchersConfig.Builder().searchcluster(
                new QrSearchersConfig.Searchcluster.Builder()
                        .indexingmode(QrSearchersConfig.Searchcluster.Indexingmode.Enum.REALTIME)
                        .name(clusterName));
        ClusterConfig.Builder clusterConfig = new ClusterConfig.Builder().
                clusterId(0).
                clusterName("test");
        new Execution(
                new GroupingValidator(new QrSearchersConfig(qrsConfig),
                        new ClusterConfig(clusterConfig),
                        attributesConfig),
                Execution.Context.createContextStub()).search(query);
    }
}
