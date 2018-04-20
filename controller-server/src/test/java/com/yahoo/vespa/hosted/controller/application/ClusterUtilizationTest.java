// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author smorgrav
 */
public class ClusterUtilizationTest {

    private static final double delta = Double.MIN_NORMAL;

    @Test
    public void getMaxUtilization() {
        ClusterUtilization resources = new ClusterUtilization(0.3, 0.1, 0.4, 0.5);
        Assert.assertEquals(0.5, resources.getMaxUtilization(), delta);

        resources = new ClusterUtilization(0.3, 0.1, 0.4, 0.0);
        Assert.assertEquals(0.4, resources.getMaxUtilization(), delta);

        resources = new ClusterUtilization(0.4, 0.3, 0.3, 0.0);
        Assert.assertEquals(0.4, resources.getMaxUtilization(), delta);

        resources = new ClusterUtilization(0.1, 0.3, 0.3, 0.0);
        Assert.assertEquals(0.3, resources.getMaxUtilization(), delta);
    }

}
