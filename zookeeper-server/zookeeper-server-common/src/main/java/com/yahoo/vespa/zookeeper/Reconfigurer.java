// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.protect.Process;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.stream.Collectors.joining;

/**
 * Starts zookeeper server and supports reconfiguring zookeeper cluster. Keep this as a component
 * without injected config, to make sure that it is not recreated when config changes.
 *
 * @author hmusum
 */
public class Reconfigurer extends AbstractComponent {

    private static final Logger log = java.util.logging.Logger.getLogger(Reconfigurer.class.getName());

    static final Duration TIMEOUT = Duration.ofMinutes(15);

    private final ExponentialBackoff backoff = new ExponentialBackoff(Duration.ofMillis(50), Duration.ofSeconds(10));
    private final Duration timeout;
    private final boolean haltOnFailure;
    private final VespaZooKeeperAdmin vespaZooKeeperAdmin;
    private final Sleeper sleeper;

    private QuorumPeer peer;
    private ZooKeeperRunner zooKeeperRunner;
    private ZookeeperServerConfig activeConfig;

    @Inject
    public Reconfigurer(VespaZooKeeperAdmin vespaZooKeeperAdmin) {
        this(vespaZooKeeperAdmin, new Sleeper(), true, TIMEOUT);
    }

    public Reconfigurer(VespaZooKeeperAdmin vespaZooKeeperAdmin, Sleeper sleeper, boolean haltOnFailure, Duration timeout) {
        this.vespaZooKeeperAdmin = Objects.requireNonNull(vespaZooKeeperAdmin);
        this.sleeper = Objects.requireNonNull(sleeper);
        this.haltOnFailure = haltOnFailure;
        this.timeout = timeout;
    }

    @Override
    public void deconstruct() {
        shutdown();
    }

    QuorumPeer startOrReconfigure(ZookeeperServerConfig newConfig,
                                  VespaZooKeeperServer server,
                                  Supplier<QuorumPeer> quorumPeerCreator) {
        if (zooKeeperRunner == null) {
            peer = quorumPeerCreator.get(); // Obtain the peer from the server. This will be shared with later servers.
            zooKeeperRunner = startServer(newConfig, server);
        }

        if (newConfig.dynamicReconfiguration()) {
            reconfigure(newConfig);
        }
        return peer;
    }

    ZookeeperServerConfig activeConfig() {
        return activeConfig;
    }

    void shutdown() {
        if (zooKeeperRunner != null) {
            zooKeeperRunner.shutdown();
        }
    }

    private ZooKeeperRunner startServer(ZookeeperServerConfig zookeeperServerConfig, VespaZooKeeperServer server) {
        ZooKeeperRunner runner = new ZooKeeperRunner(zookeeperServerConfig, server);
        activeConfig = zookeeperServerConfig;
        return runner;
    }

    // TODO jonmv: read dynamic file, discard if old quorum impossible (config file + .dynamic.<id>)
    // TODO jonmv: if dynamic file, all unlisted servers are observers; otherwise joiners are observers
    // TODO jonmv: wrap Curator in Provider, for Curator shutdown
    private void reconfigure(ZookeeperServerConfig newConfig) {
        Instant reconfigTriggered = Instant.now();
        String newServers = servers(newConfig);
        log.log(Level.INFO, "Will reconfigure ZooKeeper cluster." +
                            "\nServers in active config:" + servers(activeConfig) +
                            "\nServers in new config:" + newServers);
        String connectionSpec = vespaZooKeeperAdmin.localConnectionSpec(activeConfig);
        Instant now = Instant.now();
        // For reconfig to succeed, the current and resulting ensembles must have a majority. When an ensemble grows and
        // the joining servers outnumber the existing ones, we have to wait for enough of them to start to have a majority.
        Instant end = now.plus(timeout);
        // Loop reconfiguring since we might need to wait until another reconfiguration is finished before we can succeed
        for (int attempt = 1; ; attempt++) {
            try {
                Instant reconfigStarted = Instant.now();
                vespaZooKeeperAdmin.reconfigure(connectionSpec, newServers);
                Instant reconfigEnded = Instant.now();
                log.log(Level.INFO, "Reconfiguration completed in " +
                                    Duration.between(reconfigTriggered, reconfigEnded) +
                                    ", after " + attempt + " attempt(s). ZooKeeper reconfig call took " +
                                    Duration.between(reconfigStarted, reconfigEnded));
                activeConfig = newConfig;
                return;
            } catch (ReconfigException e) {
                Duration delay = backoff.delay(attempt);
                now = Instant.now();
                if (now.isBefore(end)) {
                    log.log(Level.INFO, "Reconfiguration attempt " + attempt + " failed. Retrying in " + delay +
                                           ", time left " + Duration.between(now, end) + ": " + Exceptions.toMessageString(e));
                    sleeper.sleep(delay);
                }
                else {
                    log.log(Level.SEVERE, "Reconfiguration attempt " + attempt + " failed, and was failing for " +
                                          timeout + "; giving up now: " + Exceptions.toMessageString(e));
                    shutdown();
                    if (haltOnFailure)
                        Process.logAndDie("Reconfiguration did not complete within timeout " + timeout + ". Forcing container shutdown.");
                    else
                        throw e;
                }
            }
        }
    }

    private static String servers(ZookeeperServerConfig config) {
        return Configurator.getServerConfig(config.server().stream().filter(server -> ! server.retired()).toList(), -1)
                           .entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).collect(joining(","));
    }

}
