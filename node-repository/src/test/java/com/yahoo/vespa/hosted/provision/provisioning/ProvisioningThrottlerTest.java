// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.vespa.hosted.provision.node.Agent;
import org.junit.jupiter.api.Test;

import static com.yahoo.vespa.hosted.provision.provisioning.ProvisioningThrottler.throttle;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author mpolden
 */
class ProvisioningThrottlerTest {

    @Test
    void throttling() {
        Agent agent = Agent.system;
        assertFalse(throttle(239, 10, agent));
        assertFalse(throttle(240, 10, agent));
        assertTrue(throttle(241, 10, agent));
    }

}
