// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Throttles provisioning of new hosts in dynamically provisioned zones.
 *
 * @author mpolden
 */
public class ProvisioningThrottler {

    /** Metric that indicates whether throttling is active where 1 means active and 0 means inactive */
    private static final String throttlingActiveMetric = "throttledHostProvisioning";

    private static final Logger LOG = Logger.getLogger(ProvisioningThrottler.class.getName());

    private static final int MIN_SIZE = 100;
    private static final int MAX_GROWTH = 200;
    private static final double MAX_GROWTH_RATE = 0.4;

    private final NodeRepository nodeRepository;
    private final Metric metric;

    public ProvisioningThrottler(NodeRepository nodeRepository, Metric metric) {
        this.nodeRepository = Objects.requireNonNull(nodeRepository);
        this.metric = Objects.requireNonNull(metric);
    }

    private Duration window() {
        return nodeRepository.zone().system().isCd() ? Duration.ofHours(2) : Duration.ofHours(8);
    }

    /** Returns whether provisioning should be throttled at given instant */
    public boolean throttle(NodeList allNodes, Agent agent) {
        Duration window = window();
        Instant startOfWindow = nodeRepository.clock().instant().minus(window);
        NodeList hosts = allNodes.hosts();
        int existingHosts = hosts.not().state(Node.State.deprovisioned).size();
        int provisionedRecently = hosts.matching(host -> host.history().hasEventAfter(History.Event.Type.provisioned, startOfWindow))
                                       .size();
        boolean throttle = throttle(provisionedRecently, existingHosts, window, agent);
        metric.set(throttlingActiveMetric, throttle ? 1 : 0, null);
        return throttle;
    }

    static boolean throttle(int recent, int total, Duration window, Agent agent) {
        if (total < MIN_SIZE && recent < MIN_SIZE) return false; // Allow burst in small zones
        int maxGrowth = Math.min(MAX_GROWTH, (int) (total * MAX_GROWTH_RATE));
        boolean throttle = recent > maxGrowth;
        if (throttle) {
            LOG.warning(String.format("Throttling provisioning of new hosts by %s: %d hosts have been provisioned " +
                                      "in the past %s, which exceeds growth limit of %d", agent,
                                      recent, window, maxGrowth));
        }
        return throttle;
    }

}
