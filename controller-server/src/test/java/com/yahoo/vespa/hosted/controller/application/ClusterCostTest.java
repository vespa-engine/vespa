// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.config.provision.ClusterSpec;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * @author smorgrav
 */
public class ClusterCostTest {

    @Test
    public void clusterCost() {
        List<String> hostnames = new ArrayList<>();
        hostnames.add("host1");
        hostnames.add("host2");
        ClusterInfo info = new ClusterInfo("test", 100, 10, 10, 10, ClusterSpec.Type.container, hostnames);
        ClusterUtilization util = new ClusterUtilization(0.3, 0.2, 0.5, 0.1);
        ClusterCost cost = new ClusterCost(info, util);

        // CPU is fully utilized
        Assert.assertEquals(200, cost.getTco(), Double.MIN_VALUE);
        Assert.assertEquals(0, cost.getWaste(), Double.MIN_VALUE);

        // Set Disk as the most utilized resource
        util = new ClusterUtilization(0.3, 0.1, 0.5, 0.1);
        cost = new ClusterCost(info, util);
        Assert.assertEquals(200, cost.getTco(), Double.MIN_NORMAL); // TCO is independent of utilization
        Assert.assertEquals(57.1428571429, cost.getWaste(), 0.001); // Waste is not independent
    }
}
