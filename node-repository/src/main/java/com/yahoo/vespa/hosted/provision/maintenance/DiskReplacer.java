// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeMutex;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner.RebuildResult;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Rebuilds hosts by replacing the root disk (only supports hosts with remote storage).
 *
 * @author mpolden
 */
public class DiskReplacer extends NodeRepositoryMaintainer {

    private static final Logger log = Logger.getLogger(DiskReplacer.class.getName());

    private final HostProvisioner hostProvisioner;

    DiskReplacer(NodeRepository nodeRepository, Duration interval, Metric metric, HostProvisioner hostProvisioner) {
        super(nodeRepository, interval, metric);
        this.hostProvisioner = hostProvisioner;
    }

    @Override
    protected double maintain() {
        NodeList candidates = nodeRepository().nodes().list().rebuilding(true);
        if (candidates.isEmpty()) {
            return 0;
        }
        int failures = 0;
        List<Node> rebuilding;
        try (var locked = nodeRepository().nodes().lockAndGetAll(candidates.asList(), Optional.of(Duration.ofSeconds(10)))) {
            rebuilding = locked.nodes().stream().map(NodeMutex::node).toList();
            RebuildResult result = hostProvisioner.replaceRootDisk(rebuilding);

            for (Node updated : result.rebuilt())
                if (!updated.status().wantToRebuild())
                    nodeRepository().nodes().write(updated, () -> { });

            for (var entry : result.failed().entrySet()) {
                ++failures;
                log.log(Level.WARNING, "Failed to rebuild " + entry.getKey() + ", will retry in " +
                                       interval() + ": " + Exceptions.toMessageString(entry.getValue()));
            }
        }
        return asSuccessFactorDeviation(rebuilding.size(), failures);
    }

}
