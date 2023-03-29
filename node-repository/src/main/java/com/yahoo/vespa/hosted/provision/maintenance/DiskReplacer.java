// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeMutex;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner;

import java.time.Duration;
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
        NodeList nodes = nodeRepository().nodes().list().rebuilding(true);
        int failures = 0;
        for (var host : nodes) {
            Optional<NodeMutex> optionalMutex = nodeRepository().nodes().lockAndGet(host, Duration.ofSeconds(10));
            if (optionalMutex.isEmpty()) continue;
            try (NodeMutex mutex = optionalMutex.get()) {
                // Re-check flag while holding lock
                host = mutex.node();
                if (!host.status().wantToRebuild()) {
                    continue;
                }
                Node updatedNode = hostProvisioner.replaceRootDisk(host);
                if (!updatedNode.status().wantToRebuild()) {
                    nodeRepository().nodes().write(updatedNode, mutex);
                }
            } catch (RuntimeException e) {
                failures++;
                log.log(Level.WARNING, "Failed to rebuild " + host.hostname() + ", will retry in " + interval(), e);
            }
        }
        return this.asSuccessFactorDeviation(nodes.size(), failures);
    }
}
