package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.vespa.hosted.provision.node.Agent;
import org.junit.jupiter.api.Test;

import java.time.Duration;

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
        Duration window = Duration.ofHours(1);
        assertFalse(throttle(199, 99, window, agent));
        assertTrue(throttle(200, 99, window, agent));
        assertFalse(throttle(40, 100, window, agent));
        assertTrue(throttle(41, 100, window, agent));
        assertTrue(throttle(100, 100, window, agent));
        assertFalse(throttle(200, 2100, window, agent));
        assertTrue(throttle(201, 2100, window, agent));
    }

}
