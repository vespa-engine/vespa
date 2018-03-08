// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.log.LogLevel;
import static com.yahoo.vespa.defaults.Defaults.getDefaults;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Writes zookeeper config and starts zookeeper server.
 *
 * @author Ulf Lilleengen
 */
public class ZooKeeperServer extends AbstractComponent implements Runnable {

    /** 
     * The set of hosts which can access the ZooKeeper server in this VM, or empty
     * to allow access from anywhere.
     * This belongs logically to the server instance and is final, but must be static to make it accessible
     * from RestrictedServerCnxnFactory, which is created by ZK through reflection.
     */
    private static ImmutableSet<String> allowedClientHostnames = ImmutableSet.of();

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(ZooKeeperServer.class.getName());
    private static final String ZOOKEEPER_JMX_LOG4J_DISABLE = "zookeeper.jmx.log4j.disable";
    static final String ZOOKEEPER_JUTE_MAX_BUFFER = "jute.maxbuffer";
    private final Thread zkServerThread;
    private final ZookeeperServerConfig zookeeperServerConfig;

    ZooKeeperServer(ZookeeperServerConfig zookeeperServerConfig, ConfigserverConfig configserverConfig, boolean startServer) {
        this.zookeeperServerConfig = zookeeperServerConfig;
        System.setProperty("zookeeper.jmx.log4j.disable", "true");
        System.setProperty(ZOOKEEPER_JUTE_MAX_BUFFER, "" + zookeeperServerConfig.juteMaxBuffer());
        System.setProperty("zookeeper.serverCnxnFactory", "com.yahoo.vespa.zookeeper.RestrictedServerCnxnFactory");

        if (configserverConfig.hostedVespa()) // restrict access to config servers only
            allowedClientHostnames =  ImmutableSet.copyOf(zookeeperServerHostnames(zookeeperServerConfig));

        writeConfigToDisk(zookeeperServerConfig);
        zkServerThread = new Thread(this, "zookeeper server");
        if (startServer) {
            zkServerThread.start();
        }
    }

    @Inject
    public ZooKeeperServer(ZookeeperServerConfig zookeeperServerConfig, ConfigserverConfig configserverConfig) {
        this(zookeeperServerConfig, configserverConfig, true);
    }

    /** Returns the hosts which are allowed to access this ZooKeeper server, or empty to allow access from anywhere */
    public static ImmutableSet<String> getAllowedClientHostnames() { return allowedClientHostnames; }
    
    private void writeConfigToDisk(ZookeeperServerConfig config) {
       String cfg = transformConfigToString(config);
       try (FileWriter writer = new FileWriter(getDefaults().underVespaHome(config.zooKeeperConfigFile()))) {
           writer.write(cfg);
           writeMyIdFile(config);
       } catch (IOException e) {
           throw new RuntimeException("Error writing zookeeper config", e);
       }
   }

    private String transformConfigToString(ZookeeperServerConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("tickTime=").append(config.tickTime()).append("\n");
        sb.append("initLimit=").append(config.initLimit()).append("\n");
        sb.append("syncLimit=").append(config.syncLimit()).append("\n");
        sb.append("maxClientCnxns=").append(config.maxClientConnections()).append("\n");
        sb.append("snapCount=").append(config.snapshotCount()).append("\n");
        sb.append("dataDir=").append(getDefaults().underVespaHome(config.dataDir())).append("\n");
        sb.append("clientPort=").append(config.clientPort()).append("\n");
        sb.append("autopurge.purgeInterval=").append(config.autopurge().purgeInterval()).append("\n");
        sb.append("autopurge.snapRetainCount=").append(config.autopurge().snapRetainCount()).append("\n");
        if (config.server().size() > 1) {
            ensureThisServerIsRepresented(config.myid(), config.server());
            for (ZookeeperServerConfig.Server server : config.server()) {
                addServerToCfg(sb, server);
            }
        }
        return sb.toString();
    }

    private void writeMyIdFile(ZookeeperServerConfig config) throws IOException {
        if (config.server().size() > 1) {
            try (FileWriter writer = new FileWriter(getDefaults().underVespaHome(config.myidFile()))) {
                writer.write(config.myid() + "\n");
            }
        }
    }

    private void ensureThisServerIsRepresented(int myid, List<ZookeeperServerConfig.Server> servers) {
        boolean found = false;
        for (ZookeeperServerConfig.Server server : servers) {
            if (myid == server.id()) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new RuntimeException("No id in zookeeper server list that corresponds to my id(" + myid + ")");
        }
    }

    private void addServerToCfg(StringBuilder sb, ZookeeperServerConfig.Server server) {
        sb.append("server.").append(server.id()).append("=").append(server.hostname()).append(":").append(server.quorumPort()).append(":").append(server.electionPort()).append("\n");
    }

    private void shutdown() {
        zkServerThread.interrupt();
        try {
            zkServerThread.join();
        } catch (InterruptedException e) {
            log.log(LogLevel.WARNING, "Error joining server thread on shutdown", e);
        }
    }

    @Override
    public void run() {
        System.setProperty(ZOOKEEPER_JMX_LOG4J_DISABLE, "true");
        String[] args = new String[]{getDefaults().underVespaHome(zookeeperServerConfig.zooKeeperConfigFile())};
        log.log(LogLevel.DEBUG, "Starting ZooKeeper server with config: " + args[0]);
        log.log(LogLevel.INFO, "Trying to establish ZooKeeper quorum (from " + zookeeperServerHostnames(zookeeperServerConfig) + ")");
        org.apache.zookeeper.server.quorum.QuorumPeerMain.main(args);
    }

    @Override
    public void deconstruct() {
        shutdown();
        super.deconstruct();
    }

    public ZookeeperServerConfig getZookeeperServerConfig() { return zookeeperServerConfig; }

    private static Set<String> zookeeperServerHostnames(ZookeeperServerConfig zookeeperServerConfig) {
        return zookeeperServerConfig.server().stream().map(ZookeeperServerConfig.Server::hostname).collect(Collectors.toSet());
    }

}
