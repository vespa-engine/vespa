// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.config.provision.ClusterSpec;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author smorgrav
 */
public class DeploymentCostTest {

    @Test
    public void deploymentCost() {
        Map<String, ClusterCost> clusters = new HashMap<>();
        clusters.put("cluster1", createClusterCost(100, 0.2));
        clusters.put("cluster2", createClusterCost(50, 0.1));

        DeploymentCost cost = new DeploymentCost(clusters);
        Assert.assertEquals(300, cost.getTco(), Double.MIN_VALUE); // 2*100 + 2*50
        Assert.assertEquals(28.5714285714, cost.getWaste(), 0.001); // from cluster2
        Assert.assertEquals(0.7142857142857143, cost.getUtilization(), Double.MIN_VALUE); // from cluster2
    }

    private ClusterCost createClusterCost(int flavorCost, double cpuUtil) {
        List<String> hostnames = new ArrayList<>();
        hostnames.add("host1");
        hostnames.add("host2");
        ClusterInfo info = new ClusterInfo("test", flavorCost, 10, 10, 10, ClusterSpec.Type.container, hostnames);
        ClusterUtilization util = new ClusterUtilization(0.3, cpuUtil, 0.5, 0.1);
        return new ClusterCost(info, util);
    }
}