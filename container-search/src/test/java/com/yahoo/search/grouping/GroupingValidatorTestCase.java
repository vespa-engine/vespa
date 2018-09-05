// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping;

import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.search.Query;
import com.yahoo.search.config.ClusterConfig;
import com.yahoo.search.grouping.request.GroupingOperation;
import com.yahoo.search.searchchain.Execution;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author Simon Thoresen Hult
 */
public class GroupingValidatorTestCase {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void requireThatAvailableAttributesDoNotThrow() {
        validateGrouping("myCluster", Arrays.asList("foo", "bar"),
                "all(group(foo) each(output(max(bar))))");;
    }

    @Test
    public void requireThatUnavailableAttributesThrow() {
        thrown.expect(UnavailableAttributeException.class);
        thrown.expectMessage(createMessage("myCluster", "bar"));
        validateGrouping("myCluster", Arrays.asList("foo"),
                "all(group(foo) each(output(max(bar))))");
    }

    @Test
    public void requireThatEnableFlagPreventsThrow() {
        Query query = createQuery("all(group(foo) each(output(max(bar))))");
        query.properties().set(GroupingValidator.PARAM_ENABLED, "false");
        validateGrouping("myCluster", Arrays.asList("foo"), query);
    }

    @Test
    public void available_primitive_map_attribute_does_not_throw() {
        validateGrouping("myCluster", Arrays.asList("map.key", "map.value"),
                "all(group(map{\"foo\"}) each(output(count())))");
    }

    @Test
    public void unavailable_primitive_map_key_attribute_throws() {
        thrown.expect(UnavailableAttributeException.class);
        thrown.expectMessage(createMessage("myCluster", "map.key"));
        validateGrouping("myCluster", Arrays.asList("null"),
                "all(group(map{\"foo\"}) each(output(count())))");
    }

    @Test
    public void unavailable_primitive_map_value_attribute_throws() {
        thrown.expect(UnavailableAttributeException.class);
        thrown.expectMessage(createMessage("myCluster", "map.value"));
        validateGrouping("myCluster", Arrays.asList("map.key"),
                "all(group(map{\"foo\"}) each(output(count())))");
    }

    @Test
    public void available_struct_map_attribute_does_not_throw() {
        validateGrouping("myCluster", Arrays.asList("map.key", "map.value.name"),
                "all(group(map{\"foo\"}.name) each(output(count())))");
    }

    @Test
    public void unavailable_struct_map_key_attribute_throws() {
        thrown.expect(UnavailableAttributeException.class);
        thrown.expectMessage(createMessage("myCluster", "map.key"));
        validateGrouping("myCluster", Arrays.asList("null"),
                "all(group(map{\"foo\"}.name) each(output(count())))");
    }

    @Test
    public void unavailable_struct_map_value_attribute_throws() {
        thrown.expect(UnavailableAttributeException.class);
        thrown.expectMessage(createMessage("myCluster", "map.value.name"));
        validateGrouping("myCluster", Arrays.asList("map.key"),
                "all(group(map{\"foo\"}.name) each(output(count())))");
    }

    private static String createMessage(String clusterName, String attributeName) {
        return "Grouping request references attribute '" + attributeName + "' which is not available in cluster '" + clusterName + "'.";
    }

    private static Query createQuery(String groupingExpression) {
        Query query = new Query();
        GroupingRequest req = GroupingRequest.newInstance(query);
        req.setRootOperation(GroupingOperation.fromString(groupingExpression));
        return query;
    }

    private static void validateGrouping(String clusterName, Collection<String> attributeNames, String groupingExpression) {
        validateGrouping(clusterName, attributeNames, createQuery(groupingExpression));
    }

    private static void validateGrouping(String clusterName, Collection<String> attributeNames, Query query) {
        QrSearchersConfig.Builder qrsConfig = new QrSearchersConfig.Builder().searchcluster(
                new QrSearchersConfig.Searchcluster.Builder()
                        .indexingmode(QrSearchersConfig.Searchcluster.Indexingmode.Enum.REALTIME)
                        .name(clusterName));
        ClusterConfig.Builder clusterConfig = new ClusterConfig.Builder().
                clusterId(0).
                clusterName("test");
        AttributesConfig.Builder attributesConfig = new AttributesConfig.Builder();
        for (String attributeName : attributeNames) {
            attributesConfig.attribute(new AttributesConfig.Attribute.Builder()
                                            .name(attributeName));
        }
        new Execution(
                new GroupingValidator(new QrSearchersConfig(qrsConfig),
                                      new ClusterConfig(clusterConfig),
                                      new AttributesConfig(attributesConfig)),
                Execution.Context.createContextStub()).search(query);
    }
}
