// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.net.HostName;
import com.yahoo.protect.Process;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Starts zookeeper server and supports reconfiguring zookeeper cluster. Created as a component
 * without any config injected, to make sure that it is not recreated when config changes.
 *
 * @author hmusum
 */
public class Reconfigurer extends AbstractComponent {

    private static final Logger log = java.util.logging.Logger.getLogger(Reconfigurer.class.getName());

    private static final Duration MIN_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration NODE_TIMEOUT = Duration.ofMinutes(1);

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
        log.log(Level.FINE, "Created ZooKeeperReconfigurer");
    }

    void startOrReconfigure(ZookeeperServerConfig newConfig, VespaZooKeeperServer server,
                            Supplier<QuorumPeer> quorumPeerGetter, Consumer<QuorumPeer> quorumPeerSetter) {
        if (zooKeeperRunner == null) {
            peer = quorumPeerGetter.get(); // Obtain the peer from the server. This will be shared with later servers.
            zooKeeperRunner = startServer(newConfig, server);
        }
        quorumPeerSetter.accept(peer);

        if (shouldReconfigure(newConfig)) {
            reconfigure(newConfig);
        }
    }

    ZookeeperServerConfig activeConfig() {
        return activeConfig;
    }

    void shutdown() {
        if (zooKeeperRunner != null) {
            zooKeeperRunner.shutdown();
        }
    }

    private boolean shouldReconfigure(ZookeeperServerConfig newConfig) {
        if (!newConfig.dynamicReconfiguration()) return false;
        if (activeConfig == null) return false;
        return !newConfig.equals(activeConfig());
    }

    private ZooKeeperRunner startServer(ZookeeperServerConfig zookeeperServerConfig, VespaZooKeeperServer server) {
        ZooKeeperRunner runner = new ZooKeeperRunner(zookeeperServerConfig, server);
        activeConfig = zookeeperServerConfig;
        return runner;
    }

    private void reconfigure(ZookeeperServerConfig newConfig) {
        Instant reconfigTriggered = Instant.now();
        // No point in trying to reconfigure if there is only one server in the new ensemble,
        // the others will be shutdown or are about to be shutdown
        if (newConfig.server().size() == 1) shutdownAndDie(Duration.ZERO);

        List<String> newServers = difference(servers(newConfig), servers(activeConfig));
        String leavingServerIds = String.join(",", serverIdsDifference(activeConfig, newConfig));
        String joiningServersSpec = String.join(",", newServers);
        leavingServerIds = leavingServerIds.isEmpty() ? null : leavingServerIds;
        joiningServersSpec = joiningServersSpec.isEmpty() ? null : joiningServersSpec;
        log.log(Level.INFO, "Will reconfigure ZooKeeper cluster. \nJoining servers: " + joiningServersSpec +
                            "\nleaving servers: " + leavingServerIds +
                            "\nServers in active config:" + servers(activeConfig) +
                            "\nServers in new config:" + servers(newConfig));
        String connectionSpec = localConnectionSpec(activeConfig);
        Instant now = Instant.now();
        Duration reconfigTimeout = reconfigTimeout(newServers.size());
        Instant end = now.plus(reconfigTimeout);
        // Loop reconfiguring since we might need to wait until another reconfiguration is finished before we can succeed
        for (int attempt = 1; now.isBefore(end); attempt++) {
            try {
                Instant reconfigStarted = Instant.now();
                vespaZooKeeperAdmin.reconfigure(connectionSpec, joiningServersSpec, leavingServerIds);
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

    /** Returns the timeout to use for the given joining server count */
    private static Duration reconfigTimeout(int joiningServers) {
        // For reconfig to succeed, the current and resulting ensembles must have a majority. When an ensemble grows and
        // the joining servers outnumber the existing ones, we have to wait for enough of them to start to have a majority.
        return Duration.ofMillis(Math.max(joiningServers * NODE_TIMEOUT.toMillis(), MIN_TIMEOUT.toMillis()));
    }

    private static String localConnectionSpec(ZookeeperServerConfig config) {
        return HostName.getLocalhost() + ":" + config.clientPort();
    }

    private static List<String> serverIdsDifference(ZookeeperServerConfig oldConfig, ZookeeperServerConfig newConfig) {
        return difference(servers(oldConfig), servers(newConfig)).stream()
                                                                 .map(server -> server.substring(0, server.indexOf('=')))
                                                                 .collect(Collectors.toList());
    }

    private static List<String> servers(ZookeeperServerConfig config) {
        // See https://zookeeper.apache.org/doc/r3.5.8/zookeeperReconfig.html#sc_reconfig_clientport for format
        return config.server().stream()
                     .map(server -> server.id() + "=" + server.hostname() + ":" + server.quorumPort() + ":" +
                                    server.electionPort() + ";" + config.clientPort())
                     .collect(Collectors.toList());
    }

    private static <T> List<T> difference(List<T> list1, List<T> list2) {
        List<T> copy = new ArrayList<>(list1);
        copy.removeAll(list2);
        return copy;
    }

}
