// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import ai.vespa.metrics.ConfigServerMetrics;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.flags.IntFlag;
import com.yahoo.vespa.flags.PermanentFlags;
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
    private static final String throttlingActiveMetric = ConfigServerMetrics.THROTTLED_HOST_PROVISIONING.baseName();

    private static final Logger LOG = Logger.getLogger(ProvisioningThrottler.class.getName());
    private static final Duration WINDOW = Duration.ofDays(1);

    private final NodeRepository nodeRepository;
    private final Metric metric;
    private final IntFlag maxHostsPerHourFlag;

    public ProvisioningThrottler(NodeRepository nodeRepository, Metric metric) {
        this.nodeRepository = Objects.requireNonNull(nodeRepository);
        this.metric = Objects.requireNonNull(metric);
        this.maxHostsPerHourFlag = PermanentFlags.MAX_HOSTS_PER_HOUR.bindTo(nodeRepository.flagSource());
    }

    /** Returns whether provisioning should be throttled at given instant */
    public boolean throttle(NodeList allNodes, Agent agent) {
        Instant startOfWindow = nodeRepository.clock().instant().minus(WINDOW);
        int provisionedRecently = allNodes.matching(host -> host.history().hasEventAfter(History.Event.Type.provisioned,
                                                                                         startOfWindow))
                                          .size();
        boolean throttle = throttle(provisionedRecently, maxHostsPerHourFlag.value(), agent);
        metric.set(throttlingActiveMetric, throttle ? 1 : 0, null);
        return throttle;
    }

    static boolean throttle(int recentHosts, int maxPerHour, Agent agent) {
        double rate = recentHosts / (double) WINDOW.toMillis();
        boolean throttle = (rate * Duration.ofHours(1).toMillis()) > maxPerHour;
        if (throttle) {
            LOG.warning(String.format("Throttling provisioning of new hosts by %s: %d hosts have been provisioned " +
                                      "in the past %s, which exceeds maximum rate of %d hosts/hour", agent,
                                      recentHosts, WINDOW, maxPerHour));
        }
        return throttle;
    }

}
