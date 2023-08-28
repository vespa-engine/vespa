// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeMutex;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner;
import com.yahoo.yolean.Exceptions;
import com.yahoo.yolean.UncheckedInterruptedException;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    private final ExecutorService executor = Executors.newCachedThreadPool(new DaemonThreadFactory("disk-replacer"));

    DiskReplacer(NodeRepository nodeRepository, Duration interval, Metric metric, HostProvisioner hostProvisioner) {
        super(nodeRepository, interval, metric);
        this.hostProvisioner = hostProvisioner;
    }

    @Override
    protected double maintain() {
        NodeList nodes = nodeRepository().nodes().list().rebuilding(true);
        int attempts = 0;
        int failures = 0;
        try (var locked = nodeRepository().nodes().lockAndGetAll(nodes.asList(), Optional.of(Duration.ofSeconds(10)))) {
            Map<String, Future<Node>> rebuilt = new HashMap<>();
            for (NodeMutex node : locked.nodes()) {
                if (node.node().status().wantToRebuild()) {
                    ++attempts;
                    rebuilt.put(node.node().hostname(), executor.submit(() -> hostProvisioner.replaceRootDisk(node.node())));
                }
            }

            for (var node : rebuilt.entrySet()) {
                try {
                    Node updated = node.getValue().get();
                    if ( ! updated.status().wantToRebuild()) {
                        nodeRepository().nodes().write(updated, () -> { });
                    }
                }
                catch (ExecutionException e) {
                    ++failures;
                    log.log(Level.WARNING, "Failed to rebuild " + node.getKey() + ", will retry in " +
                                           interval() + ": " + Exceptions.toMessageString(e.getCause()));
                }
                catch (InterruptedException e) {
                    throw new UncheckedInterruptedException(e, true);
                }
            }
        }
        return this.asSuccessFactorDeviation(attempts, failures);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        executor.shutdown();
    }

}
