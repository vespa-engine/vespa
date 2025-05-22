// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeMutex;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.backup.Snapshot;
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
        int attempts = 0;
        int failures = 0;
        NodeList rebuildCandidates = nodeRepository().nodes().list().rebuilding(true);
        try (var locked = nodeRepository().nodes().lockAndGetAll(rebuildCandidates.asList(), Optional.of(Duration.ofSeconds(10)))) {
            List<Node> rebuilding = locked.nodes().stream().map(NodeMutex::node).toList();
            RebuildResult result = hostProvisioner.replaceRootDisk(rebuilding);
            for (Node updated : result.successes()) {
                if (!updated.status().wantToRebuild()) {
                    nodeRepository().nodes().write(updated, () -> {});
                }
            }
            for (var entry : result.failed().entrySet()) {
                ++failures;
                log.log(Level.WARNING, "Failed to replace root disk on " + entry.getKey() + ", will retry in " +
                        interval() + ": " + Exceptions.toMessageString(entry.getValue()));
            }
            attempts += result.successes().size() + result.failed().size();
        }

        NodeList hostsStartingRebuild = nodeRepository().nodes().list().startingRebuild();

        try (var locked = nodeRepository().nodes().lockAndGetAll(hostsStartingRebuild.asList(), Optional.of(Duration.ofSeconds(10)))) {
            List<Node> starting = locked.nodes().stream().map(NodeMutex::node).toList();
            RebuildResult startResult = hostProvisioner.startHosts(starting);

            for (Node updated : startResult.successes()) {
                if (!updated.status().wantToRebuild()) {
                    NodeList children = nodeRepository().nodes().list().childrenOf(updated);
                    restoreSnapshotsOf(children);
                    nodeRepository().nodes().write(updated, () -> {});
                }
            }

            for (var entry : startResult.failed().entrySet()) {
                ++failures;
                log.log(Level.WARNING, "Failed to start " + entry.getKey() + ", will retry in " +
                        interval() + ": " + Exceptions.toMessageString(entry.getValue()));
            }
            attempts += startResult.successes().size() + startResult.failed().size();
        }

        return asSuccessFactorDeviation(attempts, failures);
    }

    private void restoreSnapshotsOf(NodeList children) {
        for (Node child : children) {
            Optional<Snapshot> snapshot = child.status().snapshot();
            if (snapshot.isPresent() && snapshot.get().state() == Snapshot.State.created) {
                nodeRepository().snapshots().restore(snapshot.get().id(), child.hostname());
            }
        }
    }

}
