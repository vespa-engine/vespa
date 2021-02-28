// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.security.tls.TransportSecurityUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * For use in unit tests only
 *
 * @author Harald Musum
 */
public class VespaZooKeeperTestServer implements VespaZooKeeperServer, Runnable {

    private static final Logger log = java.util.logging.Logger.getLogger(VespaZooKeeperTestServer.class.getName());

    ExecutorService executorService = Executors.newSingleThreadExecutor(new DaemonThreadFactory("zookeeper test server"));

    private final ZookeeperServerConfig zookeeperServerConfig;
    private final VespaQuorumPeer peer;
    private Path configFilePath;

    private VespaZooKeeperTestServer(ZookeeperServerConfig zookeeperServerConfig) {
        this.zookeeperServerConfig = zookeeperServerConfig;
        this.peer = new VespaQuorumPeer();

    }

    public static VespaZooKeeperTestServer createAndStartServer(int port, int tickTime) throws IOException {
        Path zooKeeperDir = Files.createTempDirectory("zookeeper");
        ZookeeperServerConfig config = getZookeeperServerConfig(zooKeeperDir, port, tickTime);
        VespaZooKeeperTestServer server = new VespaZooKeeperTestServer(config);
        log.log(Level.INFO, "Starting ZooKeeper test server on port " + port);
        server.start(Paths.get(config.zooKeeperConfigFile()));
        return server;
    }

    @Override
    public void shutdown() {
        peer.shutdown(Duration.ofMinutes(1));
        executorService.shutdownNow();
    }

    @Override
    public void start(Path configFilePath) {
        this.configFilePath = configFilePath;
        new Configurator(zookeeperServerConfig).writeConfigToDisk(TransportSecurityUtils.getOptions());
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

    private static ZookeeperServerConfig getZookeeperServerConfig(Path zooKeeperDir, int port, int tickTime) {
        return new ZookeeperServerConfig.Builder()
                .tickTime(tickTime) // in ms
                .clientPort(port)
                .server(new ZookeeperServerConfig.Server.Builder().hostname("localhost").id(0))
                .myid(0)
                .zooKeeperConfigFile(zooKeeperDir.resolve("zookeeper.cfg").toFile().getAbsolutePath())
                .dataDir(zooKeeperDir.toFile().getAbsolutePath())
                .myidFile(zooKeeperDir.resolve("myId").toFile().getAbsolutePath())
                .build();
    }

}
