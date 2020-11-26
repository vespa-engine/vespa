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

    boolean shouldReconfigure(ZookeeperServerConfig newConfig) {
        ZookeeperServerConfig existingConfig = zooKeeperRunner.zookeeperServerConfig();
        if (!newConfig.dynamicReconfiguration() || existingConfig == null) return false;

        return !newConfig.equals(existingConfig);
    }

    private ZooKeeperRunner startServer(ZookeeperServerConfig zookeeperServerConfig) {
        return new ZooKeeperRunner(zookeeperServerConfig);
    }

    void reconfigure(ZookeeperServerConfig newConfig) {
        ZookeeperServerConfig existingConfig = zooKeeperRunner.zookeeperServerConfig();

        List<String> originalServers = List.copyOf(servers(existingConfig));
        log.log(Level.INFO, "Original servers: " + originalServers);

        List<String> joiningServers = servers(newConfig);
        List<String> leavingServers = setDifference(originalServers, joiningServers);
        List<String> addedServers = setDifference(joiningServers, leavingServers);

        log.log(Level.INFO, "Will reconfigure zookeeper cluster. Joining servers: " + joiningServers +
                            ", leaving servers: " + leavingServers +
                            ", new members" + addedServers);
        try {
            ZooKeeperAdmin zooKeeperAdmin = new ZooKeeperAdmin(connectionSpec(existingConfig), sessionTimeoutInSeconds, null);

            long fromConfig = -1;
            zooKeeperAdmin.reconfigure(joiningServers, originalServers, addedServers, fromConfig, null);
        } catch (IOException | KeeperException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns items in set a that are not in set b
     */
    List<String> setDifference(List<String> a, List<String> b) {
        Set<String> ret = new HashSet<>(a);
        ret.removeAll(b);
        return new ArrayList<>(ret);
    }

    private String connectionSpec(ZookeeperServerConfig config) {
        return String.join(",", servers(config));
    }

    private List<String> servers(ZookeeperServerConfig config) {
        return config.server().stream()
                .map(server -> server.hostname() + ":" + server.quorumPort() + ":" + server.electionPort())
                .collect(Collectors.toList());
    }

}
