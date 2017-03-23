// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.History;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * This moves nodes from failed back to dirty if
 * <ul>
 *     <li>No hardware failure is known to be detected on the node
 *     <li>The node has failed less than 5 times OR the environment is dev, test or perf OR system is CI or CD,
 *     as those environments have no protection against users running bogus applications, so
 *     we cannot use the node failure count to conclude the node has a failure.
 * </ul>
 * Failed nodes are typically given a long expiry time to enable us to manually moved them back to
 * active to recover data in cases where the node was failed accidentally.
 * <p>
 * The purpose of the automatic recycling to dirty + fail count is that nodes which were moved
 * to failed due to some undetected hardware failure will end up being failed again.
 * When that has happened enough they will not be recycled.
 * <p>
 * The Chef recipe running locally on the node may set the hardwareFailure flag to avoid the node
 * being automatically recycled in cases where an error has been positively detected.
 *
 * @author bratseth
 */
public class FailedExpirer extends Expirer {

    private final NodeRepository nodeRepository;
    private final Zone zone;

    public FailedExpirer(NodeRepository nodeRepository, Zone zone, Clock clock, Duration failTimeout) {
        super(Node.State.failed, History.Event.Type.failed, nodeRepository, clock, failTimeout);
        this.nodeRepository = nodeRepository;
        this.zone = zone;
    }

    @Override
    protected void expire(List<Node> expired) {
        List<Node> nodesToRecycle = new ArrayList<>();
        for (Node recycleCandidate : expired) {
            if (recycleCandidate.status().hardwareFailure().isPresent()) continue;
            if (failCountIndicatesHwFail(zone) && recycleCandidate.status().failCount() >= 5) continue;
            nodesToRecycle.add(recycleCandidate);
        }
        nodeRepository.setDirty(nodesToRecycle);
    }

    private boolean failCountIndicatesHwFail(Zone zone) {
        if (zone.system() == SystemName.cd) {
            return false;
        }
        return zone.environment() == Environment.prod || zone.environment() == Environment.staging;
    }

}
