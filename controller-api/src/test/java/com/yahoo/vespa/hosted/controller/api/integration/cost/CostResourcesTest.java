// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.api.integration.cost;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author smorgrav
 */
public class CostResourcesTest {

    private static double delta = Double.MIN_EXPONENT;

    @Test
    public void getMaxUtilization() throws Exception {
        CostResources resources = new CostResources(0.3, 0.1, 0.4, 0.5);
        Assert.assertEquals(0.5, resources.getMaxUtilization(), delta);

        resources = new CostResources(0.3, 0.1, 0.4, 0.0);
        Assert.assertEquals(0.4, resources.getMaxUtilization(), delta);

        resources = new CostResources(0.4, 0.3, 0.3, 0.0);
        Assert.assertEquals(0.4, resources.getMaxUtilization(), delta);

        resources = new CostResources(0.1, 0.3, 0.3, 0.0);
        Assert.assertEquals(0.3, resources.getMaxUtilization(), delta);
    }
}