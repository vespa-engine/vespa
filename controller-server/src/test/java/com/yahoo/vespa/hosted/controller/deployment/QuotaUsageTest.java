// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.provision.zone.ZoneId;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author ogronnesby
 */
public class QuotaUsageTest {

    @Test
    public void testQuotaUsageIsPersisted() {
        var tester = new DeploymentTester();
        var context = tester.newDeploymentContext().submit().deploy();
        assertEquals(1.062, context.deployment(ZoneId.from("prod.us-west-1")).quota().rate(), 0.01);
    }

}
