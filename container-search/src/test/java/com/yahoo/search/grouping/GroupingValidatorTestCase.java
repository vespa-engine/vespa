// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping;

import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.search.Query;
import com.yahoo.search.config.ClusterConfig;
import com.yahoo.search.grouping.request.GroupingOperation;
import com.yahoo.search.searchchain.Execution;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class GroupingValidatorTestCase {

    @Test
    public void requireThatAvailableAttributesDoNotThrow() {
        Query query = new Query();
        GroupingRequest req = GroupingRequest.newInstance(query);
        req.setRootOperation(GroupingOperation.fromString("all(group(foo) each(output(max(bar))))"));
        validateGrouping("myCluster", Arrays.asList("foo", "bar"), query);
    }

    @Test
    public void requireThatUnavailableAttributesThrow() {
        Query query = new Query();
        GroupingRequest req = GroupingRequest.newInstance(query);
        req.setRootOperation(GroupingOperation.fromString("all(group(foo) each(output(max(bar))))"));
        try {
            validateGrouping("myCluster", Arrays.asList("foo"), query);
            fail("Validator should throw exception because attribute 'bar' is unavailable.");
        } catch (UnavailableAttributeException e) {
            assertEquals("myCluster", e.getClusterName());
            assertEquals("bar", e.getAttributeName());
        }
    }

    @Test
    public void requireThatEnableFlagPreventsThrow() {
        Query query = new Query();
        GroupingRequest req = GroupingRequest.newInstance(query);
        req.setRootOperation(GroupingOperation.fromString("all(group(foo) each(output(max(bar))))"));
        query.properties().set(GroupingValidator.PARAM_ENABLED, "false");
        validateGrouping("myCluster", Arrays.asList("foo"), query);
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
