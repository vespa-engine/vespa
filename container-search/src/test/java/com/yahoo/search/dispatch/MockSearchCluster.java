// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.search.dispatch.searchcluster.SearchGroupsImpl;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;
import com.yahoo.vespa.config.search.DispatchConfig;

/**
 * @author ollivir
 */
public class MockSearchCluster extends SearchCluster {

    public MockSearchCluster(String clusterId, int groups, int nodesPerGroup) {
        super(clusterId, SearchGroupsImpl.buildGroupListForTest(groups, nodesPerGroup, 88.0), null, null);
    }

    @Override
    public int groupsWithSufficientCoverage() {
        return groupList().size();
    }

    @Override
    public void working(Node node) {
        node.setWorking(true);
    }

    @Override
    public void failed(Node node) {
        node.setWorking(false);
    }

    public static DispatchConfig createDispatchConfig() {
        return createDispatchConfig(100.0);
    }

    public static DispatchConfig createDispatchConfig(double minSearchCoverage) {
        return createDispatchConfigBuilder(minSearchCoverage).build();
    }

    public static DispatchConfig.Builder createDispatchConfigBuilder(double minSearchCoverage) {
        DispatchConfig.Builder builder = new DispatchConfig.Builder();
        builder.minActivedocsPercentage(88.0);
        builder.minSearchCoverage(minSearchCoverage);
        builder.distributionPolicy(DispatchConfig.DistributionPolicy.Enum.ROUNDROBIN);
        if (minSearchCoverage < 100.0) {
            builder.minWaitAfterCoverageFactor(0);
            builder.maxWaitAfterCoverageFactor(0.5);
        }
        return builder;
    }

}
