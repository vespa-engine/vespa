package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.vespa.hosted.provision.node.Agent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static com.yahoo.vespa.hosted.provision.provisioning.ProvisioningThrottler.throttle;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author mpolden
 */
class ProvisioningThrottlerTest {

    @Test
    void throttling() {
        Agent agent = Agent.system;
        assertFalse(throttle(99, 99, agent));
        assertTrue(throttle(100, 99, agent));
        assertFalse(throttle(40, 100, agent));
        assertTrue(throttle(41, 100, agent));
        assertTrue(throttle(100, 100, agent));
        assertFalse(throttle(200, 2100, agent));
        assertTrue(throttle(201, 2100, agent));
    }

}
