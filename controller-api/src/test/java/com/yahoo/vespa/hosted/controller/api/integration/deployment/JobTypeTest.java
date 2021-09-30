// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @author jonmv
 */
public class JobTypeTest {

    @Test
    public void test() {
        for (JobType type : JobType.values()) {
            if (type.isProduction()) {
                boolean match = false;
                for (JobType other : JobType.values())
                    match |=    type != other
                             && type.isTest() == other.isDeployment()
                             && type.zones.equals(other.zones);

                assertTrue(type + " should have matching job", match);
            }
        }
    }

}
