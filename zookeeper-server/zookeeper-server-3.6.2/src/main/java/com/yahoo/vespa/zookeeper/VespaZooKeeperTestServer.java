// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.security.tls.TransportSecurityUtils;
import org.apache.zookeeper.server.quorum.QuorumPeer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;

/**
 * For use in unit tests only
 *
 * @author Harald Musum
 */
public class VespaZooKeeperTestServer implements VespaZooKeeperServer, Runnable {

    ExecutorService executorService = Executors.newSingleThreadExecutor(new DaemonThreadFactory("zookeeper test server"));

    private final VespaQuorumPeer peer;
    private Path configFilePath;

    @Inject
    public VespaZooKeeperTestServer(ZookeeperServerConfig zookeeperServerConfig) {
        this.peer = new VespaQuorumPeer();
        new Configurator(zookeeperServerConfig).writeConfigToDisk(TransportSecurityUtils.getOptions());
    }

    @Override
    public void shutdown() {
        peer.shutdown();
        executorService.shutdownNow();
    }

    @Override
    public void start(Path configFilePath) {
        this.configFilePath = configFilePath;
        executorService.submit(this);
    }

    @Override
    public void run() {
        peer.start(configFilePath);
    }

    @Override
    public boolean reconfigurable() {
        return false;
    }

}
