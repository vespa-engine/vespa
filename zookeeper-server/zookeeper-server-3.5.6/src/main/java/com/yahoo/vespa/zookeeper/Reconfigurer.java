// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.AbstractComponent;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.admin.ZooKeeperAdmin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private static final int sessionTimeoutInSeconds = 30;

    private ZooKeeperRunner zooKeeperRunner;
    private ZookeeperServerConfig zookeeperServerConfig;

    @Inject
    public Reconfigurer() {
        log.log(Level.FINE, "Created ZooKeeperReconfigurer");
    }

    void startOrReconfigure(ZookeeperServerConfig newConfig) {
        if (zooKeeperRunner == null)
            zooKeeperRunner = startServer(newConfig);

        if (shouldReconfigure(newConfig))
            reconfigure(new ReconfigurationInfo(existingConfig(), newConfig));

        this.zookeeperServerConfig = newConfig;
    }

    boolean shouldReconfigure(ZookeeperServerConfig newConfig) {
        ZookeeperServerConfig existingConfig = existingConfig();
        if (!newConfig.dynamicReconfiguration() || existingConfig == null) return false;

        return !newConfig.equals(existingConfig);
    }

    private ZooKeeperRunner startServer(ZookeeperServerConfig zookeeperServerConfig) {
        return new ZooKeeperRunner(zookeeperServerConfig);
    }

    void reconfigure(ReconfigurationInfo reconfigurationInfo) {
        List<String> joiningServers = reconfigurationInfo.joiningServers();
        List<String> leavingServers = reconfigurationInfo.leavingServers();
        List<String> addedServers = reconfigurationInfo.addedServers();

        log.log(Level.INFO, "Will reconfigure zookeeper cluster. Joining servers: " + joiningServers +
                            ", leaving servers: " + leavingServers +
                            ", new members: " + addedServers);
        try {
            ZooKeeperAdmin zooKeeperAdmin = new ZooKeeperAdmin(connectionSpec(reconfigurationInfo.existingConfig()), sessionTimeoutInSeconds, null);

            long fromConfig = -1;
            String joiningServersString = String.join(",", joiningServers);
            String leavingServersString = String.join(",", leavingServers);
            String addedServersString = String.join(",", addedServers);
            // Using string parameters because the List variant of reconfigure fails to join empty lists (observed on 3.5.6, fixed in 3.7.0)
            zooKeeperAdmin.reconfigure(joiningServersString, leavingServersString, addedServersString, fromConfig, null);
        } catch (IOException | KeeperException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns items in set a that are not in set b
     */
    static List<String> setDifference(List<String> a, List<String> b) {
        Set<String> ret = new HashSet<>(a);
        ret.removeAll(b);
        return new ArrayList<>(ret);
    }

    private String connectionSpec(ZookeeperServerConfig config) {
        return config.server().stream()
                     .map(server -> server.hostname() + ":" + config.clientPort())
                     .collect(Collectors.joining(","));
    }

    private static List<String> servers(ZookeeperServerConfig config) {
        return config.server().stream()
                     .map(server -> server.hostname() + ":" + server.quorumPort() + ":" + server.electionPort())
                     .collect(Collectors.toList());
    }

    ZookeeperServerConfig existingConfig() {
        return zookeeperServerConfig;
    }

    static class ReconfigurationInfo {

        private final ZookeeperServerConfig existingConfig;
        private final List<String> joiningServers;
        private final List<String> leavingServers;
        private final List<String> addedServers;

        public ReconfigurationInfo(ZookeeperServerConfig existingConfig, ZookeeperServerConfig newConfig) {
            this.existingConfig = existingConfig;
            List<String> originalServers = List.copyOf(servers(existingConfig));

            this.joiningServers = servers(newConfig);
            this.leavingServers = setDifference(originalServers, servers(newConfig));
            this.addedServers = setDifference(servers(newConfig), originalServers);
        }

        public ZookeeperServerConfig existingConfig() {
            return existingConfig;
        }

        public List<String> joiningServers() {
            return joiningServers;
        }

        public List<String> leavingServers() {
            return leavingServers;
        }

        public List<String> addedServers() {
            return addedServers;
        }

    }

}
