// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.yahoo.component.annotation.Inject;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.net.HostName;
import com.yahoo.protect.Process;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.zookeeper.Configurator.serverSpec;
import static java.util.stream.Collectors.toList;

/**
 * Starts zookeeper server and supports reconfiguring zookeeper cluster. Keep this as a component
 * without injected config, to make sure that it is not recreated when config changes.
 *
 * @author hmusum
 */
public class Reconfigurer extends AbstractComponent {

    private static final Logger log = java.util.logging.Logger.getLogger(Reconfigurer.class.getName());

    private static final Duration TIMEOUT = Duration.ofMinutes(3);

    private final ExponentialBackoff backoff = new ExponentialBackoff(Duration.ofSeconds(1), Duration.ofSeconds(10));
    private final VespaZooKeeperAdmin vespaZooKeeperAdmin;
    private final Sleeper sleeper;

    private QuorumPeer peer;
    private ZooKeeperRunner zooKeeperRunner;
    private ZookeeperServerConfig activeConfig;

    @Inject
    public Reconfigurer(VespaZooKeeperAdmin vespaZooKeeperAdmin) {
        this(vespaZooKeeperAdmin, new Sleeper());
    }

    Reconfigurer(VespaZooKeeperAdmin vespaZooKeeperAdmin, Sleeper sleeper) {
        this.vespaZooKeeperAdmin = Objects.requireNonNull(vespaZooKeeperAdmin);
        this.sleeper = Objects.requireNonNull(sleeper);
    }

    @Override
    public void deconstruct() {
        shutdown();
    }

    QuorumPeer startOrReconfigure(ZookeeperServerConfig newConfig, VespaZooKeeperServer server,
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
        String newServers = String.join(",", servers(newConfig));
        log.log(Level.INFO, "Will reconfigure ZooKeeper cluster." +
                            "\nServers in active config:" + servers(activeConfig) +
                            "\nServers in new config:" + servers(newConfig));
        String connectionSpec = localConnectionSpec(activeConfig);
        Instant now = Instant.now();
        Duration reconfigTimeout = reconfigTimeout();
        Instant end = now.plus(reconfigTimeout);
        // Loop reconfiguring since we might need to wait until another reconfiguration is finished before we can succeed
        for (int attempt = 1; now.isBefore(end); attempt++) {
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
                log.log(Level.WARNING, "Reconfiguration attempt " + attempt + " failed. Retrying in " + delay +
                                       ", time left " + Duration.between(now, end) + ": " +
                                       Exceptions.toMessageString(e));
                sleeper.sleep(delay);
            } finally {
                now = Instant.now();
            }
        }

        // Reconfiguration failed
        shutdownAndDie(reconfigTimeout);
    }

    private void shutdownAndDie(Duration reconfigTimeout) {
        shutdown();
        Process.logAndDie("Reconfiguration did not complete within timeout " + reconfigTimeout + ". Forcing container shutdown.");
    }

    private static Duration reconfigTimeout() {
        // For reconfig to succeed, the current and resulting ensembles must have a majority. When an ensemble grows and
        // the joining servers outnumber the existing ones, we have to wait for enough of them to start to have a majority.
        return TIMEOUT;
    }

    private static String localConnectionSpec(ZookeeperServerConfig config) {
        return HostName.getLocalhost() + ":" + config.clientPort();
    }

    private static List<String> servers(ZookeeperServerConfig config) {
        return config.server().stream()
                     .filter(server -> ! server.retired())
                     .map(server -> serverSpec(server, false))
                     .collect(toList());
    }

}
