// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.AbstractComponent;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.admin.ZooKeeperAdmin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
    private static final Duration sessionTimeout = Duration.ofSeconds(30);

    private ZooKeeperRunner zooKeeperRunner;
    private ZookeeperServerConfig activeConfig;

    @Inject
    public Reconfigurer() {
        log.log(Level.FINE, "Created ZooKeeperReconfigurer");
    }

    void startOrReconfigure(ZookeeperServerConfig newConfig) {
        if (zooKeeperRunner == null)
            zooKeeperRunner = startServer(newConfig);

        if (shouldReconfigure(newConfig))
            reconfigure(newConfig);
    }

    ZookeeperServerConfig activeConfig() {
        return activeConfig;
    }

    void zooKeeperReconfigure(String connectionSpec, String joiningServers, String leavingServers) {
        try {
            ZooKeeperAdmin zooKeeperAdmin = new ZooKeeperAdmin(connectionSpec,
                                                               (int) sessionTimeout.toMillis(),
                                                               null);
            long fromConfig = -1;
            // Using string parameters because the List variant of reconfigure fails to join empty lists (observed on 3.5.6, fixed in 3.7.0)
            byte[] appliedConfig = zooKeeperAdmin.reconfigure(joiningServers, leavingServers, null, fromConfig, null);
            log.log(Level.INFO, "Applied ZooKeeper config: " + new String(appliedConfig, StandardCharsets.UTF_8));
        } catch (IOException | KeeperException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean shouldReconfigure(ZookeeperServerConfig newConfig) {
        if (!newConfig.dynamicReconfiguration()) return false;
        if (activeConfig == null) return false;
        return !newConfig.equals(activeConfig());
    }

    private ZooKeeperRunner startServer(ZookeeperServerConfig zookeeperServerConfig) {
        ZooKeeperRunner runner = new ZooKeeperRunner(zookeeperServerConfig);
        activeConfig = zookeeperServerConfig;
        return runner;
    }

    private void reconfigure(ZookeeperServerConfig newConfig) {
        String leavingServers = String.join(",", difference(serverIds(activeConfig), serverIds(newConfig)));
        String joiningServers = String.join(",", difference(servers(newConfig), servers(activeConfig)));
        leavingServers = leavingServers.isEmpty() ? null : leavingServers;
        joiningServers = joiningServers.isEmpty() ? null : joiningServers;
        log.log(Level.INFO, "Will reconfigure ZooKeeper cluster. Joining servers: " + joiningServers +
                            ", leaving servers: " + leavingServers);
        zooKeeperReconfigure(connectionSpec(activeConfig), joiningServers, leavingServers);
        activeConfig = newConfig;
    }

    private static String connectionSpec(ZookeeperServerConfig config) {
        return config.server().stream()
                     .map(server -> server.hostname() + ":" + config.clientPort())
                     .collect(Collectors.joining(","));
    }

    private static List<String> serverIds(ZookeeperServerConfig config) {
        return config.server().stream()
                     .map(ZookeeperServerConfig.Server::id)
                     .map(String::valueOf)
                     .collect(Collectors.toList());
    }

    private static List<String> servers(ZookeeperServerConfig config) {
        // See https://zookeeper.apache.org/doc/r3.5.8/zookeeperReconfig.html#sc_reconfig_clientport for format
        return config.server().stream()
                     .map(server -> server.id() + "=" + server.hostname() + ":" + server.quorumPort() + ":" +
                                    server.electionPort())
                     .collect(Collectors.toList());
    }

    private static <T> List<T> difference(List<T> list1, List<T> list2) {
        List<T> copy = new ArrayList<>(list1);
        copy.removeAll(list2);
        return copy;
    }

}
